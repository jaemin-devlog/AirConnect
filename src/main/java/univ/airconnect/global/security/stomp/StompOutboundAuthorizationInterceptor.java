package univ.airconnect.global.security.stomp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import univ.airconnect.chat.repository.ChatRoomMemberRepository;
import univ.airconnect.groupmatching.service.GMatchingService;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.repository.UserRepository;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple broker가 개별 WebSocket 세션으로 보내기 직전에 현재 권한을 재검증한다.
 * 구독 이후 leave/expel/logout/delete가 발생해도 이전 구독으로 보호 이벤트가
 * 전달되지 않게 하는 최종 보안 경계다.
 */
@Slf4j
@Component
public class StompOutboundAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_ROOM = Pattern.compile("^/sub/chat/room/([1-9]\\d*)$");
    private static final Pattern CHAT_LIST = Pattern.compile("^/sub/chat/list/([1-9]\\d*)$");
    private static final Pattern MATCHING_TEAM_ROOM = Pattern.compile("^/sub/matching/team-room/([1-9]\\d*)$");
    private static final String ONLINE_USERS = StompSessionRegistry.ONLINE_USERS_DESTINATION;
    private static final String MAIN_STATISTICS = StompSessionRegistry.MAIN_STATISTICS_DESTINATION;
    private static final String DEPARTMENT_RANKINGS = StompSessionRegistry.DEPARTMENT_RANKINGS_DESTINATION;

    private final StompSessionRegistry sessionRegistry;
    private final UserRepository userRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final GMatchingService matchingService;

    public StompOutboundAuthorizationInterceptor(
            StompSessionRegistry sessionRegistry,
            UserRepository userRepository,
            ChatRoomMemberRepository chatRoomMemberRepository,
            @Lazy GMatchingService matchingService
    ) {
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.matchingService = matchingService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, SimpMessageHeaderAccessor.class);
        if (accessor == null || accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/sub")) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        Long userId = sessionRegistry.findUserId(sessionId).orElse(null);
        if (userId == null) {
            return deny(accessor, null, "inactive_or_revoked_session");
        }
        // 전 사용자 브로드캐스트마다 DB를 조회하면 접속자 수 변동 1회가
        // 구독자 수만큼의 쿼리를 만들므로, 로컬에서 폐기되지 않은 인증 세션만 확인한다.
        if (ONLINE_USERS.equals(destination)
                || MAIN_STATISTICS.equals(destination)
                || DEPARTMENT_RANKINGS.equals(destination)) {
            return message;
        }
        if (!isActiveUser(userId)) {
            return deny(accessor, userId, "inactive_or_revoked_session");
        }

        Matcher chatRoom = CHAT_ROOM.matcher(destination);
        if (chatRoom.matches()) {
            Long roomId = Long.valueOf(chatRoom.group(1));
            if (chatRoomMemberRepository.existsByChatRoomIdAndUserIdAndHiddenAtIsNull(roomId, userId)) {
                return message;
            }
            return deny(accessor, userId, "chat_room_access_revoked");
        }

        Matcher chatList = CHAT_LIST.matcher(destination);
        if (chatList.matches()) {
            return userId.equals(Long.valueOf(chatList.group(1)))
                    ? message
                    : deny(accessor, userId, "chat_list_forbidden");
        }

        Matcher matchingRoom = MATCHING_TEAM_ROOM.matcher(destination);
        if (matchingRoom.matches()) {
            Long teamRoomId = Long.valueOf(matchingRoom.group(1));
            return matchingService.canSubscribeTeamRoom(teamRoomId, userId)
                    ? message
                    : deny(accessor, userId, "matching_room_access_revoked");
        }

        return deny(accessor, userId, "unsupported_destination");
    }

    private boolean isActiveUser(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
    }

    private Message<?> deny(SimpMessageHeaderAccessor accessor, Long userId, String reason) {
        log.warn("STOMP OUTBOUND DROPPED: sessionId={}, userId={}, destination={}, reason={}",
                accessor.getSessionId(), userId, accessor.getDestination(), reason);
        return null;
    }
}
