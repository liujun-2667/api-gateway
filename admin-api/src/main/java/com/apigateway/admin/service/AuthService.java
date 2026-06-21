package com.apigateway.admin.service;

import com.apigateway.admin.dto.AuthDTO;
import com.apigateway.admin.entity.AdminUser;
import com.apigateway.admin.enums.AdminRole;
import com.apigateway.admin.repository.AdminUserRepository;
import com.apigateway.admin.security.JwtTokenProvider;
import com.apigateway.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Transactional(readOnly = true)
    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = jwtTokenProvider.generateToken(authentication);

        AdminUser user = adminUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("User not found"));

        return AuthDTO.LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration / 1000)
                .user(AuthDTO.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .tenantId(user.getTenantId())
                        .enabled(user.getEnabled())
                        .build())
                .build();
    }

    @Transactional
    public AuthDTO.UserInfo register(AuthDTO.RegisterRequest request) {
        if (adminUserRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Username already exists");
        }

        AdminRole role = request.getRole() != null ? request.getRole() : AdminRole.TENANT_ADMIN;

        AdminUser user = AdminUser.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(role)
                .tenantId(request.getTenantId())
                .enabled(true)
                .build();

        user = adminUserRepository.save(user);

        return AuthDTO.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .enabled(user.getEnabled())
                .build();
    }
}
