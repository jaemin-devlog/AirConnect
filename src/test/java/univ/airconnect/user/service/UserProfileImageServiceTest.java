package univ.airconnect.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.iap.domain.LedgerRefType;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.user.domain.MilestoneType;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserMilestone;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.exception.UserErrorCode;
import univ.airconnect.user.exception.UserException;
import univ.airconnect.user.infrastructure.MilestoneRewardProperties;
import univ.airconnect.user.infrastructure.ProfileImageProperties;
import univ.airconnect.user.repository.UserMilestoneRepository;
import univ.airconnect.user.repository.UserProfileRepository;
import univ.airconnect.user.repository.UserRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileImageServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMilestoneRepository userMilestoneRepository;
    @Mock
    private TicketLedgerRepository ticketLedgerRepository;

    @TempDir
    Path tempDir;

    private UserProfileImageService service;
    private MilestoneRewardProperties rewardProperties;

    @BeforeEach
    void setUp() {
        ProfileImageProperties properties = new ProfileImageProperties();
        properties.setProfileImageDir(tempDir.toString());
        properties.setProfileImageUrlBase("http://localhost:8080/api/v1/users/profile-images");
        properties.setProfileImageMaxBytes(5L * 1024L * 1024L);
        properties.setProfileImageMaxPixels(25_000_000L);
        properties.setProfileImageAllowedFormats(List.of("jpg", "jpeg", "png"));
        rewardProperties = new MilestoneRewardProperties();
        rewardProperties.setProfileImageUploadedTickets(1);
        rewardProperties.setEmailVerifiedTickets(1);
        service = new UserProfileImageService(
                userProfileRepository,
                userRepository,
                userMilestoneRepository,
                properties,
                rewardProperties,
                ticketLedgerRepository
        );
    }

    @Test
    void saveProfileImage_successAndReplaceOldFile() throws Exception {
        Long userId = 1L;
        User user = activeUser(userId);
        UserProfile profile = profile(user);
        profile.updateProfileImagePath("old-file.jpg");
        Files.write(tempDir.resolve("old-file.jpg"), createImageBytes("jpg"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userRepository.findByIdForTicketUpdate(userId)).thenReturn(Optional.of(user));
        when(userMilestoneRepository.findByUserIdAndMilestoneTypeForUpdate(userId, MilestoneType.PROFILE_IMAGE_UPLOADED))
                .thenReturn(Optional.of(UserMilestone.create(userId, MilestoneType.PROFILE_IMAGE_UPLOADED)));

        int before = user.getTickets();
        MockMultipartFile file = new MockMultipartFile("file", "new-profile.jpg", "image/jpeg", createImageBytes("jpg"));
        String imageUrl = service.saveProfileImage(userId, file);

        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        assertThat(fileName).endsWith(".jpg");
        assertThat(Files.exists(tempDir.resolve(fileName))).isTrue();
        assertThat(Files.exists(tempDir.resolve("old-file.jpg"))).isFalse();
        assertThat(profile.getProfileImagePath()).isEqualTo(fileName);
        assertThat(user.getTickets()).isEqualTo(before);
        verify(userMilestoneRepository, never()).save(any());
        verify(ticketLedgerRepository, never()).save(any(TicketLedger.class));
    }

    @Test
    void saveProfileImage_failsWhenFileIsEmpty() {
        Long userId = 2L;
        User user = activeUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.saveProfileImage(userId, file))
                .isInstanceOf(UserException.class)
                .extracting(ex -> ((UserException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.PROFILE_IMAGE_EMPTY);
    }

    @Test
    void saveProfileImage_failsWhenExtensionNotAllowed() throws Exception {
        Long userId = 3L;
        User user = activeUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "profile.txt", "image/jpeg", createImageBytes("jpg"));

        assertThatThrownBy(() -> service.saveProfileImage(userId, file))
                .isInstanceOf(UserException.class)
                .extracting(ex -> ((UserException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.PROFILE_IMAGE_UNSUPPORTED_FORMAT);
    }

    @Test
    void saveProfileImage_failsWhenDisguisedAsImage() {
        Long userId = 4L;
        User user = activeUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThatThrownBy(() -> service.saveProfileImage(userId, file))
                .isInstanceOf(UserException.class)
                .extracting(ex -> ((UserException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.PROFILE_IMAGE_CORRUPTED);
    }

    @Test
    void saveProfileImage_failsWhenTooLarge() throws Exception {
        ProfileImageProperties tinyLimit = new ProfileImageProperties();
        tinyLimit.setProfileImageDir(tempDir.toString());
        tinyLimit.setProfileImageUrlBase("http://localhost:8080/api/v1/users/profile-images");
        tinyLimit.setProfileImageMaxBytes(10);
        tinyLimit.setProfileImageAllowedFormats(List.of("jpg", "jpeg", "png"));
        MilestoneRewardProperties rewardProperties = new MilestoneRewardProperties();
        rewardProperties.setProfileImageUploadedTickets(1);
        rewardProperties.setEmailVerifiedTickets(1);
        UserProfileImageService tinyLimitService = new UserProfileImageService(
                userProfileRepository,
                userRepository,
                userMilestoneRepository,
                tinyLimit,
                rewardProperties,
                ticketLedgerRepository
        );

        Long userId = 5L;
        User user = activeUser(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", createImageBytes("jpg"));

        assertThatThrownBy(() -> tinyLimitService.saveProfileImage(userId, file))
                .isInstanceOf(UserException.class)
                .extracting(ex -> ((UserException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
    }

    @Test
    void saveProfileImage_failsForSuspendedUser() throws Exception {
        Long userId = 6L;
        User user = activeUser(userId);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", createImageBytes("jpg"));

        assertThatThrownBy(() -> service.saveProfileImage(userId, file))
                .isInstanceOf(UserException.class)
                .extracting(ex -> ((UserException) ex).getErrorCode())
                .isEqualTo(UserErrorCode.USER_SUSPENDED);
    }

    @Test
    void saveProfileImage_grantsOneTicketForProfileImageMilestone() throws Exception {
        Long userId = 7L;
        User user = activeUser(userId);
        UserProfile profile = profile(user);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userRepository.findByIdForTicketUpdate(userId)).thenReturn(Optional.of(user));
        when(userMilestoneRepository.findByUserIdAndMilestoneTypeForUpdate(userId, MilestoneType.PROFILE_IMAGE_UPLOADED))
                .thenReturn(Optional.empty());

        int before = user.getTickets();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", createImageBytes("jpg"));

        service.saveProfileImage(userId, file);

        assertThat(user.getTickets()).isEqualTo(before + 1);
        verify(userMilestoneRepository).save(org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<TicketLedger> history = ArgumentCaptor.forClass(TicketLedger.class);
        verify(ticketLedgerRepository).save(history.capture());
        assertThat(history.getValue().getUserId()).isEqualTo(userId);
        assertThat(history.getValue().getBeforeAmount()).isEqualTo(before);
        assertThat(history.getValue().getChangeAmount()).isEqualTo(1);
        assertThat(history.getValue().getAfterAmount()).isEqualTo(before + 1);
        assertThat(history.getValue().getRefType()).isEqualTo(LedgerRefType.MILESTONE_REWARD);
        assertThat(history.getValue().getRefId()).isEqualTo("milestone:" + userId + ":PROFILE_IMAGE_UPLOADED");
        assertThat(history.getValue().getReason()).isEqualTo(MilestoneType.PROFILE_IMAGE_UPLOADED.name());
    }

    @Test
    void saveProfileImage_recordsMilestoneWithoutTicketHistoryWhenRewardIsZero() throws Exception {
        rewardProperties.setProfileImageUploadedTickets(0);
        Long userId = 8L;
        User user = activeUser(userId);
        UserProfile profile = profile(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userRepository.findByIdForTicketUpdate(userId)).thenReturn(Optional.of(user));
        when(userMilestoneRepository.findByUserIdAndMilestoneTypeForUpdate(userId, MilestoneType.PROFILE_IMAGE_UPLOADED))
                .thenReturn(Optional.empty());

        int before = user.getTickets();
        MockMultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", createImageBytes("jpg"));
        String imageUrl = service.saveProfileImage(userId, file);

        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        assertThat(Files.exists(tempDir.resolve(fileName))).isTrue();
        assertThat(profile.getProfileImagePath()).isEqualTo(fileName);
        assertThat(user.getTickets()).isEqualTo(before);
        verify(userMilestoneRepository).save(any(UserMilestone.class));
        verify(ticketLedgerRepository, never()).save(any(TicketLedger.class));
    }

    private User activeUser(Long id) {
        User user = User.create(SocialProvider.KAKAO, "social-" + id, "u" + id + "@test.dev");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProfile profile(User user) {
        UserProfile profile = UserProfile.create(
                user,
                170,
                25,
                "INTJ",
                "N",
                null,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(profile, "userId", user.getId());
        return profile;
    }

    private byte[] createImageBytes(String format) throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}
