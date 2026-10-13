package com.fridgeguardian.ingredient;
import com.fridgeguardian.user.UserAccount;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.OptimisticLock;

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
    // 서버가 만든 파일 이름만 저장합니다. 사진 교체가 목록 화면의 수정 버전을 무효로 만들지 않도록 잠금에서 제외합니다.
    @OptimisticLock(excluded=true) @Column(length=255) private String imagePath;
    @OptimisticLock(excluded=true) @Column(length=500) private String imageAttributionUrl;
    @Column(length=500) private String memo;
    @Column(length=20) private String barcode;
    @Version private long version;
    protected Ingredient() {}
    public Ingredient(UserAccount user, String name, Category category, BigDecimal quantity, Unit unit,
                      LocalDate purchaseDate, LocalDate expirationDate, StorageType storageType) {
        this(user, name, category, quantity, unit, purchaseDate, expirationDate, storageType, null, null);
    }
    public Ingredient(UserAccount user, String name, Category category, BigDecimal quantity, Unit unit,
                      LocalDate purchaseDate, LocalDate expirationDate, StorageType storageType,
                      String memo, String barcode) {
        this.user=user;
        update(name,category,quantity,unit,purchaseDate,expirationDate,storageType,memo,barcode);
    }
    public void update(String name, Category category, BigDecimal quantity, Unit unit,
                       LocalDate purchaseDate, LocalDate expirationDate, StorageType storageType,
                       String memo, String barcode) {
        this.name=name; this.category=category; this.quantity=quantity; this.unit=unit;
        this.purchaseDate=purchaseDate; this.expirationDate=expirationDate; this.storageType=storageType;
        this.memo=memo; this.barcode=barcode;
    }
    public void setImagePath(String imagePath) { this.imagePath=imagePath; }
    public void setImageAttributionUrl(String imageAttributionUrl) { this.imageAttributionUrl=imageAttributionUrl; }
    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getName() { return name; }
    public Category getCategory() { return category; }
    public BigDecimal getQuantity() { return quantity; }
    public Unit getUnit() { return unit; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public StorageType getStorageType() { return storageType; }
    public String getImagePath() { return imagePath; }
    public String getImageAttributionUrl() { return imageAttributionUrl; }
    public String getMemo() { return memo; }
    public String getBarcode() { return barcode; }
    public long getVersion() { return version; }
}
