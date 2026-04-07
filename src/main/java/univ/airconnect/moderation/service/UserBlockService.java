package univ.airconnect.moderation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.moderation.domain.entity.UserBlock;
import univ.airconnect.moderation.dto.response.UserBlockCreateResponse;
import univ.airconnect.moderation.dto.response.UserBlockDeleteResponse;
import univ.airconnect.moderation.dto.response.UserBlockStatusResponse;
import univ.airconnect.moderation.exception.ModerationErrorCode;
import univ.airconnect.moderation.exception.ModerationException;
import univ.airconnect.moderation.repository.UserBlockRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    private static final String HIDDEN_REASON_BLOCKED_USER = "BLOCKED_USER";

    @Transactional
    public UserBlockCreateResponse block(Long blockerUserId, Long blockedUserId) {
        validateBlockUsers(blockerUserId, blockedUserId);

        UserBlock existing = userBlockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
                .orElse(null);
        if (existing != null) {
            hidePersonalRoomsForBlocker(blockerUserId, blockedUserId);
            return UserBlockCreateResponse.alreadyExists(existing);
        }

        try {
            UserBlock created = userBlockRepository.save(UserBlock.create(blockerUserId, blockedUserId));
            hidePersonalRoomsForBlocker(blockerUserId, blockedUserId);
            return UserBlockCreateResponse.created(created);
        } catch (DataIntegrityViolationException e) {
            UserBlock recovered = userBlockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
                    .orElseThrow(() -> e);
            hidePersonalRoomsForBlocker(blockerUserId, blockedUserId);
            return UserBlockCreateResponse.alreadyExists(recovered);
        }
    }

    @Transactional
    public UserBlockDeleteResponse unblock(Long blockerUserId, Long blockedUserId) {
        if (blockerUserId == null || blockedUserId == null) {
            throw new ModerationException(ModerationErrorCode.BLOCK_TARGET_NOT_FOUND);
        }

        UserBlock existing = userBlockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
                .orElse(null);
        if (existing == null) {
            return new UserBlockDeleteResponse(blockerUserId, blockedUserId, false);
        }

        userBlockRepository.delete(existing);
        unhidePersonalRoomsForBlocker(blockerUserId, blockedUserId);
        return new UserBlockDeleteResponse(blockerUserId, blockedUserId, true);
    }

    public List<UserBlockStatusResponse> getBlockedUsers(Long blockerUserId) {
        ensureBlockerExists(blockerUserId);
        return userBlockRepository.findByBlockerUserIdOrderByCreatedAtDesc(blockerUserId).stream()
                .map(block -> new UserBlockStatusResponse(
                        block.getBlockerUserId(),
                        block.getBlockedUserId(),
                        true,
                        block.getCreatedAt().atOffset(ZoneOffset.UTC)
                ))
                .toList();
    }

    public UserBlockStatusResponse getBlockStatus(Long blockerUserId, Long blockedUserId) {
        ensureBlockerExists(blockerUserId);
        return userBlockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)
                .map(block -> new UserBlockStatusResponse(
                        block.getBlockerUserId(),
                        block.getBlockedUserId(),
                        true,
                        block.getCreatedAt().atOffset(ZoneOffset.UTC)
                ))
                .orElseGet(() -> new UserBlockStatusResponse(blockerUserId, blockedUserId, false, null));
    }

    private void validateBlockUsers(Long blockerUserId, Long blockedUserId) {
        if (blockerUserId == null) {
            throw new ModerationException(ModerationErrorCode.BLOCKER_NOT_FOUND);
        }
        if (blockedUserId == null) {
            throw new ModerationException(ModerationErrorCode.BLOCK_TARGET_NOT_FOUND);
        }
        if (blockerUserId.equals(blockedUserId)) {
            throw new ModerationException(ModerationErrorCode.BLOCK_SELF_NOT_ALLOWED);
        }

        ensureBlockerExists(blockerUserId);
        ensureBlockTargetExists(blockedUserId);
    }

    private void ensureBlockerExists(Long blockerUserId) {
        User blocker = userRepository.findById(blockerUserId)
                .orElseThrow(() -> new ModerationException(ModerationErrorCode.BLOCKER_NOT_FOUND));
        if (blocker.getStatus() == UserStatus.DELETED) {
            throw new ModerationException(ModerationErrorCode.BLOCKER_NOT_FOUND);
        }
    }

    private void ensureBlockTargetExists(Long blockedUserId) {
        User blocked = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new ModerationException(ModerationErrorCode.BLOCK_TARGET_NOT_FOUND));
        if (blocked.getStatus() == UserStatus.DELETED) {
            throw new ModerationException(ModerationErrorCode.BLOCK_TARGET_NOT_FOUND);
        }
    }

    private void hidePersonalRoomsForBlocker(Long blockerUserId, Long blockedUserId) {
        List<Long> personalRoomIds = chatRoomMemberRepository.findCommonPersonalRoomIds(blockerUserId, blockedUserId);
        for (Long roomId : personalRoomIds) {
            chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, blockerUserId)
                    .ifPresent(member -> member.hide(HIDDEN_REASON_BLOCKED_USER));
        }
    }

    private void unhidePersonalRoomsForBlocker(Long blockerUserId, Long blockedUserId) {
        List<Long> personalRoomIds = chatRoomMemberRepository.findCommonPersonalRoomIds(blockerUserId, blockedUserId);
        for (Long roomId : personalRoomIds) {
            chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, blockerUserId)
                    .ifPresent(member -> {
                        if (member.isHidden()) {
                            member.unhide();
                        }
                    });
        }
    }
}
