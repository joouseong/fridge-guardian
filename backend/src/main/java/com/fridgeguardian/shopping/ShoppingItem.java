package com.fridgeguardian.shopping;

import com.fridgeguardian.ingredient.Unit;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 부족한 재료를 담아 두는 앱 내 장바구니 항목. 항상 소유자(userId) 조건으로만 조회합니다. */
@Entity @Table(name = "shopping_items")
public class ShoppingItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 80) private String name;
    @Column(precision = 12, scale = 3) private BigDecimal quantity;
    @Enumerated(EnumType.STRING) @Column(length = 20) private Unit unit;
    @Column(name = "recipe_id") private Long recipeId;
    @Column(nullable = false) private boolean checked;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private LocalDateTime createdAt;

    protected ShoppingItem() { }
    public ShoppingItem(Long userId, String name, BigDecimal quantity, Unit unit, Long recipeId) {
        this.userId = userId; this.name = name; this.quantity = quantity; this.unit = unit; this.recipeId = recipeId;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getQuantity() { return quantity; }
    public Unit getUnit() { return unit; }
    public Long getRecipeId() { return recipeId; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
