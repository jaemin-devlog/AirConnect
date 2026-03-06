package univ.airconnect.auth.service;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import univ.airconnect.auth.apple.AppleJwtVerifier;
import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.dto.request.AppleLoginRequest;
import univ.airconnect.auth.dto.response.LoginResponse;
import univ.airconnect.global.security.jwt.JwtProvider;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AppleAuthService {

    private final AppleJwtVerifier appleJwtVerifier;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    public LoginResponse login(AppleLoginRequest request) {

        // 1️⃣ Apple 토큰 검증
        Claims claims = appleJwtVerifier.verify(request.getIdentityToken());

        String appleUserId = claims.getSubject();
        String email = claims.get("email", String.class);

        // 2️⃣ 사용자 조회
        User user = userRepository
                .findByProviderAndSocialId(
                        SocialProvider.APPLE,
                        appleUserId
                )
                .orElseGet(() -> registerAppleUser(appleUserId, email));

        // 3️⃣ JWT 발급
        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), "device");

        return new LoginResponse(accessToken, refreshToken);
    }

    private User registerAppleUser(String appleUserId, String email) {

        User user = User.create(
                SocialProvider.APPLE,
                appleUserId,
                email
        );

        return userRepository.save(user);
    }
}