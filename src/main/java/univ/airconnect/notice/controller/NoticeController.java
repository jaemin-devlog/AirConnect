package univ.airconnect.notice.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.notice.dto.response.NoticeResponse;
import univ.airconnect.notice.service.NoticeService;

import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoticeResponse>>> getNotices(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(noticeService.getNotices(), traceId));
    }
}
