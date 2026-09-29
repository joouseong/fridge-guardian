package com.fridgeguardian.ingredient.barcode;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 바코드 조회 결과 캐시. 외부 API를 같은 바코드로 다시 호출하지 않기 위해 저장합니다. */
@Entity @Table(name = "barcode_products")
public class BarcodeProduct {
    @Id @Column(length = 20) private String barcode;
    @Column(nullable = false, length = 200) private String productName;
    @Column(length = 20) private String category;
    private Integer shelfLifeDays;
    @Column(nullable = false, length = 30) private String source;
    @Column(nullable = false) private LocalDateTime fetchedAt;

    protected BarcodeProduct() { }
    public BarcodeProduct(String barcode, String productName, String category, Integer shelfLifeDays,
                          String source, LocalDateTime fetchedAt) {
        this.barcode = barcode; this.productName = productName; this.category = category;
        this.shelfLifeDays = shelfLifeDays; this.source = source; this.fetchedAt = fetchedAt;
    }
    public String getBarcode() { return barcode; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public Integer getShelfLifeDays() { return shelfLifeDays; }
    public String getSource() { return source; }
}
