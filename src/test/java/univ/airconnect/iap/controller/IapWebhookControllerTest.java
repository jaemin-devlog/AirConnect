package univ.airconnect.iap.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import univ.airconnect.iap.application.IapWebhookService;
import univ.airconnect.iap.dto.response.IapWebhookAckResponse;
import univ.airconnect.iap.google.GooglePubSubPushAuthenticator;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IapWebhookControllerTest {

    @Test
    void googleWebhookRejectsRequestBeforePayloadProcessingWhenOidcIsInvalid() {
        IapWebhookService service = mock(IapWebhookService.class);
        GooglePubSubPushAuthenticator authenticator = mock(GooglePubSubPushAuthenticator.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IapWebhookController controller = new IapWebhookController(service, authenticator);
        when(authenticator.isAuthorized("Bearer invalid-dummy-token")).thenReturn(false);

        var response = controller.ingestGoogle(
                Map.of("message", Map.of("data", "DUMMY")),
                "Bearer invalid-dummy-token",
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(service);
    }

    @Test
    void googleWebhookProcessesPayloadOnlyAfterOidcAuthorization() {
        IapWebhookService service = mock(IapWebhookService.class);
        GooglePubSubPushAuthenticator authenticator = mock(GooglePubSubPushAuthenticator.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        IapWebhookController controller = new IapWebhookController(service, authenticator);
        Map<String, Object> payload = Map.of("message", Map.of("data", "DUMMY"));
        when(authenticator.isAuthorized("Bearer valid-dummy-token")).thenReturn(true);
        when(service.ingestGoogleNotification(payload)).thenReturn(new IapWebhookAckResponse(true));

        var response = controller.ingestGoogle(payload, "Bearer valid-dummy-token", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).ingestGoogleNotification(payload);
    }

    @Test
    void appleWebhookReturnsBadRequestForRejectedSignature() {
        IapWebhookService service = mock(IapWebhookService.class);
        IapWebhookController controller = new IapWebhookController(
                service, mock(GooglePubSubPushAuthenticator.class));
        HttpServletRequest request = mock(HttpServletRequest.class);
        Map<String, Object> payload = Map.of("unexpected", "payload");
        when(service.ingestAppleNotification(payload)).thenReturn(new IapWebhookAckResponse(false));

        var response = controller.ingestApple(payload, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
