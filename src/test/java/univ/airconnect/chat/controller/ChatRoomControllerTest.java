package univ.airconnect.chat.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import univ.airconnect.chat.dto.response.ChatParticipantDetailResponse;
import univ.airconnect.chat.dto.response.ChatParticipantProfileResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.dto.response.UserProfileResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private HttpServletRequest request;

    @Test
    void getCounterpartProfile_returnsWrappedDetailedCounterpartProfile() {
        ChatRoomController controller = new ChatRoomController(chatService);
        Long roomId = 900L;
        Long currentUserId = 1L;
        String traceId = "trace-chat-counterpart";

        ChatParticipantDetailResponse profile = ChatParticipantDetailResponse.builder()
                .userId(2L)
                .nickname("target")
                .deptName("cs")
                .age(24)
                .gender(Gender.FEMALE)
                .profileImage("profiles/2.png")
                .profileExists(true)
                .profileImageUploaded(true)
                .profile(UserProfileResponse.builder()
                        .userId(2L)
                        .age(24)
                        .gender(Gender.FEMALE)
                        .mbti("INTJ")
                        .residence("Daegu")
                        .instagram("target_insta")
                        .profileImagePath("http://localhost:8080/api/v1/users/profile-images/profiles/2.png")
                        .build())
                .build();

        when(request.getAttribute("traceId")).thenReturn(traceId);
        when(chatService.getCounterpartProfile(roomId, currentUserId)).thenReturn(profile);

        ResponseEntity<ApiResponse<ChatParticipantDetailResponse>> response =
                controller.getCounterpartProfile(roomId, currentUserId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getTraceId()).isEqualTo(traceId);
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getUserId()).isEqualTo(2L);
        assertThat(response.getBody().getData().getProfile()).isNotNull();
        assertThat(response.getBody().getData().getProfile().getMbti()).isEqualTo("INTJ");

        verify(chatService).getCounterpartProfile(roomId, currentUserId);
    }

    @Test
    void getParticipantProfile_returnsWrappedParticipantProfile() {
        ChatRoomController controller = new ChatRoomController(chatService);
        Long roomId = 901L;
        Long currentUserId = 1L;
        Long targetUserId = 2L;
        String traceId = "trace-chat-profile";

        ChatParticipantProfileResponse profile = ChatParticipantProfileResponse.builder()
                .userId(targetUserId)
                .nickname("target")
                .deptName("cs")
                .age(24)
                .gender(Gender.FEMALE)
                .profileImage("profiles/2.png")
                .profileExists(true)
                .profileImageUploaded(true)
                .build();

        when(request.getAttribute("traceId")).thenReturn(traceId);
        when(chatService.getParticipantProfile(roomId, currentUserId, targetUserId)).thenReturn(profile);

        ResponseEntity<ApiResponse<ChatParticipantProfileResponse>> response =
                controller.getParticipantProfile(roomId, targetUserId, currentUserId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getTraceId()).isEqualTo(traceId);
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getUserId()).isEqualTo(targetUserId);
        assertThat(response.getBody().getData().getGender()).isEqualTo(Gender.FEMALE);
        assertThat(response.getBody().getData().getProfileImage()).isEqualTo("profiles/2.png");

        verify(chatService).getParticipantProfile(roomId, currentUserId, targetUserId);
    }

    @Test
    void getParticipantProfiles_returnsWrappedParticipantProfiles() {
        ChatRoomController controller = new ChatRoomController(chatService);
        Long roomId = 902L;
        Long currentUserId = 1L;
        String traceId = "trace-chat-participants";

        List<ChatParticipantDetailResponse> profiles = List.of(
                ChatParticipantDetailResponse.builder()
                        .userId(1L)
                        .nickname("me")
                        .deptName("cs")
                        .age(24)
                        .gender(Gender.MALE)
                        .profileImage("profiles/1.png")
                        .profileExists(true)
                        .profileImageUploaded(true)
                        .profile(UserProfileResponse.builder()
                                .userId(1L)
                                .age(24)
                                .gender(Gender.MALE)
                                .profileImagePath("http://localhost:8080/api/v1/users/profile-images/profiles/1.png")
                                .build())
                        .build(),
                ChatParticipantDetailResponse.builder()
                        .userId(2L)
                        .nickname("target")
                        .deptName("biz")
                        .age(23)
                        .gender(Gender.FEMALE)
                        .profileImage("profiles/2.png")
                        .profileExists(true)
                        .profileImageUploaded(true)
                        .profile(UserProfileResponse.builder()
                                .userId(2L)
                                .age(23)
                                .gender(Gender.FEMALE)
                                .mbti("INFJ")
                                .residence("Busan")
                                .instagram("target_insta")
                                .profileImagePath("http://localhost:8080/api/v1/users/profile-images/profiles/2.png")
                                .build())
                        .build()
        );

        when(request.getAttribute("traceId")).thenReturn(traceId);
        when(chatService.getParticipantProfiles(roomId, currentUserId)).thenReturn(profiles);

        ResponseEntity<ApiResponse<List<ChatParticipantDetailResponse>>> response =
                controller.getParticipantProfiles(roomId, currentUserId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getTraceId()).isEqualTo(traceId);
        assertThat(response.getBody().getData()).hasSize(2);
        assertThat(response.getBody().getData()).extracting(ChatParticipantDetailResponse::getUserId)
                .containsExactly(1L, 2L);
        assertThat(response.getBody().getData().get(1).getProfile()).isNotNull();
        assertThat(response.getBody().getData().get(1).getProfile().getMbti()).isEqualTo("INFJ");

        verify(chatService).getParticipantProfiles(roomId, currentUserId);
    }
}
