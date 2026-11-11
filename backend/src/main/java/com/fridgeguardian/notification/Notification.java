package com.fridgeguardian.notification;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 유통기한 알림 큐 항목. (재료, 종류, 날짜)마다 한 건만 만들어집니다. */
@Entity @Table(name = "notifications")
public class Notification {
    public enum Type { EXPIRING, EXPIRED }
    public enum Status { PENDING, SENT, READ }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "ingredient_id", nullable = false) private Long ingredientId;
    @Column(nullable = false, length = 80) private String ingredientName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Type type;
    @Column(nullable = false) private LocalDate notifyDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.PENDING;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private LocalDateTime createdAt;

    protected Notification() { }
    public Notification(Long userId, Long ingredientId, String ingredientName, Type type, LocalDate notifyDate) {
        this.userId = userId; this.ingredientId = ingredientId; this.ingredientName = ingredientName;
        this.type = type; this.notifyDate = notifyDate;
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getIngredientId() { return ingredientId; }
    public String getIngredientName() { return ingredientName; }
    public Type getType() { return type; }
    public LocalDate getNotifyDate() { return notifyDate; }
    public Status getStatus() { return status; }
    public void markRead() { this.status = Status.READ; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
