package com.apigateway.admin.service;

import com.apigateway.common.dto.ApiDocDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyEndpointChange(Long docId, Long endpointId, String endpointName, String changeType, String changeSummary) {
        try {
            ApiDocDTO.ChangeNotification notification = ApiDocDTO.ChangeNotification.builder()
                    .docId(docId)
                    .endpointId(endpointId)
                    .endpointName(endpointName)
                    .changeType(changeType)
                    .changeSummary(changeSummary)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/api-docs/" + docId, notification);
            messagingTemplate.convertAndSend("/topic/api-docs/changes", notification);
            log.info("Sent change notification for endpoint {} in doc {}", endpointId, docId);
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification", e);
        }
    }

    public void notifyDocChange(Long docId, String changeType, String message) {
        try {
            ApiDocDTO.ChangeNotification notification = ApiDocDTO.ChangeNotification.builder()
                    .docId(docId)
                    .changeType(changeType)
                    .changeSummary(message)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/api-docs/" + docId, notification);
        } catch (Exception e) {
            log.error("Failed to send doc-level WebSocket notification", e);
        }
    }
}
