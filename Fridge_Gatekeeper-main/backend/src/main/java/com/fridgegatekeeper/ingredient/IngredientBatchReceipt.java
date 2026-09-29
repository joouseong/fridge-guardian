package com.fridgegatekeeper.ingredient;

import com.fridgegatekeeper.user.UserAccount;
import jakarta.persistence.*;

@Entity
@Table(name = "ingredient_batch_requests")
public class IngredientBatchReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private UserAccount user;
    @Column(nullable = false, length = 36) private String requestKey;
    @Column(nullable = false, length = 64) private String payloadHash;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String responseJson;

    protected IngredientBatchReceipt() { }
    public IngredientBatchReceipt(UserAccount user, String requestKey, String payloadHash, String responseJson) {
        this.user = user;
        this.requestKey = requestKey;
        this.payloadHash = payloadHash;
        this.responseJson = responseJson;
    }
    public String getPayloadHash() { return payloadHash; }
    public String getResponseJson() { return responseJson; }
}
