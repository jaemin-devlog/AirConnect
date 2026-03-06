package univ.airconnect.auth.service.oauth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import univ.airconnect.auth.domain.entity.SocialProvider;
import univ.airconnect.auth.exception.AuthException;

/**
 * provider에 맞는 SocialAuthClient 구현체를 찾아준다.
 * 분기(if/switch)를 이 클래스 한 곳에만 고정하는 역할.
 */
@Component
public class SocialAuthResolver {

    private final Map<SocialProvider, SocialAuthClient> clientMap;

    public SocialAuthResolver(List<SocialAuthClient> clients) {
        Map<SocialProvider, SocialAuthClient> map = new EnumMap<>(SocialProvider.class);
        for (SocialAuthClient client : clients) {
            SocialProvider key = client.supports();
            if (map.containsKey(key)) {
                throw new IllegalStateException("Duplicate SocialAuthClient for provider: " + key);
            }
            map.put(key, client);
        }
        this.clientMap = map;
    }

    public SocialAuthClient getClient(SocialProvider provider) {
        SocialAuthClient client = clientMap.get(provider);
        if (client == null) {
            // AuthException을 쓰고 있지만, 원하면 별도 UnsupportedProviderException으로 분리해도 됨
            throw new AuthException("Unsupported social provider: " + provider);
        }
        return client;
    }
}