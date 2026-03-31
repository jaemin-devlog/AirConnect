package univ.airconnect.iap.domain;

import java.util.Arrays;

public enum IapProductPolicy {
    PACK_5("com.airconnect.tickets.pack5", 5),
    PACK_10("com.airconnect.tickets.pack10", 10),
    PACK_30("com.airconnect.tickets.pack30", 30),
    PACK_50("com.airconnect.tickets.pack50", 50);

    private final String productId;
    private final int tickets;

    IapProductPolicy(String productId, int tickets) {
        this.productId = productId;
        this.tickets = tickets;
    }

    public String getProductId() {
        return productId;
    }

    public int getTickets() {
        return tickets;
    }

    public static IapProductPolicy fromProductId(String productId) {
        return Arrays.stream(values())
                .filter(v -> v.productId.equals(productId))
                .findFirst()
                .orElse(null);
    }
}

