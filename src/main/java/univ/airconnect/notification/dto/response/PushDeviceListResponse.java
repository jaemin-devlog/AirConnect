package univ.airconnect.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 활성 디바이스 목록 응답 모델이다.
 */
@Getter
@AllArgsConstructor
public class PushDeviceListResponse {

    private int count;

    private List<PushDeviceResponse> items;
}
