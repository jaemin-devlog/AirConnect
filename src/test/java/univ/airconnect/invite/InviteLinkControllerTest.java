package univ.airconnect.invite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InviteLinkControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        InviteLinkController controller = new InviteLinkController();
        ReflectionTestUtils.setField(controller, "androidPackageName", "org.airconnect.hsu");
        ReflectionTestUtils.setField(controller, "androidFingerprints", "AA:BB, CC:DD");
        ReflectionTestUtils.setField(controller, "iosTeamId", "TEAM123");
        ReflectionTestUtils.setField(controller, "iosBundleId", "com.airconnect.app");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void landingPage_isReadableAndDoesNotReceiveInviteCodeOnTheServer() throws Exception {
        String html = mvc.perform(get("/join"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html)
                .contains("그룹 매칭 초대가 도착했어요")
                .contains("초대 코드 복사")
                .contains("Google Play에서 받기")
                .contains("App Store에서 받기");
    }

    @Test
    void androidAssociation_containsPackageAndAllSigningFingerprints() throws Exception {
        mvc.perform(get("/.well-known/assetlinks.json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$[0].target.package_name").value("org.airconnect.hsu"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[0]").value("AA:BB"))
                .andExpect(jsonPath("$[0].target.sha256_cert_fingerprints[1]").value("CC:DD"));
    }

    @Test
    void appleAssociation_containsAppIdAndJoinPath() throws Exception {
        mvc.perform(get("/.well-known/apple-app-site-association"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.applinks.details[0].appID").value("TEAM123.com.airconnect.app"))
                .andExpect(jsonPath("$.applinks.details[0].paths[0]").value("/join"));
    }
}
