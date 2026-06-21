package com.apigateway.common.utils;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@UtilityClass
public class ApiKeyGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String API_KEY_PREFIX = "ak_";
    private static final int API_KEY_LENGTH = 32;
    private static final int SECRET_LENGTH = 48;

    public static String generateKeyId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return API_KEY_PREFIX + encoded;
    }

    public static String generateSecret() {
        byte[] randomBytes = new byte[SECRET_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String generateToken() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }
}
