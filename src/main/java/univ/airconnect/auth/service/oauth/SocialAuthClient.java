package univ.airconnect.auth.service.oauth;

import univ.airconnect.auth.domain.entity.SocialProvider;

/**
 * 소셜 토큰을 검증하고 소셜 사용자 식별자(socialId)를 가져오는 인터페이스.
 * provider별(KAKAO/APPLE) 구현체가 이를 구현한다.
 */
public interface SocialAuthClient {

    /** 이 구현체가 담당하는 provider */
    SocialProvider supports();

    /**
     * 모바일 앱에서 전달받은 소셜 토큰(카카오 accessToken 등)으로
     * 소셜 서버에서 socialId(카카오 user id)를 조회해 반환한다.
     */
    String getSocialId(String socialToken);
}