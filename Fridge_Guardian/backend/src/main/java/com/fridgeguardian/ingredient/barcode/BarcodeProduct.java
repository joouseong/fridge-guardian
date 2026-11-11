package com.fridgeguardian.ingredient.barcode;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 바코드 조회 결과 캐시. 외부 API를 같은 바코드로 다시 호출하지 않기 위해 저장합니다. */
@Entity @Table(name = "barcode_products")
public class BarcodeProduct {
    @Id @Column(length = 20) private String barcode;
    @Column(nullable = false, length = 200) private String productName;
    @Column(length = 20) private String category;
    @Column(length = 200) private String brand;
    @Column(length = 1000) private String productImageUrl;
    private Integer shelfLifeDays;
    @Column(nullable = false, length = 30) private String source;
    @Column(nullable = false) private LocalDateTime fetchedAt;

    protected BarcodeProduct() { }
    public BarcodeProduct(String barcode, String productName, String category, Integer shelfLifeDays,
                          String source, LocalDateTime fetchedAt) {
        this(barcode, productName, category, null, null, shelfLifeDays, source, fetchedAt);
    }
    public BarcodeProduct(String barcode, String productName, String category, String brand, String productImageUrl,
                          Integer shelfLifeDays, String source, LocalDateTime fetchedAt) {
        this.barcode = barcode; this.productName = productName; this.category = category;
        this.brand = brand; this.productImageUrl = productImageUrl;
        this.shelfLifeDays = shelfLifeDays; this.source = source; this.fetchedAt = fetchedAt;
    }
    public void supplementImage(String brand, String productImageUrl) {
        if (productImageUrl == null || productImageUrl.isBlank()) return;
        if (this.brand == null || this.brand.isBlank()) this.brand = brand;
        this.productImageUrl = productImageUrl;
    }
    public String getBarcode() { return barcode; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public String getBrand() { return brand; }
    public String getProductImageUrl() { return productImageUrl; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public String getSource() { return source; }
}
