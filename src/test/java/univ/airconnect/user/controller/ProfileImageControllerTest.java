package univ.airconnect.user.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.domain.entity.UserProfile;
import univ.airconnect.user.infrastructure.ProfileImageProperties;
import univ.airconnect.user.repository.UserProfileRepository;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileImageControllerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @TempDir
    Path tempDir;

    private ProfileImageController controller;

    @BeforeEach
    void setUp() {
        ProfileImageProperties properties = new ProfileImageProperties();
        properties.setProfileImageDir(tempDir.toString());
        properties.setProfileImageHiddenUserStatuses(List.of(UserStatus.DELETED, UserStatus.SUSPENDED));
        controller = new ProfileImageController(properties, userProfileRepository);
    }

    @Test
    void getProfileImage_returnsImageForActiveUser() throws Exception {
        String fileName = "active-image.jpg";
        Files.write(tempDir.resolve(fileName), createImageBytes());

        User user = user(1L, UserStatus.ACTIVE);
        UserProfile profile = profile(user, fileName);
        when(userProfileRepository.findByProfileImagePathWithUser(fileName)).thenReturn(Optional.of(profile));

        ResponseEntity<byte[]> response = controller.getProfileImage(fileName);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(0);
    }

    @Test
    void getProfileImage_hiddenWhenDeletedUser() {
        String fileName = "deleted-image.jpg";
        User user = user(2L, UserStatus.DELETED);
        UserProfile profile = profile(user, fileName);
        when(userProfileRepository.findByProfileImagePathWithUser(fileName)).thenReturn(Optional.of(profile));

        ResponseEntity<byte[]> response = controller.getProfileImage(fileName);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getProfileImage_hiddenWhenSuspendedUser() {
        String fileName = "suspended-image.jpg";
        User user = user(3L, UserStatus.SUSPENDED);
        UserProfile profile = profile(user, fileName);
        when(userProfileRepository.findByProfileImagePathWithUser(fileName)).thenReturn(Optional.of(profile));

        ResponseEntity<byte[]> response = controller.getProfileImage(fileName);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private User user(Long id, UserStatus status) {
        User user = User.create(SocialProvider.KAKAO, "social-" + id, "u" + id + "@test.dev");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private UserProfile profile(User user, String profileImagePath) {
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
        profile.updateProfileImagePath(profileImagePath);
        return profile;
    }

    private byte[] createImageBytes() throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }
}
