package univ.airconnect.ticket.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.ticket.domain.entity.FestivalCoupon;
import univ.airconnect.ticket.repository.FestivalCouponRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class FestivalCouponSeeder implements ApplicationRunner {

    private static final String COUPON_RESOURCE = "festival/coupons-500.txt";

    private final FestivalCouponRepository festivalCouponRepository;

    @Value("${app.festival-coupons.seed-enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!seedEnabled) {
            return;
        }

        List<String> codes = readAndValidateCodes();
        Set<String> existingCodes = new HashSet<>(festivalCouponRepository.findAllCodes());
        List<FestivalCoupon> missingCoupons = codes.stream()
                .filter(code -> !existingCodes.contains(code))
                .map(FestivalCoupon::issue)
                .toList();

        if (!missingCoupons.isEmpty()) {
            festivalCouponRepository.saveAll(missingCoupons);
        }
        log.info("Festival coupons ready. catalogSize={}, inserted={}", codes.size(), missingCoupons.size());
    }

    private List<String> readAndValidateCodes() throws IOException {
        ClassPathResource resource = new ClassPathResource(COUPON_RESOURCE);
        List<String> codes;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            codes = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
        }

        if (codes.size() != 500 || new HashSet<>(codes).size() != 500
                || codes.stream().anyMatch(code -> !code.matches("\\d{6}"))) {
            throw new IllegalStateException("축제 쿠폰 원본은 중복 없는 숫자 6자리 코드 500개여야 합니다.");
        }
        return codes;
    }
}
