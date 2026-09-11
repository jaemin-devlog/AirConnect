package univ.airconnect.statistics.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import univ.airconnect.global.response.ApiResponse;
import univ.airconnect.statistics.dto.response.MainStatisticsResponse;
import univ.airconnect.statistics.dto.response.DepartmentRankingResponse;
import univ.airconnect.statistics.dto.response.OnlinePresenceResponse;
import univ.airconnect.statistics.dto.response.RealtimeMainStatisticsResponse;
import univ.airconnect.statistics.service.StatisticsService;

import java.util.List;

import static univ.airconnect.global.web.TraceIdFilter.TRACE_ID_ATTRIBUTE;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/main")
    public ResponseEntity<ApiResponse<MainStatisticsResponse>> getMainStatistics(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        MainStatisticsResponse response = statisticsService.getMainStatistics();
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/online")
    public ResponseEntity<ApiResponse<OnlinePresenceResponse>> getOnlinePresence(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.getOnlinePresence(), traceId));
    }

    @GetMapping("/main/realtime")
    public ResponseEntity<ApiResponse<RealtimeMainStatisticsResponse>> getRealtimeMainStatistics(
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.getRealtimeMainStatistics(), traceId));
    }

    @GetMapping("/departments/rankings")
    public ResponseEntity<ApiResponse<List<DepartmentRankingResponse>>> getDepartmentRankings(
            HttpServletRequest request
    ) {
        String traceId = (String) request.getAttribute(TRACE_ID_ATTRIBUTE);
        return ResponseEntity.ok(ApiResponse.ok(statisticsService.getDepartmentRankings(), traceId));
    }
}
