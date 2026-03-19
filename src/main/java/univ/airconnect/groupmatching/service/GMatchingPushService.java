package univ.airconnect.groupmatching.service;

import java.util.Collection;

public interface GMatchingPushService {
    void notifyMatched(Collection<Long> userIds, Long finalGroupRoomId, Long finalChatRoomId);
}
