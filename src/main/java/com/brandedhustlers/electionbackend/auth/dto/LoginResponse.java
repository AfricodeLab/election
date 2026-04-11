package com.brandedhustlers.electionbackend.auth.dto;

public record LoginResponse(
                boolean success,
                String accessToken,
                String refreshToken,
                String username,
                String userEmail,
                /** True when the account has MFA enabled. accessToken and refreshToken will be null. */
                boolean mfaRequired,
                /** Short-lived (5 min) token to be submitted with the OTP to /auth/mfa/verify. Null when mfaRequired=false. */
                String mfaToken
                ) {

    /** Convenience factory for a normal (non-MFA) login. */
    public static LoginResponse standard(boolean success, String accessToken, String refreshToken,
                                         String username, String userEmail) {
        return new LoginResponse(success, accessToken, refreshToken, username, userEmail, false, null);
    }

    /** Convenience factory for an MFA-required login. */
    public static LoginResponse mfaChallenge(String mfaToken) {
        return new LoginResponse(false, null, null, null, null, true, mfaToken);
    }
}
