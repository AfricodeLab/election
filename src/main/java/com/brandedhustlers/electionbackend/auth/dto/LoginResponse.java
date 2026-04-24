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
                String mfaToken,
                /**
                 * True when the account was provisioned by an admin and the user must set
                 * a new password before continuing. The frontend should redirect to the
                 * change-password page when this is {@code true}.
                 */
                boolean mustChangePassword
                ) {

    /** Convenience factory for a normal (non-MFA) login. */
    public static LoginResponse standard(boolean success, String accessToken, String refreshToken,
                                         String username, String userEmail) {
        return new LoginResponse(success, accessToken, refreshToken, username, userEmail, false, null, false);
    }

    /** Convenience factory for a normal login where the password must be changed. */
    public static LoginResponse mustChangePassword(boolean success, String accessToken, String refreshToken,
                                                   String username, String userEmail) {
        return new LoginResponse(success, accessToken, refreshToken, username, userEmail, false, null, true);
    }

    /** Convenience factory for an MFA-required login. */
    public static LoginResponse mfaChallenge(String mfaToken) {
        return new LoginResponse(false, null, null, null, null, true, mfaToken, false);
    }
}
