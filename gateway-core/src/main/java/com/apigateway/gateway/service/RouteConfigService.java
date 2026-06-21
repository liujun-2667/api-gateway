package com.apigateway.gateway.service;

import com.apigateway.common.entity.Application;
import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.gateway.repository.RouteRuleRepository;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteConfigService {

    private final RouteRuleRepository routeRuleRepository;
    private final RouteDefinitionWriter routeDefinitionWriter;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, RouteRule> activeRouteRules = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshRoutes();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshRoutes() {
        try {
            List<RouteRule> rules = routeRuleRepository.findAllActiveWithTenantAndApplication(RuleStatus.ACTIVE)
                    .stream()
                    .filter(this::isEffective)
                    .toList();

            Set<String> currentRuleIds = new HashSet<>();
            for (RouteRule rule : rules) {
                currentRuleIds.add(rule.getRuleId());
                RouteRule existing = activeRouteRules.get(rule.getRuleId());
                if (existing == null || !isSameRoute(existing, rule)) {
                    saveRouteDefinition(rule);
                    activeRouteRules.put(rule.getRuleId(), rule);
                }
            }

            activeRouteRules.keySet().removeIf(ruleId -> {
                if (!currentRuleIds.contains(ruleId)) {
                    deleteRouteDefinition(ruleId);
                    return true;
                }
                return false;
            });

            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("Routes refreshed, total active routes: {}", activeRouteRules.size());
        } catch (Exception e) {
            log.error("Failed to refresh routes", e);
        }
    }

    private boolean isEffective(RouteRule rule) {
        LocalDateTime now = LocalDateTime.now();
        if (rule.getEffectiveFrom() != null && rule.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        if (rule.getEffectiveTo() != null && rule.getEffectiveTo().isBefore(now)) {
            return false;
        }
        return true;
    }

    private boolean isSameRoute(RouteRule oldRule, RouteRule newRule) {
        return Objects.equals(oldRule.getPath(), newRule.getPath())
                && Objects.equals(oldRule.getMethod(), newRule.getMethod())
                && Objects.equals(oldRule.getTargetUrl(), newRule.getTargetUrl())
                && Objects.equals(oldRule.getPriority(), newRule.getPriority());
    }

    private void saveRouteDefinition(RouteRule rule) {
        try {
            RouteDefinition routeDefinition = buildRouteDefinition(rule);
            routeDefinitionWriter.save(Mono.just(routeDefinition)).subscribe(
                    null,
                    error -> log.error("Failed to save route definition: {}", rule.getRuleId(), error)
            );
            log.info("Saved route definition: {}", rule.getRuleId());
        } catch (Exception e) {
            log.error("Error building route definition: {}", rule.getRuleId(), e);
        }
    }

    private void deleteRouteDefinition(String ruleId) {
        routeDefinitionWriter.delete(Mono.just(ruleId)).subscribe(
                null,
                error -> log.error("Failed to delete route definition: {}", ruleId, error),
                () -> log.info("Deleted route definition: {}", ruleId)
        );
    }

    private RouteDefinition buildRouteDefinition(RouteRule rule) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(rule.getRuleId());

        Application application = rule.getApplication();
        String targetUrl = rule.getTargetUrl();
        if (targetUrl == null || targetUrl.isEmpty()) {
            targetUrl = "http://localhost:8081";
        }
        definition.setUri(URI.create(targetUrl));
        definition.setOrder(rule.getPriority());

        List<PredicateDefinition> predicates = new ArrayList<>();

        PredicateDefinition pathPredicate = new PredicateDefinition();
        pathPredicate.setName("Path");
        pathPredicate.addArg("pattern", rule.getPath());
        predicates.add(pathPredicate);

        if (rule.getMethod() != null) {
            PredicateDefinition methodPredicate = new PredicateDefinition();
            methodPredicate.setName("Method");
            methodPredicate.addArg("methods", rule.getMethod().name());
            predicates.add(methodPredicate);
        }

        definition.setPredicates(predicates);

        List<FilterDefinition> filters = new ArrayList<>();
        FilterDefinition stripPrefixFilter = new FilterDefinition();
        stripPrefixFilter.setName("StripPrefix");
        stripPrefixFilter.addArg("parts", "1");
        filters.add(stripPrefixFilter);

        definition.setFilters(filters);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tenantId", rule.getTenant() != null ? rule.getTenant().getId() : null);
        metadata.put("tenantName", rule.getTenant() != null ? rule.getTenant().getName() : null);
        metadata.put("applicationId", application != null ? application.getId() : null);
        metadata.put("applicationName", application != null ? application.getName() : null);
        metadata.put("routeRuleId", rule.getId());
        metadata.put("requiresAuth", rule.getRequiresAuth());
        metadata.put("rateLimitEnabled", rule.getRateLimitEnabled());
        metadata.put("circuitBreakerEnabled", rule.getCircuitBreakerEnabled());
        definition.setMetadata(metadata);

        return definition;
    }

    public RouteRule getRouteRule(String ruleId) {
        return activeRouteRules.get(ruleId);
    }

    public Map<String, RouteRule> getAllActiveRouteRules() {
        return Collections.unmodifiableMap(activeRouteRules);
    }

    public Mono<Void> triggerRefresh() {
        return Mono.fromRunnable(this::refreshRoutes);
    }
}
