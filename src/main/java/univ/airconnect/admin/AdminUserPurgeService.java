package univ.airconnect.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.auth.domain.entity.RefreshToken;
import univ.airconnect.auth.repository.RefreshTokenRepository;
import univ.airconnect.auth.repository.SocialLoginDeviceBindingRepository;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.notification.repository.NotificationOutboxRepository;
import univ.airconnect.notification.repository.NotificationPreferenceRepository;
import univ.airconnect.notification.repository.NotificationRepository;
import univ.airconnect.notification.repository.PushDeviceRepository;
import univ.airconnect.notification.repository.PushEventRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;
import univ.airconnect.user.repository.UserSchoolConsentRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserPurgeService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserSchoolConsentRepository userSchoolConsentRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final MatchingExposureRepository matchingExposureRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SocialLoginDeviceBindingRepository socialLoginDeviceBindingRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;
    private final PushEventRepository pushEventRepository;
    private final UserMilestoneRepository userMilestoneRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional
    public AdminDtos.UserPermanentDeleteResult permanentlyDeleteDeletedUser(Long adminUserId, Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (user.getStatus() != UserStatus.DELETED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "탈퇴 상태인 사용자만 영구 삭제할 수 있습니다.");
        }

        String provider = user.getProvider() != null ? user.getProvider().name() : null;
        String socialId = user.getSocialId();
        String status = user.getStatus().name();

        long deletedNotificationOutboxRows = notificationOutboxRepository.deleteByUserId(userId);
        long deletedPushEventRows = pushEventRepository.deleteByUserId(userId);
        long deletedNotificationRows = notificationRepository.deleteByUserId(userId);
        long deletedNotificationPreferenceRows = notificationPreferenceRepository.deleteByUserId(userId);
        long deletedPushDeviceRows = pushDeviceRepository.deleteByUserId(userId);
        long deletedSocialDeviceBindingRows = socialLoginDeviceBindingRepository.deleteByUserId(userId);
        long deletedRefreshTokenRows = deleteRefreshTokens(userId);
        long deletedUserMilestoneRows = userMilestoneRepository.deleteByUserId(userId);
        long deletedChatRoomMemberRows = chatRoomMemberRepository.deleteByUserId(userId);
        matchingConnectionRepository.deleteByUser1IdOrUser2Id(userId, userId);
        matchingExposureRepository.deleteByUserIdOrCandidateUserId(userId, userId);
        long deletedProfileRows = userProfileRepository.deleteByUserId(userId);
        long deletedSchoolConsentRows = userSchoolConsentRepository.deleteByUserId(userId);

        userRepository.delete(user);
        userRepository.flush();

        AdminDtos.UserPermanentDeleteResult result = new AdminDtos.UserPermanentDeleteResult(
                userId,
                provider,
                socialId,
                status,
                deletedProfileRows,
                deletedSchoolConsentRows,
                deletedChatRoomMemberRows,
                deletedRefreshTokenRows,
                deletedSocialDeviceBindingRows,
                deletedPushDeviceRows,
                deletedNotificationPreferenceRows,
                deletedNotificationRows,
                deletedNotificationOutboxRows,
                deletedPushEventRows,
                deletedUserMilestoneRows,
                true
        );

        adminAuditLogService.record(
                adminUserId,
                AdminAuditAction.USER_PERMANENTLY_DELETED,
                "USER",
                userId,
                "탈퇴 사용자 #" + userId + "를 영구 삭제했습니다.",
                "재가입 허용을 위한 관리자 영구 삭제",
                metadata(result)
        );
        return result;
    }

    private long deleteRefreshTokens(Long userId) {
        Iterable<RefreshToken> tokens = refreshTokenRepository.findByUserId(userId);
        List<String> tokenIds = new ArrayList<>();
        for (RefreshToken token : tokens) {
            if (token != null && token.getId() != null) {
                tokenIds.add(token.getId());
            }
        }
        if (!tokenIds.isEmpty()) {
            refreshTokenRepository.deleteAllById(tokenIds);
        }
        return tokenIds.size();
    }

    private Map<String, Object> metadata(AdminDtos.UserPermanentDeleteResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", result.provider());
        metadata.put("socialId", result.socialId());
        metadata.put("deletedProfileRows", result.deletedProfileRows());
        metadata.put("deletedSchoolConsentRows", result.deletedSchoolConsentRows());
        metadata.put("deletedChatRoomMemberRows", result.deletedChatRoomMemberRows());
        metadata.put("deletedRefreshTokenRows", result.deletedRefreshTokenRows());
        metadata.put("deletedSocialDeviceBindingRows", result.deletedSocialDeviceBindingRows());
        metadata.put("deletedPushDeviceRows", result.deletedPushDeviceRows());
        metadata.put("deletedNotificationPreferenceRows", result.deletedNotificationPreferenceRows());
        metadata.put("deletedNotificationRows", result.deletedNotificationRows());
        metadata.put("deletedNotificationOutboxRows", result.deletedNotificationOutboxRows());
        metadata.put("deletedPushEventRows", result.deletedPushEventRows());
        metadata.put("deletedUserMilestoneRows", result.deletedUserMilestoneRows());
        return metadata;
    }
}
