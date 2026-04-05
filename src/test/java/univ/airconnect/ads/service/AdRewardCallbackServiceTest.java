package univ.airconnect.ads.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.ads.domain.entity.AdRewardSession;
import univ.airconnect.ads.dto.response.AdRewardCallbackResponse;
import univ.airconnect.ads.exception.AdsErrorCode;
import univ.airconnect.ads.exception.AdsException;
import univ.airconnect.ads.infrastructure.AdmobSignatureVerifier;
import univ.airconnect.ads.repository.AdRewardCallbackRepository;
import univ.airconnect.ads.repository.AdRewardSessionRepository;
import univ.airconnect.ticket.service.AdTicketGrantService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdRewardCallbackServiceTest {

    @Mock
    private AdmobSignatureVerifier admobSignatureVerifier;
    @Mock
    private AdRewardSessionRepository adRewardSessionRepository;
    @Mock
    private AdRewardCallbackRepository adRewardCallbackRepository;
    @Mock
    private AdTicketGrantService adTicketGrantService;

    @InjectMocks
    private AdRewardCallbackService adRewardCallbackService;

    @Test
    void handleCallback_grants_whenValid() {
        AdRewardSession session = AdRewardSession.createReady("sess-1", 3L, 1, LocalDateTime.now().plusMinutes(5));
        ReflectionTestUtils.setField(session, "id", 11L);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("custom_data=sess-1&transaction_id=tx-1&signature=s&key_id=k");
        request.addParameter("custom_data", "sess-1");
        request.addParameter("transaction_id", "tx-1");
        request.addParameter("signature", "s");
        request.addParameter("key_id", "k");

        when(admobSignatureVerifier.verify(any())).thenReturn(true);
        when(adRewardCallbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(adRewardSessionRepository.findBySessionKeyForUpdate("sess-1")).thenReturn(Optional.of(session));
        when(adRewardSessionRepository.existsByTransactionId("tx-1")).thenReturn(false);
        when(adTicketGrantService.grantFromAdReward(3L, 1, 11L))
                .thenReturn(AdTicketGrantService.GrantResult.granted(100, 101, "TICKET_LEDGER_1"));

        AdRewardCallbackResponse response = adRewardCallbackService.handleAdmobCallback(request);

        assertThat(response.getGrantStatus()).isEqualTo("GRANTED");
        assertThat(response.getGrantedTickets()).isEqualTo(1);
        assertThat(response.getAfterTickets()).isEqualTo(101);
    }

    @Test
    void handleCallback_ignores_whenSignatureMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("custom_data=sess-1&transaction_id=tx-1");
        request.addParameter("custom_data", "sess-1");
        request.addParameter("transaction_id", "tx-1");

        AdRewardCallbackResponse response = adRewardCallbackService.handleAdmobCallback(request);

        assertThat(response.getGrantStatus()).isEqualTo("IGNORED");
        verify(adRewardCallbackRepository, never()).save(any());
    }

    @Test
    void handleCallback_fails_whenSignatureInvalid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("custom_data=sess-1&transaction_id=tx-1&signature=s&key_id=k");
        request.addParameter("custom_data", "sess-1");
        request.addParameter("transaction_id", "tx-1");
        request.addParameter("signature", "s");
        request.addParameter("key_id", "k");

        when(admobSignatureVerifier.verify(any())).thenReturn(false);
        when(adRewardCallbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> adRewardCallbackService.handleAdmobCallback(request))
                .isInstanceOf(AdsException.class)
                .extracting(ex -> ((AdsException) ex).getErrorCode())
                .isEqualTo(AdsErrorCode.AD_REWARD_INVALID_SIGNATURE);
    }

    @Test
    void handleCallback_ignores_whenProbeRequestHasNoParams() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AdRewardCallbackResponse response = adRewardCallbackService.handleAdmobCallback(request);

        assertThat(response.getGrantStatus()).isEqualTo("IGNORED");
        assertThat(response.getGrantedTickets()).isEqualTo(0);
        verify(adRewardCallbackRepository, never()).save(any());
    }

    @Test
    void handleCallback_ignores_whenProbeRequestHasOnlySignatureLikeParams() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setQueryString("signature=dummy&key_id=1");
        request.addParameter("signature", "dummy");
        request.addParameter("key_id", "1");

        AdRewardCallbackResponse response = adRewardCallbackService.handleAdmobCallback(request);

        assertThat(response.getGrantStatus()).isEqualTo("IGNORED");
        assertThat(response.getGrantedTickets()).isEqualTo(0);
        verify(adRewardCallbackRepository, never()).save(any());
    }
}


