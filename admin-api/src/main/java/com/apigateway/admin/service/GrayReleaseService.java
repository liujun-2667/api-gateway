package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.ApplicationRepository;
import com.apigateway.admin.repository.GrayReleaseRepository;
import com.apigateway.admin.repository.RouteRuleRepository;
import com.apigateway.admin.repository.RouteRuleVersionRepository;
import com.apigateway.admin.repository.TenantRepository;
import com.apigateway.admin.repository.TrafficColorRuleRepository;
import com.apigateway.common.dto.GrayReleaseDTO;
import com.apigateway.common.dto.RouteRuleDTO;
import com.apigateway.common.dto.TrafficColorRuleDTO;
import com.apigateway.common.entity.Application;
import com.apigateway.common.entity.GrayRelease;
import com.apigateway.common.entity.RouteRule;
import com.apigateway.common.entity.RouteRuleVersion;
import com.apigateway.common.entity.TargetBackend;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.entity.TrafficColorRule;
import com.apigateway.common.enums.ColorTagOperation;
import com.apigateway.common.enums.GrayReleasePhase;
import com.apigateway.common.enums.GrayReleaseStatus;
import com.apigateway.common.enums.MatchType;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.enums.RuleStatus;
import com.apigateway.common.enums.TrafficConditionType;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import com.apigateway.common.utils.ApiKeyGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrayReleaseService {

    private final GrayReleaseRepository grayReleaseRepository;
    private final RouteRuleRepository routeRuleRepository;
    private final TrafficColorRuleRepository trafficColorRuleRepository;
    private final ApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;
    private final RouteRuleVersionRepository routeRuleVersionRepository;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    private static final String GRAY_COLOR_TAG = "gray";
    private static final String RANDOM_KEY = "random";

    @Auditable(resourceType = "GrayRelease", operationType = OperationType.GRAY_RELEASE_START)
    @Transactional
    public GrayReleaseDTO.GrayReleaseResponse createGrayRelease(
            GrayReleaseDTO.GrayReleaseCreateRequest request, String createdBy) {

        GrayReleaseDTO.GrayReleaseWizardStep1 step1 = request.getStep1();
        GrayReleaseDTO.GrayReleaseWizardStep2 step2 = request.getStep2();

        Application application = applicationRepository.findById(step1.getAppId())
                .orElseThrow(() -> new NotFoundException("Application", step1.getAppId().toString()));

        RouteRule routeRule = routeRuleRepository.findById(step1.getRouteRuleId())
                .orElseThrow(() -> new NotFoundException("RouteRule", step1.getRouteRuleId().toString()));

        List<GrayReleaseStatus> activeStatuses = List.of(
                GrayReleaseStatus.IN_PROGRESS,
                GrayReleaseStatus.PENDING,
                GrayReleaseStatus.PAUSED
        );
        if (grayReleaseRepository.existsByRouteRuleIdAndStatusIn(step1.getRouteRuleId(), activeStatuses)) {
            throw new BusinessException("An active gray release already exists for this route rule");
        }

        Tenant tenant = tenantRepository.findById(routeRule.getTenant().getId())
                .orElseThrow(() -> new NotFoundException("Tenant", routeRule.getTenant().getId().toString()));

        TrafficColorRule colorRule = TrafficColorRule.builder()
                .ruleId("gray_" + ApiKeyGenerator.generateKeyId())
                .name("Gray Release - " + step1.getName())
                .description("Auto-generated color rule for gray release")
                .tenant(tenant)
                .routeRule(routeRule)
                .conditionType(TrafficConditionType.RANDOM_PERCENTAGE)
                .conditionKey(RANDOM_KEY)
                .matchType(MatchType.EXACT)
                .conditionValue(step2.getInitialPercent().toString())
                .colorTag(GRAY_COLOR_TAG)
                .operation(ColorTagOperation.ADD)
                .priority(100)
                .status(RuleStatus.ACTIVE)
                .build();
        colorRule = trafficColorRuleRepository.save(colorRule);

        String releaseStagesJson;
        try {
            releaseStagesJson = objectMapper.writeValueAsString(step2.getReleaseStages());
        } catch (JsonProcessingException e) {
            throw new BusinessException("Failed to serialize release stages");
        }

        GrayReleasePhase firstPhase = GrayReleasePhase.INITIAL;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextStageTime = now.plusMinutes(step2.getObservationMinutesPerStage());

        GrayRelease grayRelease = GrayRelease.builder()
                .grayReleaseId("gr_" + ApiKeyGenerator.generateKeyId())
                .name(step1.getName())
                .description(step1.getDescription())
                .tenant(tenant)
                .application(application)
                .routeRule(routeRule)
                .colorRule(colorRule)
                .status(GrayReleaseStatus.PENDING)
                .currentPhase(firstPhase)
                .currentTrafficPercent(step2.getInitialPercent())
                .initialPercent(step2.getInitialPercent())
                .releaseStagesJson(releaseStagesJson)
                .observationMinutesPerStage(step2.getObservationMinutesPerStage())
                .errorRateThreshold(step2.getErrorRateThreshold())
                .phaseStartTime(now)
                .nextStageTime(nextStageTime)
                .totalStages(step2.getReleaseStages().size())
                .completedStages(0)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        grayRelease = grayReleaseRepository.save(grayRelease);

        updateRouteRuleTargetBackends(routeRule, step2.getInitialPercent());

        grayRelease.setStatus(GrayReleaseStatus.IN_PROGRESS);
        grayRelease = grayReleaseRepository.save(grayRelease);

        return toResponse(grayRelease);
    }

    @Transactional(readOnly = true)
    public GrayReleaseDTO.GrayReleaseResponse getGrayReleaseById(Long id) {
        GrayRelease grayRelease = grayReleaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GrayRelease", id.toString()));
        return toResponse(grayRelease);
    }

    @Transactional(readOnly = true)
    public List<GrayReleaseDTO.GrayReleaseResponse> getGrayReleasesByAppId(Long appId) {
        if (!applicationRepository.existsById(appId)) {
            throw new NotFoundException("Application", appId.toString());
        }
        return grayReleaseRepository.findByApplicationIdOrderByCreatedAtDesc(appId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GrayReleaseDTO.GrayReleaseResponse> getActiveGrayReleases() {
        List<GrayReleaseStatus> activeStatuses = List.of(
                GrayReleaseStatus.IN_PROGRESS,
                GrayReleaseStatus.PAUSED
        );
        return grayReleaseRepository.findByStatusIn(activeStatuses).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GrayReleaseDTO.GrayReleaseStatusResponse getGrayReleaseStatus(Long id) {
        GrayRelease grayRelease = grayReleaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GrayRelease", id.toString()));

        Double currentErrorRate = calculateErrorRate(grayRelease);

        TrafficColorRuleDTO.TrafficColorRuleResponse colorRuleResponse = null;
        if (grayRelease.getColorRule() != null) {
            colorRuleResponse = toColorRuleResponse(grayRelease.getColorRule());
        }

        RouteRuleDTO.RouteRuleResponse routeRuleResponse = toRouteRuleResponse(grayRelease.getRouteRule());

        GrayReleaseDTO.GrayReleaseResponse response = toResponse(grayRelease);
        response.setCurrentErrorRate(currentErrorRate);

        return GrayReleaseDTO.GrayReleaseStatusResponse.builder()
                .grayRelease(response)
                .colorRule(colorRuleResponse)
                .routeRule(routeRuleResponse)
                .build();
    }

    @Transactional
    public void processGrayReleasePhase(Long grayReleaseId) {
        GrayRelease grayRelease = grayReleaseRepository.findById(grayReleaseId)
                .orElseThrow(() -> new NotFoundException("GrayRelease", grayReleaseId.toString()));

        if (grayRelease.getStatus() != GrayReleaseStatus.IN_PROGRESS) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (grayRelease.getNextStageTime() == null || now.isBefore(grayRelease.getNextStageTime())) {
            return;
        }

        Double currentErrorRate = calculateErrorRate(grayRelease);
        grayRelease.setCurrentErrorRate(currentErrorRate);

        if (currentErrorRate > grayRelease.getErrorRateThreshold()) {
            log.warn("Error rate {} exceeds threshold {} for gray release {}",
                    currentErrorRate, grayRelease.getErrorRateThreshold(), grayReleaseId);
            rollbackToPreviousStage(grayRelease);
            triggerAlert(grayRelease, "Error rate exceeded threshold");
            return;
        }

        List<Integer> releaseStages = parseReleaseStages(grayRelease.getReleaseStagesJson());
        int completedStages = grayRelease.getCompletedStages() != null ? grayRelease.getCompletedStages() : 0;

        if (completedStages >= releaseStages.size()) {
            completeGrayRelease(grayRelease);
            return;
        }

        Integer nextPercent = releaseStages.get(completedStages);
        advanceToNextPhase(grayRelease, nextPercent, completedStages + 1);
    }

    @Auditable(resourceType = "GrayRelease", operationType = OperationType.GRAY_RELEASE_FULL)
    @Transactional
    public GrayReleaseDTO.GrayReleaseResponse performFull(Long id, String operator) {
        GrayRelease grayRelease = grayReleaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GrayRelease", id.toString()));

        updateTrafficPercentage(grayRelease, 100);
        grayRelease.setCurrentPhase(GrayReleasePhase.FULL);
        grayRelease.setCurrentTrafficPercent(100);
        grayRelease.setStatus(GrayReleaseStatus.COMPLETED);
        grayRelease.setCompletedStages(grayRelease.getTotalStages());
        grayRelease.setUpdatedBy(operator);
        grayRelease.setNextStageTime(null);

        grayRelease = grayReleaseRepository.save(grayRelease);
        return toResponse(grayRelease);
    }

    @Auditable(resourceType = "GrayRelease", operationType = OperationType.GRAY_RELEASE_ROLLBACK)
    @Transactional
    public GrayReleaseDTO.GrayReleaseResponse performRollback(Long id, String reason, String operator) {
        GrayRelease grayRelease = grayReleaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GrayRelease", id.toString()));

        updateTrafficPercentage(grayRelease, 0);
        grayRelease.setCurrentTrafficPercent(0);
        grayRelease.setStatus(GrayReleaseStatus.ROLLED_BACK);
        grayRelease.setUpdatedBy(operator);
        grayRelease.setNextStageTime(null);

        if (grayRelease.getColorRule() != null) {
            grayRelease.getColorRule().setStatus(RuleStatus.INACTIVE);
            trafficColorRuleRepository.save(grayRelease.getColorRule());
        }

        grayRelease = grayReleaseRepository.save(grayRelease);

        auditLogService.log(
                "GrayRelease",
                grayRelease.getId().toString(),
                OperationType.GRAY_RELEASE_ROLLBACK,
                null,
                grayRelease,
                true,
                reason,
                grayRelease.getTenant()
        );

        return toResponse(grayRelease);
    }

    @Auditable(resourceType = "GrayRelease", operationType = OperationType.GRAY_RELEASE_PAUSE)
    @Transactional
    public GrayReleaseDTO.GrayReleaseResponse performPause(Long id, String operator) {
        GrayRelease grayRelease = grayReleaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GrayRelease", id.toString()));

        if (grayRelease.getStatus() != GrayReleaseStatus.IN_PROGRESS) {
            throw new BusinessException("Only IN_PROGRESS releases can be paused");
        }

        grayRelease.setStatus(GrayReleaseStatus.PAUSED);
        grayRelease.setUpdatedBy(operator);

        grayRelease = grayReleaseRepository.save(grayRelease);
        return toResponse(grayRelease);
    }

    @Auditable(resourceType = "GrayRelease", operationType = OperationType.GRAY_RELEASE_RESUME)
    @Transactional
    public GrayReleaseDTO.GrayReleaseResponse performResume(Long id, String operator) {
        GrayRelease grayRelease = grayReleaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("GrayRelease", id.toString()));

        if (grayRelease.getStatus() != GrayReleaseStatus.PAUSED) {
            throw new BusinessException("Only PAUSED releases can be resumed");
        }

        LocalDateTime now = LocalDateTime.now();
        grayRelease.setStatus(GrayReleaseStatus.IN_PROGRESS);
        grayRelease.setPhaseStartTime(now);
        grayRelease.setNextStageTime(now.plusMinutes(grayRelease.getObservationMinutesPerStage()));
        grayRelease.setUpdatedBy(operator);

        grayRelease = grayReleaseRepository.save(grayRelease);
        return toResponse(grayRelease);
    }

    @Transactional
    public GrayReleaseDTO.GrayReleaseResponse performAction(
            Long id, GrayReleaseDTO.GrayReleaseActionRequest request, String operator) {

        String action = request.getAction() != null ? request.getAction().toUpperCase() : "";
        String reason = request.getReason();

        return switch (action) {
            case "FULL" -> performFull(id, operator);
            case "ROLLBACK" -> performRollback(id, reason, operator);
            case "PAUSE" -> performPause(id, operator);
            case "RESUME" -> performResume(id, operator);
            default -> throw new BusinessException("Invalid action: " + action);
        };
    }

    private void updateRouteRuleTargetBackends(RouteRule routeRule, int grayPercent) {
        List<TargetBackend> existingBackends = routeRule.getTargetBackends();
        if (existingBackends == null || existingBackends.isEmpty()) {
            throw new BusinessException("Route rule has no target backends configured");
        }

        List<TargetBackend> newBackends = new ArrayList<>();

        TargetBackend originalBackend = existingBackends.get(0);
        TargetBackend oldBackend = TargetBackend.builder()
                .url(originalBackend.getUrl())
                .weight(100 - grayPercent)
                .colorTag(null)
                .build();
        newBackends.add(oldBackend);

        TargetBackend newBackend = TargetBackend.builder()
                .url(originalBackend.getUrl())
                .weight(grayPercent)
                .colorTag(GRAY_COLOR_TAG)
                .build();
        newBackends.add(newBackend);

        for (int i = 1; i < existingBackends.size(); i++) {
            TargetBackend backend = existingBackends.get(i);
            newBackends.add(TargetBackend.builder()
                    .url(backend.getUrl())
                    .weight(backend.getWeight())
                    .colorTag(backend.getColorTag())
                    .build());
        }

        routeRule.setTargetBackends(newBackends);
        routeRuleRepository.save(routeRule);

        createRouteRuleVersion(routeRule, "Gray release traffic split: " + grayPercent + "% to gray",
                routeRule.getUpdatedBy());
    }

    private void updateTrafficPercentage(GrayRelease grayRelease, int newPercent) {
        RouteRule routeRule = grayRelease.getRouteRule();
        List<TargetBackend> existingBackends = routeRule.getTargetBackends();
        if (existingBackends == null || existingBackends.isEmpty()) {
            return;
        }

        List<TargetBackend> newBackends = new ArrayList<>();
        int totalWeight = 0;

        for (TargetBackend backend : existingBackends) {
            if (GRAY_COLOR_TAG.equals(backend.getColorTag())) {
                if (newPercent > 0) {
                    newBackends.add(TargetBackend.builder()
                            .url(backend.getUrl())
                            .weight(newPercent)
                            .colorTag(GRAY_COLOR_TAG)
                            .build());
                    totalWeight += newPercent;
                }
            } else if (backend.getColorTag() == null) {
                int oldWeight = 100 - newPercent;
                if (oldWeight > 0) {
                    newBackends.add(TargetBackend.builder()
                            .url(backend.getUrl())
                            .weight(oldWeight)
                            .colorTag(null)
                            .build());
                    totalWeight += oldWeight;
                }
            } else {
                newBackends.add(TargetBackend.builder()
                        .url(backend.getUrl())
                        .weight(backend.getWeight())
                        .colorTag(backend.getColorTag())
                        .build());
                totalWeight += backend.getWeight() != null ? backend.getWeight() : 0;
            }
        }

        if (totalWeight < 100 && !newBackends.isEmpty()) {
            TargetBackend first = newBackends.get(0);
            first.setWeight(first.getWeight() + (100 - totalWeight));
        }

        routeRule.setTargetBackends(newBackends);
        routeRuleRepository.save(routeRule);

        if (grayRelease.getColorRule() != null) {
            TrafficColorRule colorRule = grayRelease.getColorRule();
            colorRule.setConditionValue(Integer.toString(newPercent));
            trafficColorRuleRepository.save(colorRule);
        }

        createRouteRuleVersion(routeRule, "Gray release traffic updated to " + newPercent + "%",
                grayRelease.getUpdatedBy());
    }

    private void advanceToNextPhase(GrayRelease grayRelease, int newPercent, int completedStages) {
        updateTrafficPercentage(grayRelease, newPercent);

        GrayReleasePhase nextPhase = getNextPhase(grayRelease.getCurrentPhase(), newPercent);
        LocalDateTime now = LocalDateTime.now();

        grayRelease.setCurrentPhase(nextPhase);
        grayRelease.setCurrentTrafficPercent(newPercent);
        grayRelease.setCompletedStages(completedStages);
        grayRelease.setPhaseStartTime(now);
        grayRelease.setNextStageTime(now.plusMinutes(grayRelease.getObservationMinutesPerStage()));

        if (newPercent >= 100) {
            completeGrayRelease(grayRelease);
            return;
        }

        grayReleaseRepository.save(grayRelease);

        log.info("Gray release {} advanced to phase {} with {}% traffic",
                grayRelease.getId(), nextPhase, newPercent);
    }

    private void rollbackToPreviousStage(GrayRelease grayRelease) {
        List<Integer> releaseStages = parseReleaseStages(grayRelease.getReleaseStagesJson());
        int completedStages = grayRelease.getCompletedStages() != null ? grayRelease.getCompletedStages() : 0;

        int rollbackPercent;
        if (completedStages <= 1 || releaseStages.isEmpty()) {
            rollbackPercent = 0;
        } else {
            rollbackPercent = releaseStages.get(completedStages - 2);
        }

        updateTrafficPercentage(grayRelease, rollbackPercent);

        grayRelease.setCurrentTrafficPercent(rollbackPercent);
        grayRelease.setStatus(GrayReleaseStatus.FAILED);
        grayRelease.setNextStageTime(null);

        if (grayRelease.getColorRule() != null) {
            grayRelease.getColorRule().setStatus(RuleStatus.INACTIVE);
            trafficColorRuleRepository.save(grayRelease.getColorRule());
        }

        grayReleaseRepository.save(grayRelease);

        log.warn("Gray release {} rolled back to {}% traffic due to high error rate",
                grayRelease.getId(), rollbackPercent);
    }

    private void completeGrayRelease(GrayRelease grayRelease) {
        updateTrafficPercentage(grayRelease, 100);

        grayRelease.setCurrentPhase(GrayReleasePhase.FULL);
        grayRelease.setCurrentTrafficPercent(100);
        grayRelease.setStatus(GrayReleaseStatus.COMPLETED);
        grayRelease.setCompletedStages(grayRelease.getTotalStages());
        grayRelease.setNextStageTime(null);

        if (grayRelease.getColorRule() != null) {
            grayRelease.getColorRule().setStatus(RuleStatus.INACTIVE);
            trafficColorRuleRepository.save(grayRelease.getColorRule());
        }

        grayReleaseRepository.save(grayRelease);

        log.info("Gray release {} completed successfully", grayRelease.getId());
    }

    private Double calculateErrorRate(GrayRelease grayRelease) {
        try {
            Long routeRuleId = grayRelease.getRouteRule() != null ? grayRelease.getRouteRule().getId() : null;
            if (routeRuleId != null) {
                return metricsService.getRouteErrorRate(routeRuleId, grayRelease.getObservationMinutesPerStage());
            }

            Long tenantId = grayRelease.getTenant() != null ? grayRelease.getTenant().getId() : null;
            String appId = grayRelease.getApplication() != null ? grayRelease.getApplication().getAppId() : null;

            Map<String, Object> statusMetrics = metricsService.getStatusMetrics(
                    tenantId, appId, grayRelease.getObservationMinutesPerStage());

            @SuppressWarnings("unchecked")
            Map<String, Long> statusDistribution = (Map<String, Long>) statusMetrics.get("statusDistribution");
            if (statusDistribution == null || statusDistribution.isEmpty()) {
                return 0.0;
            }

            long totalRequests = 0;
            long serverErrorRequests = 0;

            for (Map.Entry<String, Long> entry : statusDistribution.entrySet()) {
                String statusCode = entry.getKey();
                Long count = entry.getValue() != null ? entry.getValue() : 0L;
                totalRequests += count;

                if (statusCode.startsWith("5")) {
                    serverErrorRequests += count;
                }
            }

            if (totalRequests == 0) {
                return 0.0;
            }

            return (serverErrorRequests * 100.0) / totalRequests;
        } catch (Exception e) {
            log.warn("Failed to calculate error rate for gray release {}", grayRelease.getId(), e);
            return 0.0;
        }
    }

    private void triggerAlert(GrayRelease grayRelease, String message) {
        log.error("ALERT: Gray release {} - {}", grayRelease.getId(), message);
    }

    private GrayReleasePhase getNextPhase(GrayReleasePhase currentPhase, int percent) {
        if (percent >= 100) {
            return GrayReleasePhase.FULL;
        }
        return switch (currentPhase) {
            case INITIAL -> GrayReleasePhase.STAGE_1;
            case STAGE_1 -> GrayReleasePhase.STAGE_2;
            case STAGE_2 -> GrayReleasePhase.STAGE_3;
            default -> GrayReleasePhase.STAGE_3;
        };
    }

    private List<Integer> parseReleaseStages(String releaseStagesJson) {
        if (releaseStagesJson == null || releaseStagesJson.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(releaseStagesJson, new TypeReference<List<Integer>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse release stages JSON", e);
            return new ArrayList<>();
        }
    }

    private void createRouteRuleVersion(RouteRule rule, String changeLog, String createdBy) {
        RouteRuleVersion latestVersion = routeRuleVersionRepository
                .findFirstByRouteRuleIdOrderByVersionDesc(rule.getId())
                .orElse(null);
        int newVersionNumber = latestVersion != null ? latestVersion.getVersion() + 1 : 1;

        String configSnapshot;
        try {
            configSnapshot = objectMapper.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            configSnapshot = "{}";
        }

        RouteRuleVersion version = RouteRuleVersion.builder()
                .versionId("v_" + rule.getRuleId() + "_" + newVersionNumber)
                .routeRule(rule)
                .version(newVersionNumber)
                .name(rule.getName())
                .description(rule.getDescription())
                .path(rule.getPath())
                .method(rule.getMethod())
                .targetUrl(rule.getTargetUrl())
                .priority(rule.getPriority())
                .status(rule.getStatus())
                .requiresAuth(rule.getRequiresAuth())
                .rateLimitEnabled(rule.getRateLimitEnabled())
                .circuitBreakerEnabled(rule.getCircuitBreakerEnabled())
                .targetBackends(rule.getTargetBackends())
                .connectTimeoutMs(rule.getConnectTimeoutMs())
                .readTimeoutMs(rule.getReadTimeoutMs())
                .maxRetries(rule.getMaxRetries())
                .retryOn5xx(rule.getRetryOn5xx())
                .retryOnTimeout(rule.getRetryOnTimeout())
                .retryIntervalMs(rule.getRetryIntervalMs())
                .requestHeadersToAdd(rule.getRequestHeadersToAdd())
                .requestHeadersToRemove(rule.getRequestHeadersToRemove())
                .pathPrefixReplacement(rule.getPathPrefixReplacement())
                .configSnapshot(configSnapshot)
                .changeLog(changeLog)
                .createdBy(createdBy != null ? createdBy : rule.getUpdatedBy())
                .build();

        routeRuleVersionRepository.save(version);
    }

    private GrayReleaseDTO.GrayReleaseResponse toResponse(GrayRelease grayRelease) {
        return GrayReleaseDTO.GrayReleaseResponse.builder()
                .id(grayRelease.getId())
                .grayReleaseId(grayRelease.getGrayReleaseId())
                .name(grayRelease.getName())
                .description(grayRelease.getDescription())
                .routeRuleId(grayRelease.getRouteRule() != null ? grayRelease.getRouteRule().getId() : null)
                .appId(grayRelease.getApplication() != null ? grayRelease.getApplication().getId() : null)
                .status(grayRelease.getStatus())
                .currentPhase(grayRelease.getCurrentPhase())
                .currentTrafficPercent(grayRelease.getCurrentTrafficPercent())
                .errorRateThreshold(grayRelease.getErrorRateThreshold())
                .currentErrorRate(grayRelease.getCurrentErrorRate())
                .nextStageTime(grayRelease.getNextStageTime())
                .totalStages(grayRelease.getTotalStages())
                .completedStages(grayRelease.getCompletedStages())
                .createdAt(grayRelease.getCreatedAt())
                .createdBy(grayRelease.getCreatedBy())
                .build();
    }

    private TrafficColorRuleDTO.TrafficColorRuleResponse toColorRuleResponse(TrafficColorRule rule) {
        return TrafficColorRuleDTO.TrafficColorRuleResponse.builder()
                .id(rule.getId())
                .ruleId(rule.getRuleId())
                .name(rule.getName())
                .description(rule.getDescription())
                .tenantId(rule.getTenant() != null ? rule.getTenant().getId() : null)
                .tenantName(rule.getTenant() != null ? rule.getTenant().getName() : null)
                .routeRuleId(rule.getRouteRule() != null ? rule.getRouteRule().getId() : null)
                .routeRuleName(rule.getRouteRule() != null ? rule.getRouteRule().getName() : null)
                .conditionType(rule.getConditionType())
                .conditionKey(rule.getConditionKey())
                .matchType(rule.getMatchType())
                .conditionValue(rule.getConditionValue())
                .colorTag(rule.getColorTag())
                .operation(rule.getOperation())
                .priority(rule.getPriority())
                .status(rule.getStatus())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private RouteRuleDTO.RouteRuleResponse toRouteRuleResponse(RouteRule rule) {
        return RouteRuleDTO.RouteRuleResponse.builder()
                .id(rule.getId())
                .ruleId(rule.getRuleId())
                .name(rule.getName())
                .description(rule.getDescription())
                .tenantId(rule.getTenant() != null ? rule.getTenant().getId() : null)
                .tenantName(rule.getTenant() != null ? rule.getTenant().getName() : null)
                .applicationId(rule.getApplication() != null ? rule.getApplication().getId() : null)
                .applicationName(rule.getApplication() != null ? rule.getApplication().getName() : null)
                .path(rule.getPath())
                .method(rule.getMethod())
                .targetUrl(rule.getTargetUrl())
                .priority(rule.getPriority())
                .status(rule.getStatus())
                .requiresAuth(rule.getRequiresAuth())
                .rateLimitEnabled(rule.getRateLimitEnabled())
                .circuitBreakerEnabled(rule.getCircuitBreakerEnabled())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .targetBackends(rule.getTargetBackends())
                .connectTimeoutMs(rule.getConnectTimeoutMs())
                .readTimeoutMs(rule.getReadTimeoutMs())
                .maxRetries(rule.getMaxRetries())
                .retryOn5xx(rule.getRetryOn5xx())
                .retryOnTimeout(rule.getRetryOnTimeout())
                .retryIntervalMs(rule.getRetryIntervalMs())
                .requestHeadersToAdd(rule.getRequestHeadersToAdd())
                .requestHeadersToRemove(rule.getRequestHeadersToRemove())
                .pathPrefixReplacement(rule.getPathPrefixReplacement())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
