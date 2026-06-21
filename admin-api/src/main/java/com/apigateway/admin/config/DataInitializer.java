package com.apigateway.admin.config;

import com.apigateway.admin.entity.AdminUser;
import com.apigateway.admin.enums.AdminRole;
import com.apigateway.admin.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!adminUserRepository.existsByUsername("admin")) {
            AdminUser admin = AdminUser.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@apigateway.com")
                    .role(AdminRole.ADMIN)
                    .tenantId(null)
                    .enabled(true)
                    .build();
            adminUserRepository.save(admin);
            log.info("Default admin user created: admin/admin123");
        }
    }
}
