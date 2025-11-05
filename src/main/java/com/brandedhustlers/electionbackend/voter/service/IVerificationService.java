package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpConfirmResponse;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendRequest;
import com.brandedhustlers.electionbackend.voter.dto.OtpSendResponse;
import jakarta.servlet.http.HttpSession;

import java.util.UUID;

public interface IVerificationService {
    OtpSendResponse sendOtp(UUID orgId, OtpSendRequest req);
    OtpConfirmResponse confirmOtp(UUID orgId, OtpConfirmRequest req, HttpSession session);
}
