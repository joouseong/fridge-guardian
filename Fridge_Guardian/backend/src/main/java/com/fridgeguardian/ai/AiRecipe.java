package com.fridgeguardian.ai;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** AI가 만든 레시피 이력. 검증 전 생성물이므로 공통 recipes 테이블과 분리해 사용자별로만 보관합니다. */
@Entity @Table(name = "ai_recipes")
public class AiRecipe {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(nullable = false, length = 150) private String title;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String content;
    @Column(nullable = false, length = 2000) private String sourceIngredients;
    @Column(nullable = false) private int servings;
    @Column(nullable = false, length = 50) private String model;
    @Column(nullable = false) private boolean favorite;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private LocalDateTime createdAt;

    protected AiRecipe() { }
    public AiRecipe(Long userId, String title, String content, String sourceIngredients, int servings, String model) {
        this.userId = userId; this.title = title; this.content = content;
        this.sourceIngredients = sourceIngredients; this.servings = servings; this.model = model;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSourceIngredients() { return sourceIngredients; }
    public int getServings() { return servings; }
    public String getModel() { return model; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
