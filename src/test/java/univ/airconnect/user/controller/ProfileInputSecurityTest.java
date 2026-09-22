package univ.airconnect.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import univ.airconnect.global.error.GlobalExceptionHandler;
import univ.airconnect.global.security.resolver.CurrentUserId;
import univ.airconnect.user.dto.request.SignUpRequest;
import univ.airconnect.user.dto.request.UpdateProfileRequest;
import univ.airconnect.user.dto.response.SignUpResponse;
import univ.airconnect.user.dto.response.UserProfileResponse;
import univ.airconnect.user.service.UserProfileImageService;
import univ.airconnect.user.service.UserSchoolConsentService;
import univ.airconnect.user.service.UserService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileInputSecurityTest {
    private static ValidatorFactory validatorFactory;
    private final ObjectMapper mapper = new ObjectMapper();
    @Mock private UserService userService;
    @Mock private UserProfileImageService imageService;
    @Mock private UserSchoolConsentService consentService;
    private MockMvc mvc;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserController(userService, imageService, consentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new SpringValidatorAdapter(validatorFactory.getValidator()))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(CurrentUserId.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return 1L;
                    }
                }).build();
    }

    @ParameterizedTest
    @CsvSource({"name,100", "nickname,100", "deptName,100", "mbti,10", "smoking,20",
            "residence,100", "intro,500", "instagram,200"})
    void signUpRejectsStringsBeyondTheirDatabaseColumn(String field, int limit) throws Exception {
        mvc.perform(post("/api/v1/users/sign-up").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(field, "x".repeat(limit + 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));
        verifyNoInteractions(userService);
        var requestAtLimit = mapper.convertValue(Map.of(field, "x".repeat(limit)), SignUpRequest.class);
        assertThat(validatorFactory.getValidator().validate(requestAtLimit)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"mbti,10", "smoking,20", "residence,100", "intro,500", "instagram,200"})
    void profileUpdateRejectsStringsBeyondTheirDatabaseColumn(String field, int limit) throws Exception {
        mvc.perform(patch("/api/v1/users/profile").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(field, "x".repeat(limit + 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));
        verifyNoInteractions(userService);
        var requestAtLimit = mapper.convertValue(Map.of(field, "x".repeat(limit)), UpdateProfileRequest.class);
        assertThat(validatorFactory.getValidator().validate(requestAtLimit)).isEmpty();
    }

    @Test
    void existingOnboardingShapeAndOptionalValuesStillWork() throws Exception {
        when(userService.signUp(eq(1L), any())).thenReturn(
                new SignUpResponse(1L, null, "테스트", "ACTIVE", "COMPLETED", true));
        mvc.perform(post("/api/v1/users/sign-up").contentType(MediaType.APPLICATION_JSON).content("""
                        {"accessToken":"dummy", "refreshToken":"dummy", "deviceId":"dummy-device",
                         "name":"테스트", "nickname":"여덟글자테스트용", "studentNum":21,
                         "deptName":"항공소프트웨어공학과", "height":175, "age":23,
                         "mbti":"ISTJ", "smoking":"비흡연", "gender":null, "military":null,
                         "residence":"서울", "intro":"안녕하세요", "instagram":""}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileExists").value(true));
    }

    @Test
    void partialUpdateAndExistingNumericValuesAreNotNewlyRestricted() throws Exception {
        when(userService.updateProfile(eq(1L), any())).thenReturn(
                UserProfileResponse.builder().userId(1L).age(23).build());
        mvc.perform(patch("/api/v1/users/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age\":23,\"intro\":\"\",\"instagram\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.age").value(23));
        assertThat(validatorFactory.getValidator().validate(
                mapper.readValue("{\"age\":-1,\"height\":9999}", UpdateProfileRequest.class))).isEmpty();
    }
}
