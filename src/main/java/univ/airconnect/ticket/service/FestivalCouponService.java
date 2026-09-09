package univ.airconnect.ticket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import univ.airconnect.global.error.BusinessException;
import univ.airconnect.global.error.ErrorCode;
import univ.airconnect.iap.domain.entity.TicketLedger;
import univ.airconnect.iap.repository.TicketLedgerRepository;
import univ.airconnect.ticket.domain.entity.FestivalCoupon;
import univ.airconnect.ticket.dto.response.FestivalCouponRedeemResponse;
import univ.airconnect.ticket.repository.FestivalCouponRepository;
import univ.airconnect.user.domain.UserStatus;
import univ.airconnect.user.domain.entity.User;
import univ.airconnect.user.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FestivalCouponService {

    private final FestivalCouponRepository festivalCouponRepository;
    private final UserRepository userRepository;
    private final TicketLedgerRepository ticketLedgerRepository;

    @Transactional
    public FestivalCouponRedeemResponse redeem(Long userId, String rawCode) {
        String code = normalizeCode(rawCode);
        FestivalCoupon coupon = festivalCouponRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.FESTIVAL_COUPON_INVALID));

        if (coupon.isRedeemed()) {
            throw new BusinessException(ErrorCode.FESTIVAL_COUPON_ALREADY_REDEEMED);
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        int beforeTickets = user.getTickets();
        int grantedTickets = coupon.getTicketAmount();
        user.addTickets(grantedTickets);
        int afterTickets = user.getTickets();
        LocalDateTime redeemedAt = LocalDateTime.now();

        coupon.redeem(userId, redeemedAt);
        ticketLedgerRepository.save(TicketLedger.grantForFestivalCoupon(
                userId,
                grantedTickets,
                beforeTickets,
                afterTickets,
                code
        ));

        return new FestivalCouponRedeemResponse(
                grantedTickets,
                beforeTickets,
                afterTickets,
                redeemedAt
        );
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new BusinessException(ErrorCode.FESTIVAL_COUPON_INVALID);
        }
        return code;
    }
}
