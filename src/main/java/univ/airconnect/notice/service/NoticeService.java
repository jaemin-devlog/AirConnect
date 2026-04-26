package univ.airconnect.notice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.admin.AdminNoticeRepository;
import univ.airconnect.notice.dto.response.NoticeResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final AdminNoticeRepository adminNoticeRepository;

    public List<NoticeResponse> getNotices() {
        return adminNoticeRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(NoticeResponse::from)
                .toList();
    }
}
