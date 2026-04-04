package univ.airconnect.ads.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "ad_reward_callbacks",
        indexes = {
                @Index(name = "idx_ad_reward_callbacks_session_received", columnList = "session_key, received_at"),
                @Index(name = "idx_ad_reward_callbacks_transaction", columnList = "transaction_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdRewardCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", length = 80)
    private String sessionKey;

    @Column(name = "transaction_id", length = 120)
    private String transactionId;

    @Column(name = "raw_query", nullable = false, length = 2000)
    private String rawQuery;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Builder
    private AdRewardCallback(String sessionKey,
                             String transactionId,
                             String rawQuery,
                             Boolean signatureValid) {
        this.sessionKey = sessionKey;
        this.transactionId = transactionId;
        this.rawQuery = rawQuery;
        this.signatureValid = signatureValid;
        this.receivedAt = LocalDateTime.now();
    }

    public static AdRewardCallback of(String sessionKey, String transactionId, String rawQuery, boolean signatureValid) {
        return AdRewardCallback.builder()
                .sessionKey(sessionKey)
                .transactionId(transactionId)
                .rawQuery(rawQuery)
                .signatureValid(signatureValid)
                .build();
    }
}

