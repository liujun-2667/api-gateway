package com.apigateway.admin.service;

import com.apigateway.admin.repository.ApiChangeRecordRepository;
import com.apigateway.admin.repository.ApiEndpointRepository;
import com.apigateway.admin.repository.ChangeRecordRemarkRepository;
import com.apigateway.common.dto.ApiDocDTO;
import com.apigateway.common.entity.ApiChangeRecord;
import com.apigateway.common.entity.ApiEndpoint;
import com.apigateway.common.entity.ChangeRecordRemark;
import com.apigateway.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionCompareService {

    private final ApiChangeRecordRepository changeRecordRepository;
    private final ApiEndpointRepository endpointRepository;
    private final ChangeRecordRemarkRepository remarkRepository;

    @Transactional(readOnly = true)
    public ApiDocDTO.VersionCompareResponse compareVersions(ApiDocDTO.VersionCompareRequest request) {
        ApiChangeRecord leftRecord = changeRecordRepository.findById(request.getLeftRecordId())
                .orElseThrow(() -> new NotFoundException("ApiChangeRecord", request.getLeftRecordId().toString()));

        ApiChangeRecord rightRecord = changeRecordRepository.findById(request.getRightRecordId())
                .orElseThrow(() -> new NotFoundException("ApiChangeRecord", request.getRightRecordId().toString()));

        Map<String, Object> leftRequestSchema = leftRecord.getRequestSchemaSnapshot();
        Map<String, Object> rightRequestSchema = rightRecord.getRequestSchemaSnapshot();
        Map<String, Object> leftResponseSchema = leftRecord.getResponseSchemaSnapshot();
        Map<String, Object> rightResponseSchema = rightRecord.getResponseSchemaSnapshot();

        if (leftRequestSchema == null || rightRequestSchema == null ||
            leftResponseSchema == null || rightResponseSchema == null) {
            ApiEndpoint endpoint = getEndpointFromRecord(leftRecord);
            if (leftRequestSchema == null) leftRequestSchema = endpoint.getRequestSchema();
            if (rightRequestSchema == null) rightRequestSchema = endpoint.getRequestSchema();
            if (leftResponseSchema == null) leftResponseSchema = endpoint.getResponseSchema();
            if (rightResponseSchema == null) rightResponseSchema = endpoint.getResponseSchema();
        }

        List<Map<String, Object>> requestSchemaDiff = compareSchemas(
                leftRequestSchema, rightRequestSchema, "request");
        List<Map<String, Object>> responseSchemaDiff = compareSchemas(
                leftResponseSchema, rightResponseSchema, "response");

        return ApiDocDTO.VersionCompareResponse.builder()
                .leftRecordId(leftRecord.getId())
                .rightRecordId(rightRecord.getId())
                .leftTimestamp(leftRecord.getCreatedAt())
                .rightTimestamp(rightRecord.getCreatedAt())
                .leftChangedBy(leftRecord.getChangedBy())
                .rightChangedBy(rightRecord.getChangedBy())
                .leftRequestSchema(leftRequestSchema)
                .rightRequestSchema(rightRequestSchema)
                .leftResponseSchema(leftResponseSchema)
                .rightResponseSchema(rightResponseSchema)
                .requestSchemaDiff(requestSchemaDiff)
                .responseSchemaDiff(responseSchemaDiff)
                .build();
    }

    private ApiEndpoint getEndpointFromRecord(ApiChangeRecord record) {
        Long endpointId = record.getEndpoint().getId();
        return endpointRepository.findById(endpointId)
                .orElseThrow(() -> new NotFoundException("ApiEndpoint", endpointId.toString()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> compareSchemas(
            Map<String, Object> left, Map<String, Object> right, String schemaType) {
        List<Map<String, Object>> differences = new ArrayList<>();

        Map<String, Object> leftProps = left != null ? (Map<String, Object>) left.get("properties") : null;
        Map<String, Object> rightProps = right != null ? (Map<String, Object>) right.get("properties") : null;

        if (leftProps == null && rightProps == null) {
            return differences;
        }

        Set<String> allKeys = new HashSet<>();
        if (leftProps != null) allKeys.addAll(leftProps.keySet());
        if (rightProps != null) allKeys.addAll(rightProps.keySet());

        for (String key : allKeys) {
            Map<String, Object> leftProp = leftProps != null ? (Map<String, Object>) leftProps.get(key) : null;
            Map<String, Object> rightProp = rightProps != null ? (Map<String, Object>) rightProps.get(key) : null;

            if (leftProp == null && rightProp != null) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("field", key);
                diff.put("path", schemaType + "." + key);
                diff.put("changeType", "ADD");
                diff.put("newValue", rightProp);
                diff.put("oldValue", null);
                differences.add(diff);
            } else if (leftProp != null && rightProp == null) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("field", key);
                diff.put("path", schemaType + "." + key);
                diff.put("changeType", "REMOVE");
                diff.put("oldValue", leftProp);
                diff.put("newValue", null);
                differences.add(diff);
            } else if (leftProp != null && rightProp != null) {
                String leftType = (String) leftProp.get("type");
                String rightType = (String) rightProp.get("type");
                if (!Objects.equals(leftType, rightType)) {
                    Map<String, Object> diff = new LinkedHashMap<>();
                    diff.put("field", key);
                    diff.put("path", schemaType + "." + key);
                    diff.put("changeType", "TYPE_CHANGE");
                    diff.put("oldValue", leftProp);
                    diff.put("newValue", rightProp);
                    diff.put("oldType", leftType);
                    diff.put("newType", rightType);
                    differences.add(diff);
                } else {
                    Object leftDesc = leftProp.get("description");
                    Object rightDesc = rightProp.get("description");
                    if (!Objects.equals(leftDesc, rightDesc)) {
                        Map<String, Object> diff = new LinkedHashMap<>();
                        diff.put("field", key);
                        diff.put("path", schemaType + "." + key);
                        diff.put("changeType", "MODIFY");
                        diff.put("oldValue", leftProp);
                        diff.put("newValue", rightProp);
                        differences.add(diff);
                    }
                }
            }
        }

        return differences;
    }

    @Transactional
    public ApiDocDTO.ChangeRemarkResponse addRemark(Long changeRecordId, ApiDocDTO.ChangeRemarkRequest request, String createdBy) {
        ApiChangeRecord record = changeRecordRepository.findById(changeRecordId)
                .orElseThrow(() -> new NotFoundException("ApiChangeRecord", changeRecordId.toString()));

        ChangeRecordRemark remark = ChangeRecordRemark.builder()
                .changeRecord(record)
                .fieldPath(request.getFieldPath())
                .remarkType(request.getRemarkType())
                .remark(request.getRemark())
                .createdBy(createdBy)
                .build();

        remark = remarkRepository.save(remark);
        return toRemarkResponse(remark);
    }

    @Transactional(readOnly = true)
    public List<ApiDocDTO.ChangeRemarkResponse> getRemarks(Long changeRecordId) {
        if (!changeRecordRepository.existsById(changeRecordId)) {
            throw new NotFoundException("ApiChangeRecord", changeRecordId.toString());
        }
        return remarkRepository.findByChangeRecordIdOrderByCreatedAtDesc(changeRecordId).stream()
                .map(this::toRemarkResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRemark(Long remarkId) {
        if (!remarkRepository.existsById(remarkId)) {
            throw new NotFoundException("ChangeRecordRemark", remarkId.toString());
        }
        remarkRepository.deleteById(remarkId);
    }

    private ApiDocDTO.ChangeRemarkResponse toRemarkResponse(ChangeRecordRemark remark) {
        return ApiDocDTO.ChangeRemarkResponse.builder()
                .id(remark.getId())
                .changeRecordId(remark.getChangeRecord() != null ? remark.getChangeRecord().getId() : null)
                .fieldPath(remark.getFieldPath())
                .remarkType(remark.getRemarkType())
                .remark(remark.getRemark())
                .createdBy(remark.getCreatedBy())
                .createdAt(remark.getCreatedAt())
                .updatedAt(remark.getUpdatedAt())
                .build();
    }
}
