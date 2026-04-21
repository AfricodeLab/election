package com.brandedhustlers.electionbackend.voter.service;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.voter.dto.*;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

public interface IVerificationService {

    /** Send a 6-digit OTP to the voter after validating email + national ID. */
    OtpSendResponse sendOtp(OtpSendRequest req) throws MessagingException;

    /** Confirm the OTP (or station PIN) and issue a voterToken JWT. */
    OtpConfirmResponse confirmOtp(OtpConfirmRequest req, HttpServletResponse response);

    /**
     * Official-assisted in-person verification.
     * Marks the voter as identity-verified and returns a short-lived station PIN
     * that the voter uses at the terminal to obtain their voterToken.
     */
//    InPersonVerifyResponse verifyInPerson(UUID voterId, UUID electionId, UUID officialId);

    InPersonVerifyResponse verifyInPerson(UUID voterId, UUID electionId, AppUser officialId);
}
