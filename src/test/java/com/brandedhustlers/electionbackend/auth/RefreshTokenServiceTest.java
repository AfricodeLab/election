package com.brandedhustlers.electionbackend.auth;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.domain.RefreshToken;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.auth.repository.AppUserRepository;
import com.brandedhustlers.electionbackend.auth.repository.RefreshTokenRepository;
import com.brandedhustlers.electionbackend.auth.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AppUserRepository appUserRepository;

    private RefreshTokenService service;
    private UUID userId;
    private AppUser testUser;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepository, appUserRepository);
        userId = UUID.randomUUID();
        testUser = AppUser.builder()
                .appUserId(userId)
                .appUserEmail("user@test.com")
                .build();
    }

    @Nested
    @DisplayName("createRefreshToken")
    class CreateRefreshToken {

        @Test
        @DisplayName("Deletes old tokens before creating a new one")
        void deletesOldTokensFirst() {
            when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createRefreshToken(userId);

            verify(refreshTokenRepository).deleteByUser(testUser);
        }

        @Test
        @DisplayName("Returns token with 7-day expiry and non-null token string")
        void returnsTokenWithCorrectExpiry() {
            when(appUserRepository.findById(userId)).thenReturn(Optional.of(testUser));
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            when(refreshTokenRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            RefreshToken result = service.createRefreshToken(userId);

            assertNotNull(result.getToken());
            // Expiry should be roughly 7 days from now (within a 10s tolerance)
            Instant expectedExpiry = Instant.now().plusSeconds(7 * 24 * 60 * 60);
            assertTrue(result.getExpiryDate().isAfter(expectedExpiry.minusSeconds(10)));
            assertTrue(result.getExpiryDate().isBefore(expectedExpiry.plusSeconds(10)));
        }

        @Test
        @DisplayName("Throws UserNotFound when user does not exist")
        void throwsWhenUserNotFound() {
            when(appUserRepository.findById(userId)).thenReturn(Optional.empty());

            assertThrows(UserNotFound.class, () -> service.createRefreshToken(userId));
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("verifyExpiration")
    class VerifyExpiration {

        @Test
        @DisplayName("Returns token when it is still valid")
        void returnsTokenWhenValid() {
            RefreshToken token = RefreshToken.builder()
                    .token(UUID.randomUUID().toString())
                    .expiryDate(Instant.now().plusSeconds(3600))
                    .user(testUser)
                    .build();

            RefreshToken result = service.verifyExpiration(token);

            assertSame(token, result);
            verify(refreshTokenRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Deletes token and throws RuntimeException when expired")
        void deletesAndThrowsWhenExpired() {
            RefreshToken token = RefreshToken.builder()
                    .token(UUID.randomUUID().toString())
                    .expiryDate(Instant.now().minusSeconds(3600))
                    .user(testUser)
                    .build();

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.verifyExpiration(token));

            verify(refreshTokenRepository).delete(token);
            assertTrue(ex.getMessage().contains("expired"));
        }
    }

    @Nested
    @DisplayName("findByToken")
    class FindByToken {

        @Test
        @DisplayName("Delegates to repository and returns present Optional")
        void delegatesToRepository() {
            RefreshToken token = RefreshToken.builder().token("some-token").build();
            when(refreshTokenRepository.findByToken("some-token")).thenReturn(Optional.of(token));

            Optional<RefreshToken> result = service.findByToken("some-token");

            assertTrue(result.isPresent());
            assertSame(token, result.get());
        }

        @Test
        @DisplayName("Returns empty Optional when token not found")
        void returnsEmptyWhenNotFound() {
            when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

            assertTrue(service.findByToken("missing").isEmpty());
        }
    }
}
