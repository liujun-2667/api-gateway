package com.apigateway.common.utils;

import com.apigateway.common.entity.TrafficColorRule;
import com.apigateway.common.enums.MatchType;
import com.apigateway.common.enums.TrafficConditionType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@UtilityClass
public class TrafficTagMatcher {

    public static String matchAndGetTag(HttpServletRequest request, List<TrafficColorRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (TrafficColorRule rule : rules) {
            if (matches(request, rule)) {
                log.debug("Traffic rule matched: ruleId={}, colorTag={}", rule.getRuleId(), rule.getColorTag());
                return rule.getColorTag();
            }
        }
        return null;
    }

    public static String matchAndGetTag(ServerHttpRequest request, List<TrafficColorRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }

        for (TrafficColorRule rule : rules) {
            if (matches(request, rule)) {
                log.debug("Traffic rule matched: ruleId={}, colorTag={}", rule.getRuleId(), rule.getColorTag());
                return rule.getColorTag();
            }
        }
        return null;
    }

    public static boolean matches(HttpServletRequest request, TrafficColorRule rule) {
        String actualValue = extractValue(request, rule.getConditionType(), rule.getConditionKey());
        return matchesValue(actualValue, rule.getConditionValue(), rule.getMatchType());
    }

    public static boolean matches(ServerHttpRequest request, TrafficColorRule rule) {
        String actualValue = extractValue(request, rule.getConditionType(), rule.getConditionKey());
        return matchesValue(actualValue, rule.getConditionValue(), rule.getMatchType());
    }

    private static String extractValue(HttpServletRequest request, TrafficConditionType conditionType, String conditionKey) {
        if (request == null) {
            return null;
        }
        return switch (conditionType) {
            case HEADER -> request.getHeader(conditionKey);
            case QUERY_PARAM -> request.getParameter(conditionKey);
            case COOKIE -> getCookieValue(request, conditionKey);
            case PATH_VARIABLE -> null;
            case CLIENT_IP -> IpUtils.getClientIp(request);
            case USER_AGENT -> request.getHeader("User-Agent");
            case TENANT_ID -> request.getHeader("X-Tenant-Id");
            case USER_ID -> request.getHeader("X-User-Id");
            case API_KEY -> request.getHeader("X-API-Key");
        };
    }

    private static String extractValue(ServerHttpRequest request, TrafficConditionType conditionType, String conditionKey) {
        if (request == null) {
            return null;
        }
        return switch (conditionType) {
            case HEADER -> {
                List<String> headers = request.getHeaders().get(conditionKey);
                yield headers != null && !headers.isEmpty() ? headers.get(0) : null;
            }
            case QUERY_PARAM -> {
                List<String> params = request.getQueryParams().get(conditionKey);
                yield params != null && !params.isEmpty() ? params.get(0) : null;
            }
            case COOKIE -> {
                org.springframework.http.HttpCookie cookie = request.getCookies().getFirst(conditionKey);
                yield cookie != null ? cookie.getValue() : null;
            }
            case PATH_VARIABLE -> null;
            case CLIENT_IP -> request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : null;
            case USER_AGENT -> {
                List<String> userAgents = request.getHeaders().get("User-Agent");
                yield userAgents != null && !userAgents.isEmpty() ? userAgents.get(0) : null;
            }
            case TENANT_ID -> {
                List<String> tenantIds = request.getHeaders().get("X-Tenant-Id");
                yield tenantIds != null && !tenantIds.isEmpty() ? tenantIds.get(0) : null;
            }
            case USER_ID -> {
                List<String> userIds = request.getHeaders().get("X-User-Id");
                yield userIds != null && !userIds.isEmpty() ? userIds.get(0) : null;
            }
            case API_KEY -> {
                List<String> apiKeys = request.getHeaders().get("X-API-Key");
                yield apiKeys != null && !apiKeys.isEmpty() ? apiKeys.get(0) : null;
            }
        };
    }

    private static String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(cookieName)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static boolean matchesValue(String actualValue, String expectedValue, MatchType matchType) {
        if (actualValue == null || expectedValue == null) {
            return false;
        }

        try {
            return switch (matchType) {
                case EXACT -> actualValue.equals(expectedValue);
                case PREFIX -> actualValue.startsWith(expectedValue);
                case CONTAINS -> actualValue.contains(expectedValue);
                case REGEX -> Pattern.matches(expectedValue, actualValue);
            };
        } catch (Exception e) {
            log.error("Error matching value: actual={}, expected={}, matchType={}", actualValue, expectedValue, matchType, e);
            return false;
        }
    }

    public static String applyTagOperation(String existingTag, String newTag, com.apigateway.common.enums.ColorTagOperation operation) {
        if (newTag == null || operation == null) {
            return existingTag;
        }

        return switch (operation) {
            case ADD -> existingTag == null ? newTag : existingTag + "," + newTag;
            case REMOVE -> {
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
            case MODIFY, OVERRIDE -> newTag;
        };
    }
}
