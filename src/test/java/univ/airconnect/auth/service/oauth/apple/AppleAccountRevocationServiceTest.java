package univ.airconnect.auth.service.oauth.apple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.dto.request.DeleteAccountRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppleAccountRevocationServiceTest {

    @Mock
    private AppleAuthProperties appleAuthProperties;
    @Mock
    private AppleAuthProperties.Revoke revokeProperties;
    @Mock
    private AppleClientSecretService appleClientSecretService;
    @Mock
    private AppleTokenRevocationClient appleTokenRevocationClient;

    @Test
    void revokeOnAccountDeletion_skipsForNonAppleUser() {
        AppleAccountRevocationService service = createService();
        User kakaoUser = User.create(SocialProvider.KAKAO, "social-kakao", "kakao@test.dev");
        ReflectionTestUtils.setField(kakaoUser, "id", 1L);

        AppleAccountRevocationService.AppleRevocationResult result =
                service.revokeOnAccountDeletion(kakaoUser, new DeleteAccountRequest(), "trace-1");

        assertThat(result.attempted()).isFalse();
        assertThat(result.success()).isTrue();
        verify(appleClientSecretService, never()).createClientSecret();
    }

    @Test
    void revokeOnAccountDeletion_usesRefreshTokenWhenPresent() {
        AppleAccountRevocationService service = createService();
        User appleUser = User.create(SocialProvider.APPLE, "social-apple", "apple@test.dev");
        ReflectionTestUtils.setField(appleUser, "id", 2L);

        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setAppleRefreshToken("refresh-token-value");

        when(appleAuthProperties.getRevoke()).thenReturn(revokeProperties);
        when(revokeProperties.isEnabled()).thenReturn(true);
        when(appleClientSecretService.createClientSecret()).thenReturn("client-secret-jwt");

        AppleAccountRevocationService.AppleRevocationResult result =
                service.revokeOnAccountDeletion(appleUser, request, "trace-2");

        assertThat(result.attempted()).isTrue();
        assertThat(result.success()).isTrue();
        assertThat(result.source()).isEqualTo("APPLE_REFRESH_TOKEN");
        verify(appleTokenRevocationClient).revoke("refresh-token-value", "refresh_token", "client-secret-jwt");
    }

    @Test
    void revokeOnAccountDeletion_doesNotFailDeletionFlowWhenRevokeFails() {
        AppleAccountRevocationService service = createService();
        User appleUser = User.create(SocialProvider.APPLE, "social-apple", "apple@test.dev");
        ReflectionTestUtils.setField(appleUser, "id", 3L);

        DeleteAccountRequest request = new DeleteAccountRequest();
        request.setAppleAuthorizationCode("authorization-code");

        when(appleAuthProperties.getRevoke()).thenReturn(revokeProperties);
        when(revokeProperties.isEnabled()).thenReturn(true);
        when(appleClientSecretService.createClientSecret()).thenReturn("client-secret-jwt");
        org.mockito.Mockito.doThrow(new IllegalStateException("network-timeout"))
                .when(appleTokenRevocationClient).revoke("authorization-code", null, "client-secret-jwt");

        AppleAccountRevocationService.AppleRevocationResult result =
                service.revokeOnAccountDeletion(appleUser, request, "trace-3");

        assertThat(result.attempted()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(result.reason()).contains("network-timeout");
    }

    private AppleAccountRevocationService createService() {
        return new AppleAccountRevocationService(
                appleAuthProperties,
                appleClientSecretService,
                appleTokenRevocationClient
        );
    }
}
