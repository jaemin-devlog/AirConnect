package univ.airconnect.matching.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.chat.domain.ChatRoomType;
import univ.airconnect.chat.dto.response.ChatRoomResponse;
import univ.airconnect.chat.service.ChatService;
import univ.airconnect.matching.domain.entity.MatchingConnection;
import univ.airconnect.matching.domain.entity.MatchingExposure;
import univ.airconnect.matching.domain.entity.MatchingQueueEntry;
import univ.airconnect.matching.dto.response.MatchingCandidateResponse;
import univ.airconnect.matching.dto.response.MatchingConnectResponse;
import univ.airconnect.matching.dto.response.MatchingRecommendationResponse;
import univ.airconnect.matching.exception.MatchingErrorCode;
import univ.airconnect.matching.exception.MatchingException;
import univ.airconnect.matching.repository.MatchingConnectionRepository;
import univ.airconnect.matching.repository.MatchingExposureRepository;
import univ.airconnect.matching.repository.MatchingQueueEntryRepository;
import univ.airconnect.user.domain.Gender;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private static final int RECOMMEND_LIMIT = 2;
    private static final int LOOKUP_BATCH_SIZE = 20;

    private final MatchingQueueEntryRepository matchingQueueEntryRepository;
    private final MatchingExposureRepository matchingExposureRepository;
    private final MatchingConnectionRepository matchingConnectionRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ChatService chatService;

    @Value("${app.upload.profile-image-url-base:http://localhost:8080/api/v1/users/profile-images}")
    private String imageUrlBase;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(Long userId) {
        validateActiveUser(userId);
        requireProfileGender(userId);

        matchingQueueEntryRepository.findByUserId(userId)
                .ifPresentOrElse(
                        MatchingQueueEntry::activateAndRequeue,
                        () -> matchingQueueEntryRepository.save(MatchingQueueEntry.create(userId))
                );

        log.info("✅ 매칭 큐 진입/재진입 완료: userId={}", userId);
    }

    @Transactional
    public void stop(Long userId) {
        matchingQueueEntryRepository.findByUserIdAndActiveTrue(userId)
                .ifPresent(MatchingQueueEntry::deactivate);

        log.info("✅ 매칭 큐 비활성화 완료: userId={}", userId);
    }

    @Transactional
    public MatchingRecommendationResponse recommend(Long userId) {
        validateActiveUser(userId);
        Gender requesterGender = requireProfileGender(userId);

        matchingQueueEntryRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.MATCHING_NOT_STARTED));

        List<MatchingQueueEntry> available = matchingQueueEntryRepository.findAvailableCandidates(
                userId,
                requesterGender,
                PageRequest.of(0, LOOKUP_BATCH_SIZE)
        );

        List<Long> selectedIds = new ArrayList<>();
        for (MatchingQueueEntry queueEntry : available) {
            Long candidateId = queueEntry.getUserId();
            if (matchingExposureRepository.existsByUserIdAndCandidateUserId(userId, candidateId)) {
                continue;
            }
            matchingExposureRepository.save(MatchingExposure.create(userId, candidateId));
            selectedIds.add(candidateId);
            if (selectedIds.size() == RECOMMEND_LIMIT) {
                break;
            }
        }

        if (selectedIds.isEmpty()) {
            log.info("🔄 매칭 사이클 리셋: userId={}, 기존 노출 이력 삭제 후 재추천", userId);
            
            // 노출 이력을 리셋해 사이클 재시작
            matchingExposureRepository.deleteByUserId(userId);

            // 노출 이력 삭제 후 다시 조회
            available = matchingQueueEntryRepository.findAvailableCandidates(
                    userId,
                    requesterGender,
                    PageRequest.of(0, LOOKUP_BATCH_SIZE)
            );

            for (MatchingQueueEntry queueEntry : available) {
                Long candidateId = queueEntry.getUserId();
                matchingExposureRepository.save(MatchingExposure.create(userId, candidateId));
                selectedIds.add(candidateId);
                if (selectedIds.size() == RECOMMEND_LIMIT) {
                    break;
                }
            }

            if (selectedIds.isEmpty()) {
                log.warn("⚠️ 추천 대상 부족: userId={}, 충분한 후보가 없습니다", userId);
                return MatchingRecommendationResponse.builder()
                        .count(0)
                        .candidates(Collections.emptyList())
                        .build();
            }
        }

        Map<Long, User> userMap = userRepository.findAllByIdWithProfile(selectedIds)
                .stream()
                .collect(HashMap::new, (map, user) -> map.put(user.getId(), user), HashMap::putAll);

        List<MatchingCandidateResponse> candidates = selectedIds.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(this::toCandidateResponse)
                .toList();

        return MatchingRecommendationResponse.builder()
                .count(candidates.size())
                .candidates(candidates)
                .build();
    }

    @Transactional
    public MatchingConnectResponse connect(Long userId, Long targetUserId) {
        if (Objects.equals(userId, targetUserId)) {
            throw new MatchingException(MatchingErrorCode.INVALID_TARGET);
        }

        validateActiveUser(userId);
        validateActiveUser(targetUserId);

        if (!matchingExposureRepository.existsByUserIdAndCandidateUserId(userId, targetUserId)) {
            throw new MatchingException(MatchingErrorCode.CANDIDATE_NOT_EXPOSED);
        }

        Long user1 = Math.min(userId, targetUserId);
        Long user2 = Math.max(userId, targetUserId);

        Optional<MatchingConnection> existing = matchingConnectionRepository.findByUser1IdAndUser2Id(user1, user2);
        if (existing.isPresent()) {
            return new MatchingConnectResponse(existing.get().getChatRoomId(), targetUserId, true);
        }

        ChatRoomResponse room = chatService.createChatRoom("소개팅 1:1", ChatRoomType.PERSONAL, userId, targetUserId);
        MatchingConnection connection = matchingConnectionRepository.save(
                MatchingConnection.create(userId, targetUserId, room.getId())
        );

        // 연결 완료된 사용자는 큐에서 제외
        matchingQueueEntryRepository.findByUserIdAndActiveTrue(userId).ifPresent(MatchingQueueEntry::deactivate);
        matchingQueueEntryRepository.findByUserIdAndActiveTrue(targetUserId).ifPresent(MatchingQueueEntry::deactivate);

        return new MatchingConnectResponse(connection.getChatRoomId(), targetUserId, false);
    }

    private MatchingCandidateResponse toCandidateResponse(User user) {
        UserProfile profile = user.getUserProfile();

        return MatchingCandidateResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .deptName(user.getDeptName())
                .studentNum(user.getStudentNum())
                .intro(profile != null ? profile.getIntro() : null)
                .mbti(profile != null ? profile.getMbti() : null)
                .residence(profile != null ? profile.getResidence() : null)
                .profileImagePath(profile != null ? toFullImageUrl(profile.getProfileImagePath()) : null)
                .profileUpdatedAt(profile != null ? profile.getUpdatedAt() : null)
                .build();
    }

    private String toFullImageUrl(String profileImagePath) {
        if (profileImagePath == null || profileImagePath.isBlank()) {
            return null;
        }
        if (profileImagePath.startsWith("http://") || profileImagePath.startsWith("https://")) {
            return profileImagePath;
        }
        return imageUrlBase + "/" + profileImagePath;
    }

    private void validateActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.USER_NOT_FOUND));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new MatchingException(MatchingErrorCode.INVALID_TARGET);
        }
    }

    private Gender requireProfileGender(Long userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new MatchingException(MatchingErrorCode.PROFILE_REQUIRED));

        if (profile.getGender() == null) {
            throw new MatchingException(MatchingErrorCode.PROFILE_GENDER_REQUIRED);
        }

        return profile.getGender();
    }
}

