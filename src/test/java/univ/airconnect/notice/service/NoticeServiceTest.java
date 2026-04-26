package univ.airconnect.notice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.admin.AdminNotice;
import univ.airconnect.admin.AdminNoticeRepository;
import univ.airconnect.notice.dto.response.NoticeResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private AdminNoticeRepository adminNoticeRepository;

    @Test
    void getNotices_returnsAllSavedNoticesInRepositoryOrder() {
        NoticeService noticeService = new NoticeService(adminNoticeRepository);

        AdminNotice newest = notice(11L, "최신 공지", "본문 A", "airconnect://notice/11", false, 120, 1000L);
        AdminNotice older = notice(10L, "이전 공지", "본문 B", null, true, 80, 999L);
        when(adminNoticeRepository.findAllByOrderByCreatedAtDescIdDesc()).thenReturn(List.of(newest, older));

        List<NoticeResponse> response = noticeService.getNotices();

        assertThat(response).hasSize(2);
        assertThat(response.get(0).noticeId()).isEqualTo(11L);
        assertThat(response.get(0).title()).isEqualTo("최신 공지");
        assertThat(response.get(0).recipientCount()).isEqualTo(120);
        assertThat(response.get(1).noticeId()).isEqualTo(10L);
        assertThat(response.get(1).activeUsersOnly()).isTrue();
    }

    private AdminNotice notice(Long id,
                               String title,
                               String body,
                               String deeplink,
                               boolean activeUsersOnly,
                               int recipientCount,
                               Long createdByUserId) {
        AdminNotice notice = AdminNotice.create(createdByUserId, title, body, deeplink, activeUsersOnly, recipientCount);
        ReflectionTestUtils.setField(notice, "id", id);
        ReflectionTestUtils.setField(notice, "createdAt", LocalDateTime.now().minusMinutes(id));
        return notice;
    }
}
