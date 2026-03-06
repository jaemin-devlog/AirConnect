package univ.airconnect.auth.dto.request;

import univ.airconnect.auth.domain.entity.SocialProvider;

/**
 앱에서 카카오 SDK 로그인 후 카카오 accessToken을 넘김
 deviceId는 앱이 생성해서 고정 저장(UUID 추천)
 */
public record SocialLoginRequest(
        SocialProvider provider,
        String socialToken,
        String deviceId
) {}