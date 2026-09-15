package univ.airconnect.invite;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InviteLinkController {

    private static final String PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=org.airconnect.hsu";
    private static final String APP_STORE_URL =
            "https://apps.apple.com/kr/app/%EC%97%90%EC%96%B4%EC%BB%A4%EB%84%A5%ED%8A%B8-airconnect/id6761365188";

    @Value("${app.links.android-package-name:org.airconnect.hsu}")
    private String androidPackageName;

    @Value("${app.links.android-sha256-cert-fingerprints:}")
    private String androidFingerprints;

    @Value("${app.links.ios-team-id:}")
    private String iosTeamId;

    @Value("${app.links.ios-bundle-id:}")
    private String iosBundleId;

    @GetMapping(value = "/join", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> join() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header(
                        "Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; "
                                + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
                )
                .body(landingHtml());
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        String normalizedUserAgent = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        if (normalizedUserAgent.contains("android")) {
            return storeRedirect(PLAY_STORE_URL);
        }
        if (normalizedUserAgent.contains("iphone")
                || normalizedUserAgent.contains("ipad")
                || normalizedUserAgent.contains("ipod")) {
            return storeRedirect(APP_STORE_URL);
        }

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header(
                        "Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'"
                )
                .body(downloadLandingHtml());
    }

    @GetMapping(value = "/.well-known/assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> androidAssociation() {
        List<String> fingerprints = Arrays.stream(androidFingerprints.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (fingerprints.isEmpty()) {
            return missingAssociationConfiguration("Android app signing SHA-256 fingerprint");
        }
        Map<String, Object> target = Map.of(
                "namespace", "android_app",
                "package_name", androidPackageName,
                "sha256_cert_fingerprints", fingerprints
        );
        return associationResponse(List.of(Map.of(
                "relation", List.of("delegate_permission/common.handle_all_urls"),
                "target", target
        )));
    }

    @GetMapping(value = "/.well-known/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> appleAssociation() {
        if (isBlank(iosTeamId) || isBlank(iosBundleId)) {
            return missingAssociationConfiguration("iOS Team ID and Bundle ID");
        }
        Map<String, Object> details = Map.of(
                "appID", iosTeamId.trim() + "." + iosBundleId.trim(),
                "paths", List.of("/join")
        );
        return associationResponse(Map.of(
                "applinks", Map.of("apps", List.of(), "details", List.of(details))
        ));
    }

    private ResponseEntity<?> associationResponse(Object body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private ResponseEntity<Void> storeRedirect(String storeUrl) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(storeUrl))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResponseEntity<?> missingAssociationConfiguration(String missing) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("error", "App link association is not configured", "missing", missing));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String landingHtml() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
                  <meta name="theme-color" content="#FFBE24">
                  <title>에어커넥트 그룹 매칭 초대</title>
                  <style>
                    *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;
                    background:#f4f4f4;color:#111;font-family:-apple-system,BlinkMacSystemFont,"Pretendard","Noto Sans KR",sans-serif}
                    main{width:min(100%,420px);padding:36px 28px;background:#fff;border-radius:28px;text-align:center;
                    box-shadow:0 12px 32px rgba(0,0,0,.12)}.mark{font-size:42px}h1{margin:12px 0 8px;font-size:24px}
                    .guide{margin:0;color:#6f747b;font-size:15px;line-height:1.6}.code-box{margin:24px 0 18px;padding:18px;
                    border-radius:18px;background:#fff7df}.label{display:block;color:#777;font-size:13px}.code{display:block;margin-top:4px;
                    color:#d98900;font-size:32px;font-weight:800;letter-spacing:4px}.copy{margin-top:10px;border:0;background:transparent;
                    color:#7c5a00;font-size:14px;text-decoration:underline}.stores{display:grid;gap:10px}.store{display:block;padding:14px 16px;
                    border-radius:14px;background:#111;color:#fff;text-decoration:none;font-weight:700}.status{margin:18px 0 0;color:#8b9199;font-size:13px}
                  </style>
                </head>
                <body>
                  <main>
                    <div class="mark" aria-hidden="true">✈️</div>
                    <h1>그룹 매칭 초대가 도착했어요</h1>
                    <p class="guide">에어커넥트 앱에서 초대 코드를 입력하고<br>친구와 함께 매칭을 시작해 보세요.</p>
                    <div class="code-box"><span class="label">초대 코드</span><strong id="code" class="code">확인 중</strong>
                      <button id="copy" class="copy" type="button">초대 코드 복사</button></div>
                    <div class="stores"><a class="store" href="__PLAY_STORE__">Google Play에서 받기</a>
                      <a class="store" href="__APP_STORE__">App Store에서 받기</a></div>
                    <p id="status" class="status">기기에 맞는 설치 페이지를 확인하고 있어요.</p>
                  </main>
                  <script>
                    (()=>{const match=decodeURIComponent(location.hash.slice(1)).match(/^(?:code=)?(\\d{6})$/);
                    const code=match?match[1]:'';const codeEl=document.getElementById('code');const copy=document.getElementById('copy');
                    codeEl.textContent=code||'코드를 확인해 주세요';copy.hidden=!code;copy.onclick=async()=>{try{await navigator.clipboard.writeText(code);
                    copy.textContent='복사했어요'}catch(_){copy.textContent='코드: '+code}};const ua=navigator.userAgent;
                    const ios=/iPad|iPhone|iPod/.test(ua)||(navigator.platform==='MacIntel'&&navigator.maxTouchPoints>1);
                    const android=/Android/i.test(ua);const store=ios?'__APP_STORE__':android?'__PLAY_STORE__':null;
                    if(store){document.getElementById('status').textContent='잠시 후 스토어로 이동합니다.';setTimeout(()=>location.replace(store),900)}
                    else{document.getElementById('status').textContent='사용 중인 기기의 스토어를 선택해 주세요.'}})();
                  </script>
                </body>
                </html>
                """.replace("__PLAY_STORE__", PLAY_STORE_URL)
                .replace("__APP_STORE__", APP_STORE_URL);
    }

    private String downloadLandingHtml() {
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
                  <meta name="theme-color" content="#FFBE24">
                  <title>에어커넥트 다운로드</title>
                  <style>
                    *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;
                    background:#f4f4f4;color:#111;font-family:-apple-system,BlinkMacSystemFont,"Pretendard","Noto Sans KR",sans-serif}
                    main{width:min(100%,420px);padding:38px 28px;background:#fff;border-radius:28px;text-align:center;
                    box-shadow:0 12px 32px rgba(0,0,0,.12)}.mark{font-size:46px}h1{margin:12px 0 8px;font-size:25px}
                    p{margin:0 0 24px;color:#6f747b;font-size:15px;line-height:1.6}.stores{display:grid;gap:12px}
                    a{display:block;padding:15px 16px;border-radius:14px;background:#111;color:#fff;text-decoration:none;font-weight:700}
                  </style>
                </head>
                <body><main><div class="mark" aria-hidden="true">✈️</div><h1>에어커넥트 시작하기</h1>
                  <p>사용 중인 기기에 맞는 스토어를 선택해 주세요.</p><div class="stores">
                    <a href="__PLAY_STORE__" rel="noreferrer">Google Play에서 받기</a>
                    <a href="__APP_STORE__" rel="noreferrer">App Store에서 받기</a>
                  </div></main></body></html>
                """.replace("__PLAY_STORE__", PLAY_STORE_URL)
                .replace("__APP_STORE__", APP_STORE_URL);
    }
}
