package univ.airconnect.iap.domain;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public enum IapProductPolicy {
    IOS_ECONOMY_5("AirConnect_Economy_5", 8, 1_100),
    IOS_PREMIUM_ECONOMY_10("AirConnect_PremiumEconomy_10", 19, 2_200),
    IOS_BUSINESS_30("AirConnect_Business_30", 50, 5_500),
    IOS_FIRST_CLASS_50("AirConnect_FirstClass_50", 115, 11_000),
    LEGACY_PACK_5("com.airconnect.tickets.pack5", 8, 1_100),
    LEGACY_PACK_12("com.airconnect.tickets.pack12", 19, 2_200),
    LEGACY_PACK_10("com.airconnect.tickets.pack10", 10, null),
    LEGACY_PACK_30("com.airconnect.tickets.pack30", 50, 5_500),
    LEGACY_PACK_50("com.airconnect.tickets.pack50", 50, 5_500),
    LEGACY_PACK_70("com.airconnect.tickets.pack70", 115, 11_000);

    private static final Map<String, IapProductPolicy> BY_PRODUCT_ID = new LinkedHashMap<>();

    private final String productId;
    private final int tickets;
    private final Integer catalogPriceKrw;

    static {
        Arrays.stream(values()).forEach(value -> BY_PRODUCT_ID.put(value.productId, value));
    }

    IapProductPolicy(String productId, int tickets, Integer catalogPriceKrw) {
        this.productId = productId;
        this.tickets = tickets;
        this.catalogPriceKrw = catalogPriceKrw;
    }

    public String getProductId() {
        return productId;
    }

    public int getTickets() {
        return tickets;
    }

    /**
     * 관리자 화면에 표시하는 현재 한국 상품 기준가다. 결제 당시 영수증 금액이 아니다.
     */
    public Integer getCatalogPriceKrw() {
        return catalogPriceKrw;
    }

    public static IapProductPolicy fromProductId(String productId) {
        return BY_PRODUCT_ID.get(productId);
    }
}
