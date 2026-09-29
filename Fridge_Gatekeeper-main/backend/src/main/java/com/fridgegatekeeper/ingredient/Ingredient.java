package com.fridgegatekeeper.ingredient;
import com.fridgegatekeeper.user.UserAccount;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="ingredients")
public class Ingredient {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    // 조회·수정·삭제 시 항상 소유자 조건을 함께 사용합니다.
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id", nullable=false) private UserAccount user;
    @Column(nullable=false, length=80) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Category category;
    @Column(nullable=false, precision=12, scale=3) private BigDecimal quantity;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Unit unit;
    @Column(nullable=false) private LocalDate purchaseDate;
    @Column private LocalDate expirationDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private StorageType storageType;
    @Version private long version;
    protected Ingredient() {}
    public Ingredient(UserAccount user, String name, Category category, BigDecimal quantity, Unit unit,
                      LocalDate purchaseDate, LocalDate expirationDate, StorageType storageType) {
        this.user=user;
        update(name,category,quantity,unit,purchaseDate,expirationDate,storageType);
    }
    public void update(String name, Category category, BigDecimal quantity, Unit unit,
                       LocalDate purchaseDate, LocalDate expirationDate, StorageType storageType) {
        this.name=name; this.category=category; this.quantity=quantity; this.unit=unit;
        this.purchaseDate=purchaseDate; this.expirationDate=expirationDate; this.storageType=storageType;
    }
    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getName() { return name; }
    public Category getCategory() { return category; }
    public BigDecimal getQuantity() { return quantity; }
    public Unit getUnit() { return unit; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public StorageType getStorageType() { return storageType; }
    public long getVersion() { return version; }
}
