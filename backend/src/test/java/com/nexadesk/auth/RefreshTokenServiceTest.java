package com.nexadesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.support.TestSecurityProperties;
import com.nexadesk.support.TestUsers;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final String RAW = "raw-refresh-token";

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private User user;
    private final UUID family = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, TestSecurityProperties.create(), Clock.fixed(NOW, ZoneOffset.UTC));
        user = TestUsers.user(7L, "tech@nexadesk.test", RoleCode.TECHNICIAN, "hash");
    }

    private RefreshToken stored(Instant expiresAt) {
        return new RefreshToken(user, RefreshTokenService.hash(RAW), family, NOW.minusSeconds(3600), expiresAt);
    }

    @Test
    void issuedTokenIsStoredHashedNeverInClear() {
        RefreshTokenService.IssuedRefreshToken issued = service.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshTokenService.hash(issued.rawValue()))
                .isNotEqualTo(issued.rawValue());
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(TestSecurityProperties.REFRESH_TTL));
    }

    @Test
    void rotationRevokesCurrentTokenAndIssuesOneInTheSameFamily() {
        RefreshToken current = stored(NOW.plusSeconds(3600));
        when(repository.findByTokenHash(RefreshTokenService.hash(RAW))).thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = service.rotate(RAW);

        assertThat(current.getRevokedAt()).isEqualTo(NOW);
        assertThat(rotation.user()).isSameAs(user);
        assertThat(rotation.token().rawValue()).isNotEqualTo(RAW);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(family);
    }

    @Test
    void reusingARotatedTokenAfterGracePeriodRevokesTheWholeFamily() {
        RefreshToken current = stored(NOW.plusSeconds(3600));
        current.revoke(NOW.minus(TestSecurityProperties.REUSE_GRACE).minusSeconds(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate(RAW))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(repository).revokeFamily(family, NOW);
    }

    @Test
    void reusingARotatedTokenWithinGracePeriodIsRejectedWithoutRevokingTheFamily() {
        RefreshToken current = stored(NOW.plusSeconds(3600));
        current.revoke(NOW.minusSeconds(2));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(BusinessException.class);
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void expiredTokenIsRejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored(NOW)));

        assertThatThrownBy(() -> service.rotate(RAW))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(repository, never()).save(any());
    }

    @Test
    void tokenOfDeactivatedUserIsRejectedAndFamilyRevoked() {
        user.setActive(false);
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored(NOW.plusSeconds(3600))));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(BusinessException.class);
        verify(repository).revokeFamily(family, NOW);
    }

    @Test
    void unknownOrMissingTokenIsRejected() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("unknown")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.rotate("  ")).isInstanceOf(BusinessException.class);
    }
}
