package com.brandedhustlers.electionbackend.common.config.storage.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class RedisOtpService {
    private final StringRedisTemplate stringRedisTemplate;
    private static final int EXPIRE_TIME_MINUTES = 5;

    /**
     * Single key prefix used by ALL methods.
     *
     * Previously storeOTP() used "OTP: " + email while getOTP(), deleteOTP(),
     * and verifyOTP() used plain email — meaning any OTP stored via storeOTP()
     * could never be retrieved or verified, causing silent login failures.
     */
    private static final String KEY_PREFIX = "OTP:";

    private static final SecureRandom random = new SecureRandom();

    public RedisOtpService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private String key(String email) {
        return KEY_PREFIX + email;
    }

    public void storeOTP(String email, String otp) {
        stringRedisTemplate.opsForValue().set(key(email), otp, 15, TimeUnit.MINUTES);
    }

    public String getOTP(String email) {
        return stringRedisTemplate.opsForValue().get(key(email));
    }

    public void deleteOTP(String email) {
        if (email != null) {
            stringRedisTemplate.delete(key(email));
        }
    }

    public String generateAndStoreOTP(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String otp = generateOTP();
        stringRedisTemplate.opsForValue().set(key(email), otp, EXPIRE_TIME_MINUTES, TimeUnit.MINUTES);
        return otp;
    }

    public boolean verifyOTP(String email, String otp) {
        String storedOTP = stringRedisTemplate.opsForValue().get(key(email));
        if (storedOTP == null || !storedOTP.equals(otp)) {
            return false;
        }
        stringRedisTemplate.delete(key(email));
        return true;
    }

    private String generateOTP() {
        int otp = random.nextInt(100000, 999999);
        return String.valueOf(otp);
    }
}
