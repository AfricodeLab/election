package com.brandedhustlers.electionbackend.common.config.storage.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class RedisOtpService {
    private final StringRedisTemplate stringRedisTemplate;
    private static final int EXPIRE_TIME_MINUTES = 5;

    private static final SecureRandom random = new SecureRandom();

    public RedisOtpService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void storeOTP(String email, String otp) {
        stringRedisTemplate.opsForValue().set("OTP: " + email, otp, 15, TimeUnit.MINUTES);
    }

    public String getOTP(String email) {
        return stringRedisTemplate.opsForValue().get(email);
    }

    public void deleteOTP(String email) {
        if (email != null) {
            stringRedisTemplate.delete(email);
        }
    }

    public String generateAndStoreOTP(String email) {
        if (email == null || email.isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String otp = generateOTP();
        stringRedisTemplate.opsForValue().set(email, otp, EXPIRE_TIME_MINUTES, TimeUnit.MINUTES);
        return otp;
    }

    public boolean verifyOTP(String email, String otp) {
        String storedOTP = stringRedisTemplate.opsForValue().get(email);
        if(storedOTP == null || !storedOTP.equals(otp)) {
            return false;
        }
        stringRedisTemplate.delete(email);
        return true;
    }

    private String generateOTP() {
        int otp = random.nextInt(100000, 999999);
        return String.valueOf(otp);
    }
}
