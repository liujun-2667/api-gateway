package com.apigateway.admin.aspect;

import com.apigateway.admin.annotation.Auditable;
import com.apigateway.common.entity.Tenant;
import com.apigateway.common.enums.OperationType;
import com.apigateway.admin.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        String resourceType = auditable.resourceType();
        OperationType operationType = auditable.operationType();

        Object beforeValue = extractBeforeValue(parameters, args);
        String resourceId = extractResourceId(parameters, args);
        Tenant tenant = extractTenant(parameters, args);

        Object result;
        boolean success = true;
        String errorMessage = null;

        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            success = false;
            errorMessage = throwable.getMessage();
            throw throwable;
        } finally {
            try {
                Object afterValue = success ? result : null;
                auditLogService.log(
                        resourceType,
                        resourceId,
                        operationType,
                        beforeValue,
                        afterValue,
                        success,
                        errorMessage,
                        tenant
                );
            } catch (Exception e) {
                log.error("Failed to write audit log", e);
            }
        }

        return result;
    }

    private Object extractBeforeValue(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (param.getType().getSimpleName().endsWith("Request")
                    || param.getType().getSimpleName().endsWith("DTO")) {
                return args[i];
            }
        }
        return null;
    }

    private String extractResourceId(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            if (param.getName() != null && (param.getName().equals("id") || param.getName().endsWith("Id"))
                    && (args[i] instanceof Long || args[i] instanceof String)) {
                return String.valueOf(args[i]);
            }
        }
        return null;
    }

    private Tenant extractTenant(Parameter[] parameters, Object[] args) {
        for (int i = 0; i < parameters.length; i++) {
            if (args[i] instanceof Tenant) {
                return (Tenant) args[i];
            }
            if (args[i] != null) {
                try {
                    Method getTenant = args[i].getClass().getMethod("getTenant");
                    Object tenant = getTenant.invoke(args[i]);
                    if (tenant instanceof Tenant) {
                        return (Tenant) tenant;
                    }
                } catch (Exception ignored) {
                }
                try {
                    Method getTenantId = args[i].getClass().getMethod("getTenantId");
                    Object tenantId = getTenantId.invoke(args[i]);
                    if (tenantId != null) {
                        Tenant t = new Tenant();
                        t.setId(((Number) tenantId).longValue());
                        return t;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
