package com.apigateway.gateway.service;

import com.apigateway.gateway.entity.RouteRuleR2dbc;
import com.apigateway.gateway.repository.RouteRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteConfigService {

    private final RouteRuleRepository routeRuleRepository;
    private final RouteDefinitionWriter routeDefinitionWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private final Map<Long, RouteRuleR2dbc> activeRouteRules = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshRoutes();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshRoutes() {
        routeRuleRepository.findByStatus("PUBLISHED")
                .collectList()
                .flatMap(rules -> {
                    Set<Long> currentRuleIds = new HashSet<>();
                    List<Mono<Void>> saveOperations = new ArrayList<>();

                    for (RouteRuleR2dbc rule : rules) {
                        currentRuleIds.add(rule.getId());
                        RouteRuleR2dbc existing = activeRouteRules.get(rule.getId());
                        if (existing == null || !isSameRoute(existing, rule)) {
                            saveOperations.add(saveRouteDefinition(rule));
                            activeRouteRules.put(rule.getId(), rule);
                        }
                    }

                    List<Mono<Void>> deleteOperations = new ArrayList<>();
                    activeRouteRules.keySet().removeIf(ruleId -> {
                        if (!currentRuleIds.contains(ruleId)) {
                            deleteOperations.add(deleteRouteDefinition(String.valueOf(ruleId)));
                            return true;
                        }
                        return false;
                    });

                    return Mono.when(saveOperations)
                            .then(Mono.when(deleteOperations))
                            .doOnSuccess(v -> {
                                eventPublisher.publishEvent(new RefreshRoutesEvent(this));
                                log.info("Routes refreshed, total active routes: {}", activeRouteRules.size());
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to refresh routes", e);
                    return Mono.empty();
                })
                .subscribe();
    }

    private boolean isSameRoute(RouteRuleR2dbc oldRule, RouteRuleR2dbc newRule) {
        return Objects.equals(oldRule.getPathPrefix(), newRule.getPathPrefix())
                && Objects.equals(oldRule.getHttpMethod(), newRule.getHttpMethod())
                && Objects.equals(oldRule.getTargetBackends(), newRule.getTargetBackends())
                && Objects.equals(oldRule.getPriority(), newRule.getPriority());
    }

    private Mono<Void> saveRouteDefinition(RouteRuleR2dbc rule) {
        try {
            RouteDefinition routeDefinition = buildRouteDefinition(rule);
            return routeDefinitionWriter.save(Mono.just(routeDefinition))
                    .doOnSuccess(v -> log.info("Saved route definition: {}", rule.getId()))
                    .doOnError(error -> log.error("Failed to save route definition: {}", rule.getId(), error))
                    .then();
        } catch (Exception e) {
            log.error("Error building route definition: {}", rule.getId(), e);
            return Mono.empty();
        }
    }

    private Mono<Void> deleteRouteDefinition(String ruleId) {
        return routeDefinitionWriter.delete(Mono.just(ruleId))
                .doOnSuccess(v -> log.info("Deleted route definition: {}", ruleId))
                .doOnError(error -> log.error("Failed to delete route definition: {}", ruleId, error));
    }

    private RouteDefinition buildRouteDefinition(RouteRuleR2dbc rule) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(String.valueOf(rule.getId()));

        String targetUrl = extractFirstBackend(rule.getTargetBackends());
        if (targetUrl == null || targetUrl.isEmpty()) {
            targetUrl = "http://localhost:8081";
        }
        definition.setUri(URI.create(targetUrl));
        definition.setOrder(rule.getPriority() != null ? rule.getPriority() : 0);

        List<PredicateDefinition> predicates = new ArrayList<>();

        if (rule.getPathPrefix() != null && !rule.getPathPrefix().isEmpty()) {
            PredicateDefinition pathPredicate = new PredicateDefinition();
            pathPredicate.setName("Path");
            pathPredicate.addArg("pattern", rule.getPathPrefix() + "/**");
            predicates.add(pathPredicate);
        }

        if (rule.getHttpMethod() != null && !rule.getHttpMethod().isEmpty()) {
            PredicateDefinition methodPredicate = new PredicateDefinition();
            methodPredicate.setName("Method");
            methodPredicate.addArg("methods", rule.getHttpMethod());
            predicates.add(methodPredicate);
        }

        definition.setPredicates(predicates);

        List<FilterDefinition> filters = new ArrayList<>();

        if (rule.getPathPrefixReplacement() != null && !rule.getPathPrefixReplacement().isEmpty()) {
            FilterDefinition rewritePathFilter = new FilterDefinition();
            rewritePathFilter.setName("RewritePath");
            rewritePathFilter.addArg("regexp", rule.getPathPrefix() + "/(?<segment>.*)");
            rewritePathFilter.addArg("replacement", rule.getPathPrefixReplacement() + "/${segment}");
            filters.add(rewritePathFilter);
        } else {
            FilterDefinition stripPrefixFilter = new FilterDefinition();
            stripPrefixFilter.setName("StripPrefix");
            stripPrefixFilter.addArg("parts", "1");
            filters.add(stripPrefixFilter);
        }

        if (rule.getRequestHeadersToAdd() != null && !rule.getRequestHeadersToAdd().isEmpty()) {
            Map<String, String> headersToAdd = parseJsonMap(rule.getRequestHeadersToAdd());
            for (Map.Entry<String, String> entry : headersToAdd.entrySet()) {
                FilterDefinition addHeaderFilter = new FilterDefinition();
                addHeaderFilter.setName("AddRequestHeader");
                addHeaderFilter.addArg("name", entry.getKey());
                addHeaderFilter.addArg("value", entry.getValue());
                filters.add(addHeaderFilter);
            }
        }

        if (rule.getRequestHeadersToRemove() != null && !rule.getRequestHeadersToRemove().isEmpty()) {
            String[] headersToRemove = rule.getRequestHeadersToRemove().split(",");
            for (String header : headersToRemove) {
                FilterDefinition removeHeaderFilter = new FilterDefinition();
                removeHeaderFilter.setName("RemoveRequestHeader");
                removeHeaderFilter.addArg("name", header.trim());
                filters.add(removeHeaderFilter);
            }
        }

        definition.setFilters(filters);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("appId", rule.getAppId());
        metadata.put("routeRuleId", rule.getId());
        metadata.put("routeRuleName", rule.getName());
        metadata.put("connectTimeoutMs", rule.getConnectTimeoutMs());
        metadata.put("readTimeoutMs", rule.getReadTimeoutMs());
        metadata.put("maxRetries", rule.getMaxRetries());
        metadata.put("retryOn5xx", rule.getRetryOn5xx());
        metadata.put("retryOnTimeout", rule.getRetryOnTimeout());
        metadata.put("retryIntervalMs", rule.getRetryIntervalMs());
        definition.setMetadata(metadata);

        return definition;
    }

    private String extractFirstBackend(String targetBackends) {
        if (targetBackends == null || targetBackends.isEmpty()) {
            return null;
        }
        try {
            List<String> backends = objectMapper.readValue(targetBackends, new TypeReference<List<String>>() {});
            return backends != null && !backends.isEmpty() ? backends.get(0) : null;
        } catch (Exception e) {
            return targetBackends.split(",")[0].trim();
        }
    }

    private Map<String, String> parseJsonMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public RouteRuleR2dbc getRouteRule(Long ruleId) {
        return activeRouteRules.get(ruleId);
    }

    public Map<Long, RouteRuleR2dbc> getAllActiveRouteRules() {
        return Collections.unmodifiableMap(activeRouteRules);
    }

    public Mono<Void> triggerRefresh() {
        return Mono.fromRunnable(this::refreshRoutes);
    }

    public Flux<RouteRuleR2dbc> getActiveRulesFlux() {
        return Flux.fromIterable(activeRouteRules.values());
    }
}
