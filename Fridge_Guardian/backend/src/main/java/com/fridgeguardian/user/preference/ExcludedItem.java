package com.fridgeguardian.user.preference;

import jakarta.persistence.*;

@Entity @Table(name = "excluded_items")
public class ExcludedItem {
    public enum Type { ALLERGY, DISLIKE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(name = "item_type", nullable = false, length = 20) private Type type;
    @Column(nullable = false, length = 80) private String name;

    protected ExcludedItem() { }
    public ExcludedItem(Long userId, Type type, String name) { this.userId = userId; this.type = type; this.name = name; }
    public Type getType() { return type; }
    public String getName() { return name; }
}
