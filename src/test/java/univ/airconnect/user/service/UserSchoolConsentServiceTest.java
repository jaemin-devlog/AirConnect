package univ.airconnect.user.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserSchoolConsent;
import univ.airconnect.user.dto.request.SchoolConsentUpsertRequest;
import univ.airconnect.user.dto.response.SchoolConsentResponse;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.repository.UserSchoolConsentRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSchoolConsentServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSchoolConsentRepository userSchoolConsentRepository;

    @Test
    void upsert_success_whenAllRequiredAgreed() {
        UserSchoolConsentService service = new UserSchoolConsentService(userRepository, userSchoolConsentRepository);
        Long userId = 1L;

        User user = User.create(SocialProvider.APPLE, "apple-social-1", "u1@test.dev");
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userSchoolConsentRepository.findById(userId)).thenReturn(Optional.empty());
        when(userSchoolConsentRepository.save(any(UserSchoolConsent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchoolConsentUpsertRequest request = new SchoolConsentUpsertRequest();
        ReflectionTestUtils.setField(request, "adultAndCollegeConfirmed", true);
        ReflectionTestUtils.setField(request, "termsOfServiceAgreed", true);
        ReflectionTestUtils.setField(request, "privacyCollectionAgreed", true);
        ReflectionTestUtils.setField(request, "profileDisclosureAgreed", true);
        ReflectionTestUtils.setField(request, "marketingAgreed", false);

        SchoolConsentResponse response = service.upsert(userId, request);

        assertThat(response.getCanProceedSchoolVerification()).isTrue();
        assertThat(response.getRequiredConsentsAgreed()).isTrue();
        assertThat(response.getAllConsentsAgreed()).isFalse();
    }

    @Test
    void upsert_fail_whenRequiredConsentMissing() {
        UserSchoolConsentService service = new UserSchoolConsentService(userRepository, userSchoolConsentRepository);
        Long userId = 2L;

        User user = User.create(SocialProvider.APPLE, "apple-social-2", "u2@test.dev");
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        SchoolConsentUpsertRequest request = new SchoolConsentUpsertRequest();
        ReflectionTestUtils.setField(request, "adultAndCollegeConfirmed", true);
        ReflectionTestUtils.setField(request, "termsOfServiceAgreed", true);
        ReflectionTestUtils.setField(request, "privacyCollectionAgreed", false);
        ReflectionTestUtils.setField(request, "profileDisclosureAgreed", true);
        ReflectionTestUtils.setField(request, "marketingAgreed", false);

        assertThatThrownBy(() -> service.upsert(userId, request))
                .isInstanceOf(UserException.class)
                .extracting(ex -> ((UserException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.REQUIRED_CONSENT_NOT_ACCEPTED);
    }
}

