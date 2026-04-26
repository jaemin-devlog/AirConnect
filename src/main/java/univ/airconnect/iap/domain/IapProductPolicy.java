package univ.airconnect.iap.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public enum IapProductPolicy {
    IOS_ECONOMY_5("AirConnect_Economy_5", 5),
    IOS_PREMIUM_ECONOMY_10("AirConnect_PremiumEconomy_10", 12),
    IOS_BUSINESS_30("AirConnect_Business_30", 30),
    IOS_FIRST_CLASS_50("AirConnect_FirstClass_50", 70),
    LEGACY_PACK_5("com.airconnect.tickets.pack5", 5),
    LEGACY_PACK_10("com.airconnect.tickets.pack10", 10),
    LEGACY_PACK_30("com.airconnect.tickets.pack30", 30),
    LEGACY_PACK_50("com.airconnect.tickets.pack50", 50);

    private static final Map<String, IapProductPolicy> BY_PRODUCT_ID = new LinkedHashMap<>();

    private final String productId;
    private final int tickets;

    static {
        Arrays.stream(values()).forEach(value -> BY_PRODUCT_ID.put(value.productId, value));
    }

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
        return BY_PRODUCT_ID.get(productId);
    }
}
