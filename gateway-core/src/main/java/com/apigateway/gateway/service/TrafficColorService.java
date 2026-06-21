package com.apigateway.gateway.service;

import com.apigateway.common.entity.TrafficColorRule;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.common.utils.TrafficTagMatcher;
import com.apigateway.gateway.repository.TrafficColorRuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficColorService {

    private final TrafficColorRuleRepository trafficColorRuleRepository;
    private final CopyOnWriteArrayList<TrafficColorRule> activeRules = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() {
        refreshRules();
    }

    @Scheduled(fixedDelay = 60000)
    public void refreshRules() {
        try {
            List<TrafficColorRule> rules = trafficColorRuleRepository.findAllActiveWithTenant(RuleStatus.ACTIVE)
                    .stream()
                    .filter(this::isEffective)
                    .sorted(Comparator.comparing(TrafficColorRule::getPriority).reversed())
                    .collect(Collectors.toList());
            activeRules.clear();
            activeRules.addAll(rules);
            log.info("Traffic color rules refreshed, total active rules: {}", rules.size());
        } catch (Exception e) {
            log.error("Failed to refresh traffic color rules", e);
        }
    }

    public String matchAndGetTag(ServerHttpRequest request, Long tenantId) {
        List<TrafficColorRule> filteredRules = activeRules;
        if (tenantId != null) {
            filteredRules = activeRules.stream()
                    .filter(rule -> rule.getTenant() != null && rule.getTenant().getId().equals(tenantId))
                    .collect(Collectors.toList());
        }

        for (TrafficColorRule rule : filteredRules) {
            if (TrafficTagMatcher.matches(request, rule)) {
                log.debug("Traffic rule matched: ruleId={}, colorTag={}", rule.getRuleId(), rule.getColorTag());
                return TrafficTagMatcher.applyTagOperation(null, rule.getColorTag(), rule.getOperation());
            }
        }
        return null;
    }

    private boolean isEffective(TrafficColorRule rule) {
        LocalDateTime now = LocalDateTime.now();
        if (rule.getEffectiveFrom() != null && rule.getEffectiveFrom().isAfter(now)) {
            return false;
        }
        if (rule.getEffectiveTo() != null && rule.getEffectiveTo().isBefore(now)) {
            return false;
        }
        return true;
    }

    public List<TrafficColorRule> getActiveRules() {
        return List.copyOf(activeRules);
    }
}
