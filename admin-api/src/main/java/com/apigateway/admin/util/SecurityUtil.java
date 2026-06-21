package com.apigateway.admin.util;

import com.apigateway.admin.entity.AdminUser;
import com.apigateway.admin.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final CustomUserDetailsService userDetailsService;

    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return null;
    }

    public AdminUser getCurrentUser() {
        String username = getCurrentUsername();
        if (username != null) {
            try {
                return userDetailsService.loadAdminUserByUsername(username);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
