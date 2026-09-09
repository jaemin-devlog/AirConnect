package univ.airconnect.ticket.dto.response;

import java.time.LocalDateTime;

public record FestivalCouponRedeemResponse(
        int grantedTickets,
        int beforeTickets,
        int afterTickets,
        LocalDateTime redeemedAt
) {
}
