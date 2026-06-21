package com.apigateway.gateway.service;

import com.apigateway.gateway.entity.TrafficColorRuleR2dbc;
import com.apigateway.gateway.repository.TrafficColorRuleRepository;
import com.apigateway.common.utils.IpUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficColorService {

    private final TrafficColorRuleRepository trafficColorRuleRepository;

    private final Map<Long, List<TrafficColorRuleR2dbc>> appRulesCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshRules();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshRules() {
        trafficColorRuleRepository.findByEnabledTrue()
                .collectList()
                .doOnSuccess(rules -> {
                    appRulesCache.clear();
                    Map<Long, List<TrafficColorRuleR2dbc>> grouped = rules.stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                    TrafficColorRuleR2dbc::getAppId,
                                    java.util.stream.Collectors.collectingAndThen(
                                            java.util.stream.Collectors.toList(),
                                            list -> list.stream()
                                                    .sorted(java.util.Comparator.comparingInt(TrafficColorRuleR2dbc::getPriority).reversed())
                                                    .toList()
                                    )
                            ));
                    appRulesCache.putAll(grouped);
                    log.info("Traffic color rules refreshed, total apps: {}, total rules: {}", grouped.size(), rules.size());
                })
                .doOnError(e -> log.error("Failed to refresh traffic color rules", e))
                .subscribe();
    }

    public Mono<String> matchAndGetTag(ServerHttpRequest request, Long tenantId) {
        Long appId = extractAppId(request, tenantId);
        if (appId == null) {
            return Mono.empty();
        }

        List<TrafficColorRuleR2dbc> rules = appRulesCache.get(appId);
        if (rules == null || rules.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(rules)
                .filterWhen(rule -> matchesCondition(request, rule))
                .next()
                .map(rule -> {
                    log.debug("Traffic rule matched: ruleId={}, tagValue={}", rule.getId(), rule.getTagValue());
                    return applyTagOperation(null, rule.getTagValue(), rule.getOperationType());
                })
                .switchIfEmpty(Mono.empty());
    }

    private Mono<Boolean> matchesCondition(ServerHttpRequest request, TrafficColorRuleR2dbc rule) {
        String conditionType = rule.getConditionType();
        String conditionValue = rule.getConditionValue();

        if (conditionType == null || conditionValue == null) {
            return Mono.just(false);
        }

        try {
            return switch (conditionType.toUpperCase()) {
                case "USER_ID_SUFFIX" -> Mono.just(matchUserIdSuffix(request, conditionValue));
                case "HEADER" -> Mono.just(matchHeader(request, conditionValue));
                case "IP_RANGE" -> Mono.just(matchIpRange(request, conditionValue));
                case "TIME_WINDOW" -> Mono.just(matchTimeWindow(conditionValue));
                case "RANDOM_PERCENT" -> Mono.just(matchRandomPercent(conditionValue));
                default -> Mono.just(false);
            };
        } catch (Exception e) {
            log.error("Error matching condition: ruleId={}, type={}", rule.getId(), conditionType, e);
            return Mono.just(false);
        }
    }

    private boolean matchUserIdSuffix(ServerHttpRequest request, String conditionValue) {
        List<String> userIds = request.getHeaders().get("X-User-Id");
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }
        String userId = userIds.get(0);
        if (userId == null || userId.isEmpty()) {
            return false;
        }

        String[] suffixes = conditionValue.split(",");
        for (String suffix : suffixes) {
            String trimmedSuffix = suffix.trim();
            if (userId.endsWith(trimmedSuffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchHeader(ServerHttpRequest request, String conditionValue) {
        String[] parts = conditionValue.split("=", 2);
        if (parts.length != 2) {
            return false;
        }
        String headerName = parts[0].trim();
        String expectedValue = parts[1].trim();

        List<String> headers = request.getHeaders().get(headerName);
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        return headers.stream().anyMatch(h -> h != null && h.equals(expectedValue));
    }

    private boolean matchIpRange(ServerHttpRequest request, String conditionValue) {
        String clientIp = request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;
        if (clientIp == null) {
            return false;
        }

        String[] ranges = conditionValue.split(",");
        for (String range : ranges) {
            String trimmedRange = range.trim();
            if (IpUtils.isIpInRange(clientIp, trimmedRange)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchTimeWindow(String conditionValue) {
        String[] parts = conditionValue.split("-");
        if (parts.length != 2) {
            return false;
        }
        try {
            LocalTime startTime = LocalTime.parse(parts[0].trim());
            LocalTime endTime = LocalTime.parse(parts[1].trim());
            LocalTime now = LocalTime.now();

            if (startTime.isBefore(endTime)) {
                return !now.isBefore(startTime) && !now.isAfter(endTime);
            } else {
                return !now.isBefore(startTime) || !now.isAfter(endTime);
            }
        } catch (Exception e) {
            log.error("Error parsing time window: {}", conditionValue, e);
            return false;
        }
    }

    private boolean matchRandomPercent(String conditionValue) {
        try {
            double percent = Double.parseDouble(conditionValue.trim());
            double random = ThreadLocalRandom.current().nextDouble(100);
            return random < percent;
        } catch (NumberFormatException e) {
            log.error("Error parsing random percent: {}", conditionValue, e);
            return false;
        }
    }

    private String applyTagOperation(String existingTag, String newTag, String operationType) {
        if (newTag == null || operationType == null) {
            return existingTag;
        }

        return switch (operationType.toUpperCase()) {
            case "ADD" -> existingTag == null ? newTag : existingTag + "," + newTag;
            case "REMOVE" -> {
                if (existingTag == null) {
                    yield null;
                }
                String[] tags = existingTag.split(",");
                StringBuilder result = new StringBuilder();
                for (String tag : tags) {
                    if (!tag.trim().equals(newTag)) {
                        if (!result.isEmpty()) {
                            result.append(",");
                        }
                        result.append(tag.trim());
                    }
                }
                yield result.isEmpty() ? null : result.toString();
            }
            case "MODIFY", "OVERRIDE" -> newTag;
            default -> existingTag;
        };
    }

    private Long extractAppId(ServerHttpRequest request, Long tenantId) {
        List<String> appIds = request.getHeaders().get("X-Application-Id");
        if (appIds != null && !appIds.isEmpty()) {
            try {
                return Long.parseLong(appIds.get(0));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public Map<Long, List<TrafficColorRuleR2dbc>> getAppRulesCache() {
        return Map.copyOf(appRulesCache);
    }
}
