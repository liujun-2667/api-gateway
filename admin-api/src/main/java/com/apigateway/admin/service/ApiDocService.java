package com.apigateway.admin.service;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.admin.repository.*;
import com.apigateway.common.dto.ApiDocDTO;
import com.apigateway.common.entity.*;
import com.apigateway.common.enums.DocStatus;
import com.apigateway.common.enums.HttpMethod;
import com.apigateway.common.enums.OperationType;
import com.apigateway.common.exception.BusinessException;
import com.apigateway.common.exception.NotFoundException;
import com.apigateway.common.utils.ApiKeyGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiDocService {

    private final ApiDocRepository apiDocRepository;
    private final ApiDocGroupRepository apiDocGroupRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final MockConfigRepository mockConfigRepository;
    private final DebugCaseRepository debugCaseRepository;
    private final ApiChangeRecordRepository apiChangeRecordRepository;
    private final ApplicationRepository applicationRepository;
    private final ChangeNotificationService changeNotificationService;
    private final ObjectMapper objectMapper;

    @Auditable(resourceType = "ApiDoc", operationType = OperationType.API_DOC_CREATE)
    @Transactional
    public ApiDocDTO.ApiDocResponse createApiDoc(ApiDocDTO.ApiDocCreateRequest request, String createdBy) {
        Application application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new NotFoundException("Application", request.getApplicationId().toString()));

        ApiDoc apiDoc = ApiDoc.builder()
                .docId("doc_" + ApiKeyGenerator.generateKeyId())
                .name(request.getName())
                .description(request.getDescription())
                .version(request.getVersion() != null ? request.getVersion() : "1.0.0")
                .application(application)
                .status(DocStatus.DRAFT)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        apiDoc = apiDocRepository.save(apiDoc);
        return toDocResponse(apiDoc);
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.ApiDocResponse> getApiDocsByAppId(Long applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new NotFoundException("Application", applicationId.toString());
        }
        return apiDocRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .map(this::toDocResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApiDocDTO.ApiDocResponse getApiDocById(Long id) {
        ApiDoc apiDoc = apiDocRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiDoc", id.toString()));
        return toDocResponseWithGroups(apiDoc);
    }

    @Auditable(resourceType = "ApiDoc", operationType = OperationType.API_DOC_UPDATE)
    @Transactional
    public ApiDocDTO.ApiDocResponse updateApiDoc(Long id, ApiDocDTO.ApiDocUpdateRequest request, String updatedBy) {
        ApiDoc apiDoc = apiDocRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiDoc", id.toString()));

        if (request.getName() != null) apiDoc.setName(request.getName());
        if (request.getDescription() != null) apiDoc.setDescription(request.getDescription());
        if (request.getVersion() != null) apiDoc.setVersion(request.getVersion());
        apiDoc.setUpdatedBy(updatedBy);

        apiDoc = apiDocRepository.save(apiDoc);
        return toDocResponse(apiDoc);
    }

    @Auditable(resourceType = "ApiDoc", operationType = OperationType.API_DOC_PUBLISH)
    @Transactional
    public ApiDocDTO.ApiDocResponse publishApiDoc(Long id, String updatedBy) {
        ApiDoc apiDoc = apiDocRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ApiDoc", id.toString()));

        ApiDoc previousState = cloneApiDoc(apiDoc);
        apiDoc.setStatus(DocStatus.PUBLISHED);
        apiDoc.setUpdatedBy(updatedBy);
        apiDoc = apiDocRepository.save(apiDoc);

        detectAndRecordChanges(apiDoc, previousState, updatedBy);

        return toDocResponse(apiDoc);
    }

    @Auditable(resourceType = "ApiDoc", operationType = OperationType.API_DOC_DELETE)
    @Transactional
    public void deleteApiDoc(Long id) {
        if (!apiDocRepository.existsById(id)) {
            throw new NotFoundException("ApiDoc", id.toString());
        }
        apiDocRepository.deleteById(id);
    }

    @Transactional
    public ApiDocDTO.ApiDocGroupResponse createGroup(Long docId, ApiDocDTO.ApiDocGroupCreateRequest request) {
        ApiDoc apiDoc = apiDocRepository.findById(docId)
                .orElseThrow(() -> new NotFoundException("ApiDoc", docId.toString()));

        ApiDocGroup group = ApiDocGroup.builder()
                .name(request.getName())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .apiDoc(apiDoc)
                .build();

        group = apiDocGroupRepository.save(group);
        return toGroupResponse(group);
    }

    @Transactional
    public void deleteGroup(Long groupId) {
        if (!apiDocGroupRepository.existsById(groupId)) {
            throw new NotFoundException("ApiDocGroup", groupId.toString());
        }
        apiDocGroupRepository.deleteById(groupId);
    }

    @Auditable(resourceType = "ApiEndpoint", operationType = OperationType.API_ENDPOINT_CREATE)
    @Transactional
    public ApiDocDTO.ApiEndpointResponse createEndpoint(Long groupId, ApiDocDTO.ApiEndpointCreateRequest request) {
        ApiDocGroup group = apiDocGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("ApiDocGroup", groupId.toString()));

        ApiEndpoint endpoint = ApiEndpoint.builder()
                .name(request.getName())
                .description(request.getDescription())
                .method(request.getMethod())
                .path(request.getPath())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .group(group)
                .requestParams(request.getRequestParams())
                .requestSchema(request.getRequestSchema())
                .responseSchema(request.getResponseSchema())
                .statusCodes(request.getStatusCodes())
                .deprecated(request.getDeprecated())
                .build();

        endpoint = apiEndpointRepository.save(endpoint);
        return toEndpointResponse(endpoint);
    }

    @Auditable(resourceType = "ApiEndpoint", operationType = OperationType.API_ENDPOINT_UPDATE)
    @Transactional
    public ApiDocDTO.ApiEndpointResponse updateEndpoint(Long endpointId, ApiDocDTO.ApiEndpointUpdateRequest request, String updatedBy) {
        ApiEndpoint endpoint = apiEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new NotFoundException("ApiEndpoint", endpointId.toString()));

        ApiEndpoint previousState = cloneEndpoint(endpoint);

        if (request.getName() != null) endpoint.setName(request.getName());
        if (request.getDescription() != null) endpoint.setDescription(request.getDescription());
        if (request.getMethod() != null) endpoint.setMethod(request.getMethod());
        if (request.getPath() != null) endpoint.setPath(request.getPath());
        if (request.getSortOrder() != null) endpoint.setSortOrder(request.getSortOrder());
        if (request.getRequestParams() != null) endpoint.setRequestParams(request.getRequestParams());
        if (request.getRequestSchema() != null) endpoint.setRequestSchema(request.getRequestSchema());
        if (request.getResponseSchema() != null) endpoint.setResponseSchema(request.getResponseSchema());
        if (request.getStatusCodes() != null) endpoint.setStatusCodes(request.getStatusCodes());
        if (request.getDeprecated() != null) endpoint.setDeprecated(request.getDeprecated());

        endpoint = apiEndpointRepository.save(endpoint);

        if (isEndpointDocPublished(endpoint)) {
            detectEndpointChanges(endpoint, previousState, updatedBy);
        }

        return toEndpointResponse(endpoint);
    }

    @Auditable(resourceType = "ApiEndpoint", operationType = OperationType.API_ENDPOINT_DELETE)
    @Transactional
    public void deleteEndpoint(Long endpointId) {
        if (!apiEndpointRepository.existsById(endpointId)) {
            throw new NotFoundException("ApiEndpoint", endpointId.toString());
        }
        apiEndpointRepository.deleteById(endpointId);
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.ApiChangeRecordResponse> getChangeHistory(Long endpointId) {
        if (!apiEndpointRepository.existsById(endpointId)) {
            throw new NotFoundException("ApiEndpoint", endpointId.toString());
        }
        return apiChangeRecordRepository.findByEndpointIdOrderByCreatedAtDesc(endpointId).stream()
                .map(this::toChangeRecordResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.ApiChangeRecordResponse> getDocChangeHistory(Long docId) {
        if (!apiDocRepository.existsById(docId)) {
            throw new NotFoundException("ApiDoc", docId.toString());
        }
        List<ApiDocGroup> groups = apiDocGroupRepository.findByApiDocId(docId);
        List<Long> groupIds = groups.stream().map(ApiDocGroup::getId).collect(Collectors.toList());
        List<Long> endpointIds = new ArrayList<>();
        for (Long groupId : groupIds) {
            apiEndpointRepository.findByGroupId(groupId).forEach(e -> endpointIds.add(e.getId()));
        }
        if (endpointIds.isEmpty()) {
            return Collections.emptyList();
        }
        return apiChangeRecordRepository.findByEndpointIdInOrderByCreatedAtDesc(endpointIds).stream()
                .map(this::toChangeRecordResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApiDocDTO.ApiDocResponse importOpenApi(Long applicationId, ApiDocDTO.OpenApiImportRequest request, String createdBy) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application", applicationId.toString()));

        try {
            Map<String, Object> openApiDoc = objectMapper.readValue(request.getOpenApiContent(), new TypeReference<Map<String, Object>>() {});

            String title = "Imported API";
            String version = "1.0.0";
            String description = "";

            Map<String, Object> info = (Map<String, Object>) openApiDoc.get("info");
            if (info != null) {
                if (info.get("title") != null) title = info.get("title").toString();
                if (info.get("version") != null) version = info.get("version").toString();
                if (info.get("description") != null) description = info.get("description").toString();
            }

            ApiDoc apiDoc = ApiDoc.builder()
                    .docId("doc_" + ApiKeyGenerator.generateKeyId())
                    .name(title)
                    .description(description)
                    .version(version)
                    .application(application)
                    .status(DocStatus.DRAFT)
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();
            apiDoc = apiDocRepository.save(apiDoc);

            Map<String, Object> paths = (Map<String, Object>) openApiDoc.get("paths");
            if (paths != null) {
                Map<String, ApiDocGroup> tagGroups = new LinkedHashMap<>();
                Map<String, Object> tags = (Map<String, Object>) openApiDoc.get("tags");

                int groupSortOrder = 0;
                if (tags != null) {
                    for (Map.Entry<String, Object> tagEntry : tags.entrySet()) {
                        Map<String, Object> tagInfo = (Map<String, Object>) tagEntry.getValue();
                        String tagName = tagInfo.get("name") != null ? tagInfo.get("name").toString() : tagEntry.getKey();
                        String tagDesc = tagInfo.get("description") != null ? tagInfo.get("description").toString() : "";

                        ApiDocGroup group = ApiDocGroup.builder()
                                .name(tagName)
                                .description(tagDesc)
                                .sortOrder(groupSortOrder++)
                                .apiDoc(apiDoc)
                                .build();
                        group = apiDocGroupRepository.save(group);
                        tagGroups.put(tagName, group);
                    }
                }

                if (tagGroups.isEmpty()) {
                    ApiDocGroup defaultGroup = ApiDocGroup.builder()
                            .name("默认分组")
                            .description("默认分组")
                            .sortOrder(0)
                            .apiDoc(apiDoc)
                            .build();
                    defaultGroup = apiDocGroupRepository.save(defaultGroup);
                    tagGroups.put("default", defaultGroup);
                }

                Map<String, Object> components = (Map<String, Object>) openApiDoc.get("components");
                Map<String, Object> schemas = components != null ? (Map<String, Object>) components.get("schemas") : Collections.emptyMap();

                int endpointSortOrder = 0;
                for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                    String pathStr = pathEntry.getKey();
                    Map<String, Object> methods = (Map<String, Object>) pathEntry.getValue();

                    for (Map.Entry<String, Object> methodEntry : methods.entrySet()) {
                        String methodStr = methodEntry.getKey().toUpperCase();
                        if (!isHttpMethod(methodStr)) continue;

                        Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();

                        String epName = operation.get("summary") != null ? operation.get("summary").toString() : methodStr + " " + pathStr;
                        String epDesc = operation.get("description") != null ? operation.get("description").toString() : "";

                        List<String> epTags = (List<String>) operation.get("tags");
                        String targetTag = (epTags != null && !epTags.isEmpty()) ? epTags.get(0) : "default";

                        ApiDocGroup targetGroup = tagGroups.get(targetTag);
                        if (targetGroup == null) {
                            targetGroup = tagGroups.values().iterator().next();
                        }

                        List<Map<String, Object>> requestParams = parseOpenApiParameters(operation);
                        Map<String, Object> requestSchema = parseOpenApiRequestBody(operation, schemas);
                        Map<String, Object> responseSchema = parseOpenApiResponse(operation, schemas);
                        List<Map<String, Object>> statusCodes = parseOpenApiStatusCodes(operation);

                        ApiEndpoint endpoint = ApiEndpoint.builder()
                                .name(epName)
                                .description(epDesc)
                                .method(HttpMethod.valueOf(methodStr))
                                .path(pathStr)
                                .sortOrder(endpointSortOrder++)
                                .group(targetGroup)
                                .requestParams(requestParams)
                                .requestSchema(requestSchema)
                                .responseSchema(responseSchema)
                                .statusCodes(statusCodes)
                                .build();

                        apiEndpointRepository.save(endpoint);
                    }
                }
            }

            return toDocResponseWithGroups(apiDoc);
        } catch (Exception e) {
            log.error("Failed to import OpenAPI document", e);
            throw new BusinessException("Failed to import OpenAPI document: " + e.getMessage());
        }
    }

    private boolean isHttpMethod(String method) {
        try {
            HttpMethod.valueOf(method);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private List<Map<String, Object>> parseOpenApiParameters(Map<String, Object> operation) {
        List<Map<String, Object>> params = new ArrayList<>();
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        if (parameters != null) {
            for (Map<String, Object> param : parameters) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("name", param.get("name"));
                p.put("in", param.get("in"));
                p.put("required", param.get("required"));
                p.put("description", param.get("description"));
                Map<String, Object> schema = (Map<String, Object>) param.get("schema");
                if (schema != null) {
                    p.put("type", schema.get("type"));
                    p.put("example", schema.get("example"));
                }
                params.add(p);
            }
        }
        return params;
    }

    private Map<String, Object> parseOpenApiRequestBody(Map<String, Object> operation, Map<String, Object> schemas) {
        Map<String, Object> requestBody = (Map<String, Object>) operation.get("requestBody");
        if (requestBody == null) return null;
        return resolveSchema(requestBody, schemas);
    }

    private Map<String, Object> parseOpenApiResponse(Map<String, Object> operation, Map<String, Object> schemas) {
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        if (responses == null) return null;
        Map<String, Object> successResponse = (Map<String, Object>) responses.get("200");
        if (successResponse == null) successResponse = (Map<String, Object>) responses.get("201");
        if (successResponse == null) {
            for (Map.Entry<String, Object> entry : responses.entrySet()) {
                if (entry.getKey().startsWith("2")) {
                    successResponse = (Map<String, Object>) entry.getValue();
                    break;
                }
            }
        }
        if (successResponse == null) return null;
        return resolveSchema(successResponse, schemas);
    }

    private List<Map<String, Object>> parseOpenApiStatusCodes(Map<String, Object> operation) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        if (responses != null) {
            for (Map.Entry<String, Object> entry : responses.entrySet()) {
                Map<String, Object> statusInfo = new LinkedHashMap<>();
                statusInfo.put("statusCode", entry.getKey());
                Map<String, Object> responseObj = (Map<String, Object>) entry.getValue();
                if (responseObj != null && responseObj.get("description") != null) {
                    statusInfo.put("description", responseObj.get("description"));
                }
                result.add(statusInfo);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveSchema(Map<String, Object> container, Map<String, Object> schemas) {
        Map<String, Object> content = (Map<String, Object>) container.get("content");
        if (content != null) {
            for (Map.Entry<String, Object> contentEntry : content.entrySet()) {
                Map<String, Object> mediaType = (Map<String, Object>) contentEntry.getValue();
                Map<String, Object> schema = (Map<String, Object>) mediaType.get("schema");
                if (schema != null) {
                    return resolveSchemaRefs(schema, schemas);
                }
            }
        }
        Map<String, Object> schema = (Map<String, Object>) container.get("schema");
        if (schema != null) {
            return resolveSchemaRefs(schema, schemas);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveSchemaRefs(Map<String, Object> schema, Map<String, Object> schemas) {
        if (schema == null) return null;
        Map<String, Object> resolved = new LinkedHashMap<>(schema);

        Object ref = schema.get("$ref");
        if (ref != null) {
            String refStr = ref.toString();
            String schemaName = refStr.substring(refStr.lastIndexOf("/") + 1);
            if (schemas != null && schemas.containsKey(schemaName)) {
                Map<String, Object> referenced = (Map<String, Object>) schemas.get(schemaName);
                resolved = new LinkedHashMap<>(referenced);
                resolved.put("x-ref-name", schemaName);
            }
        }

        if (resolved.containsKey("properties")) {
            Map<String, Object> properties = (Map<String, Object>) resolved.get("properties");
            Map<String, Object> resolvedProps = new LinkedHashMap<>();
            for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
                Map<String, Object> propSchema = (Map<String, Object>) propEntry.getValue();
                resolvedProps.put(propEntry.getKey(), resolveSchemaRefs(propSchema, schemas));
            }
            resolved.put("properties", resolvedProps);
        }

        if (resolved.containsKey("items")) {
            Map<String, Object> items = (Map<String, Object>) resolved.get("items");
            resolved.put("items", resolveSchemaRefs(items, schemas));
        }

        return resolved;
    }

    private boolean isEndpointDocPublished(ApiEndpoint endpoint) {
        ApiDocGroup group = endpoint.getGroup();
        if (group == null) return false;
        ApiDoc doc = group.getApiDoc();
        return doc != null && doc.getStatus() == DocStatus.PUBLISHED;
    }

    private void detectAndRecordChanges(ApiDoc apiDoc, ApiDoc previousState, String changedBy) {
        // Used when republishing - compare current endpoint schemas with previous
    }

    private void detectEndpointChanges(ApiEndpoint current, ApiEndpoint previous, String changedBy) {
        List<Map<String, Object>> changeDetails = new ArrayList<>();
        String changeSummary = "";

        if (!Objects.equals(current.getName(), previous.getName())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("field", "name");
            detail.put("oldValue", previous.getName());
            detail.put("newValue", current.getName());
            detail.put("changeType", "MODIFY");
            changeDetails.add(detail);
        }
        if (!Objects.equals(current.getMethod(), previous.getMethod())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("field", "method");
            detail.put("oldValue", previous.getMethod());
            detail.put("newValue", current.getMethod());
            detail.put("changeType", "MODIFY");
            changeDetails.add(detail);
        }
        if (!Objects.equals(current.getPath(), previous.getPath())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("field", "path");
            detail.put("oldValue", previous.getPath());
            detail.put("newValue", current.getPath());
            detail.put("changeType", "MODIFY");
            changeDetails.add(detail);
        }
        if (!Objects.equals(current.getRequestSchema(), previous.getRequestSchema())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("field", "requestSchema");
            detail.put("oldValue", previous.getRequestSchema());
            detail.put("newValue", current.getRequestSchema());
            detail.put("changeType", "MODIFY");
            changeDetails.add(detail);
        }
        if (!Objects.equals(current.getResponseSchema(), previous.getResponseSchema())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("field", "responseSchema");
            detail.put("oldValue", previous.getResponseSchema());
            detail.put("newValue", current.getResponseSchema());
            detail.put("changeType", "MODIFY");
            changeDetails.add(detail);
        }
        if (!Objects.equals(current.getStatusCodes(), previous.getStatusCodes())) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("field", "statusCodes");
            detail.put("oldValue", previous.getStatusCodes());
            detail.put("newValue", current.getStatusCodes());
            detail.put("changeType", "MODIFY");
            changeDetails.add(detail);
        }

        if (!changeDetails.isEmpty()) {
            changeSummary = current.getName() + " - " + changeDetails.size() + " field(s) changed";

            ApiChangeRecord record = ApiChangeRecord.builder()
                    .endpoint(current)
                    .changeType("MODIFY")
                    .changeSummary(changeSummary)
                    .changeDetails(changeDetails)
                    .changedBy(changedBy)
                    .build();
            apiChangeRecordRepository.save(record);

            Long docId = getDocIdForEndpoint(current);
            changeNotificationService.notifyEndpointChange(
                    docId, current.getId(), current.getName(), "MODIFY", changeSummary);
        }
    }

    private ApiDoc cloneApiDoc(ApiDoc doc) {
        return ApiDoc.builder()
                .id(doc.getId())
                .docId(doc.getDocId())
                .name(doc.getName())
                .description(doc.getDescription())
                .version(doc.getVersion())
                .application(doc.getApplication())
                .status(doc.getStatus())
                .createdBy(doc.getCreatedBy())
                .updatedBy(doc.getUpdatedBy())
                .build();
    }

    private Long getDocIdForEndpoint(ApiEndpoint endpoint) {
        ApiDocGroup group = endpoint.getGroup();
        if (group != null && group.getApiDoc() != null) {
            return group.getApiDoc().getId();
        }
        return null;
    }

    private ApiEndpoint cloneEndpoint(ApiEndpoint ep) {
        return ApiEndpoint.builder()
                .id(ep.getId())
                .name(ep.getName())
                .description(ep.getDescription())
                .method(ep.getMethod())
                .path(ep.getPath())
                .sortOrder(ep.getSortOrder())
                .group(ep.getGroup())
                .requestParams(ep.getRequestParams() != null ? new ArrayList<>(ep.getRequestParams()) : null)
                .requestSchema(ep.getRequestSchema() != null ? new LinkedHashMap<>(ep.getRequestSchema()) : null)
                .responseSchema(ep.getResponseSchema() != null ? new LinkedHashMap<>(ep.getResponseSchema()) : null)
                .statusCodes(ep.getStatusCodes() != null ? new ArrayList<>(ep.getStatusCodes()) : null)
                .deprecated(ep.getDeprecated())
                .build();
    }

    private ApiDocDTO.ApiDocResponse toDocResponse(ApiDoc doc) {
        return ApiDocDTO.ApiDocResponse.builder()
                .id(doc.getId())
                .docId(doc.getDocId())
                .name(doc.getName())
                .description(doc.getDescription())
                .version(doc.getVersion())
                .applicationId(doc.getApplication() != null ? doc.getApplication().getId() : null)
                .applicationName(doc.getApplication() != null ? doc.getApplication().getName() : null)
                .status(doc.getStatus())
                .createdBy(doc.getCreatedBy())
                .updatedBy(doc.getUpdatedBy())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private ApiDocDTO.ApiDocResponse toDocResponseWithGroups(ApiDoc doc) {
        ApiDocDTO.ApiDocResponse response = toDocResponse(doc);
        List<ApiDocGroup> groups = apiDocGroupRepository.findByApiDocIdOrderBySortOrder(doc.getId());
        List<ApiDocDTO.ApiDocGroupResponse> groupResponses = new ArrayList<>();
        for (ApiDocGroup group : groups) {
            ApiDocDTO.ApiDocGroupResponse groupResp = toGroupResponseWithEndpoints(group);
            groupResponses.add(groupResp);
        }
        response.setGroups(groupResponses);
        return response;
    }

    private ApiDocDTO.ApiDocGroupResponse toGroupResponse(ApiDocGroup group) {
        return ApiDocDTO.ApiDocGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .sortOrder(group.getSortOrder())
                .docId(group.getApiDoc() != null ? group.getApiDoc().getId() : null)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    private ApiDocDTO.ApiDocGroupResponse toGroupResponseWithEndpoints(ApiDocGroup group) {
        ApiDocDTO.ApiDocGroupResponse response = toGroupResponse(group);
        List<ApiEndpoint> endpoints = apiEndpointRepository.findByGroupIdOrderBySortOrder(group.getId());
        response.setEndpoints(endpoints.stream().map(this::toEndpointResponse).collect(Collectors.toList()));
        return response;
    }

    private ApiDocDTO.ApiEndpointResponse toEndpointResponse(ApiEndpoint endpoint) {
        ApiDocDTO.ApiEndpointResponse response = ApiDocDTO.ApiEndpointResponse.builder()
                .id(endpoint.getId())
                .name(endpoint.getName())
                .description(endpoint.getDescription())
                .method(endpoint.getMethod())
                .path(endpoint.getPath())
                .sortOrder(endpoint.getSortOrder())
                .groupId(endpoint.getGroup() != null ? endpoint.getGroup().getId() : null)
                .groupName(endpoint.getGroup() != null ? endpoint.getGroup().getName() : null)
                .requestParams(endpoint.getRequestParams())
                .requestSchema(endpoint.getRequestSchema())
                .responseSchema(endpoint.getResponseSchema())
                .statusCodes(endpoint.getStatusCodes())
                .deprecated(endpoint.getDeprecated())
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .build();

        mockConfigRepository.findByEndpointId(endpoint.getId()).ifPresent(mc -> {
            response.setMockConfig(toMockConfigResponse(mc));
        });

        return response;
    }

    private ApiDocDTO.MockConfigResponse toMockConfigResponse(MockConfig mc) {
        return ApiDocDTO.MockConfigResponse.builder()
                .id(mc.getId())
                .mockConfigId(mc.getMockConfigId())
                .endpointId(mc.getEndpoint() != null ? mc.getEndpoint().getId() : null)
                .routeRuleId(mc.getRouteRule() != null ? mc.getRouteRule().getId() : null)
                .routeRuleName(mc.getRouteRule() != null ? mc.getRouteRule().getName() : null)
                .enabled(mc.getEnabled())
                .delayMs(mc.getDelayMs())
                .faultInjectionPercent(mc.getFaultInjectionPercent())
                .faultErrorCode(mc.getFaultErrorCode())
                .createdBy(mc.getCreatedBy())
                .createdAt(mc.getCreatedAt())
                .updatedAt(mc.getUpdatedAt())
                .build();
    }

    private ApiDocDTO.ApiChangeRecordResponse toChangeRecordResponse(ApiChangeRecord record) {
        return ApiDocDTO.ApiChangeRecordResponse.builder()
                .id(record.getId())
                .endpointId(record.getEndpoint() != null ? record.getEndpoint().getId() : null)
                .endpointName(record.getEndpoint() != null ? record.getEndpoint().getName() : null)
                .changeType(record.getChangeType())
                .changeSummary(record.getChangeSummary())
                .changeDetails(record.getChangeDetails())
                .changedBy(record.getChangedBy())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
