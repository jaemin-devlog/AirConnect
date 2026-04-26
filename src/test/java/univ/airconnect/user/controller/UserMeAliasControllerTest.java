package univ.airconnect.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.user.domain.UserRole;
import univ.airconnect.user.dto.response.UserMeResponse;
import univ.airconnect.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMeAliasControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @Test
    void getMe_returnsWrappedResponseForAliasPath() {
        UserMeAliasController controller = new UserMeAliasController(userService);
        UserMeResponse body = UserMeResponse.builder()
                .userId(1L)
                .role(UserRole.ADMIN)
                .tickets(7)
                .appAccountToken("token-1")
                .iosAppAccountToken("token-1")
                .build();

        when(request.getAttribute("traceId")).thenReturn("trace-user-me-alias");
        when(userService.getMe(1L)).thenReturn(body);

        ResponseEntity<ApiResponse<UserMeResponse>> response = controller.getMe(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getBody().getData().getAppAccountToken()).isEqualTo("token-1");
        verify(userService).getMe(1L);
    }
}
