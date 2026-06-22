package com.apigateway.admin.controller;

import com.apigateway.admin.service.GrayReleaseService;
import com.apigateway.admin.util.SecurityUtil;
import com.apigateway.common.dto.GrayReleaseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GrayReleaseController {

    private final GrayReleaseService grayReleaseService;
    private final SecurityUtil securityUtil;

    @PostMapping("/apps/{appId}/gray-releases")
    public ResponseEntity<GrayReleaseDTO.GrayReleaseResponse> createGrayRelease(
            @PathVariable Long appId,
            @Valid @RequestBody GrayReleaseDTO.GrayReleaseCreateRequest request) {
        String createdBy = securityUtil.getCurrentUsername();
        return new ResponseEntity<>(grayReleaseService.createGrayRelease(request, createdBy), HttpStatus.CREATED);
    }

    @GetMapping("/apps/{appId}/gray-releases")
    public ResponseEntity<List<GrayReleaseDTO.GrayReleaseResponse>> getGrayReleasesByAppId(
            @PathVariable Long appId) {
        return ResponseEntity.ok(grayReleaseService.getGrayReleasesByAppId(appId));
    }

    @GetMapping("/apps/{appId}/gray-releases/{id}")
    public ResponseEntity<GrayReleaseDTO.GrayReleaseResponse> getGrayReleaseById(
            @PathVariable Long appId,
            @PathVariable Long id) {
        return ResponseEntity.ok(grayReleaseService.getGrayReleaseById(id));
    }

    @GetMapping("/apps/{appId}/gray-releases/{id}/status")
    public ResponseEntity<GrayReleaseDTO.GrayReleaseStatusResponse> getGrayReleaseStatus(
            @PathVariable Long appId,
            @PathVariable Long id) {
        return ResponseEntity.ok(grayReleaseService.getGrayReleaseStatus(id));
    }

    @PostMapping("/apps/{appId}/gray-releases/{id}/action")
    public ResponseEntity<GrayReleaseDTO.GrayReleaseResponse> performAction(
            @PathVariable Long appId,
            @PathVariable Long id,
            @Valid @RequestBody GrayReleaseDTO.GrayReleaseActionRequest request) {
        String operator = securityUtil.getCurrentUsername();
        return ResponseEntity.ok(grayReleaseService.performAction(id, request, operator));
    }

    @GetMapping("/gray-releases/active")
    public ResponseEntity<List<GrayReleaseDTO.GrayReleaseResponse>> getActiveGrayReleases() {
        return ResponseEntity.ok(grayReleaseService.getActiveGrayReleases());
    }
}
