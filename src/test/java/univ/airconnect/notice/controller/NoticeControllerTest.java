package univ.airconnect.notice.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.notice.dto.response.NoticeResponse;
import univ.airconnect.notice.service.NoticeService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeControllerTest {

    @Mock
    private NoticeService noticeService;

    @Mock
    private HttpServletRequest request;

    @Test
    void getNotices_returnsWrappedNoticeList() {
        NoticeController controller = new NoticeController(noticeService);
        String traceId = "trace-notices";
        List<NoticeResponse> notices = List.of(
                new NoticeResponse(
                        1L,
                        "서비스 점검",
                        "오늘 밤 11시에 점검이 진행됩니다.",
                        "airconnect://notice/1",
                        true,
                        42,
                        999L,
                        LocalDateTime.of(2026, 4, 26, 10, 0)
                )
        );

        when(request.getAttribute("traceId")).thenReturn(traceId);
        when(noticeService.getNotices()).thenReturn(notices);

        ResponseEntity<ApiResponse<List<NoticeResponse>>> response = controller.getNotices(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getTraceId()).isEqualTo(traceId);
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).title()).isEqualTo("서비스 점검");

        verify(noticeService).getNotices();
    }
}
