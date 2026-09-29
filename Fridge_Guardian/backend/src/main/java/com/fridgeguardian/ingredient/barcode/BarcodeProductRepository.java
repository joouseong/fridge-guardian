package com.fridgeguardian.ingredient.barcode;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BarcodeProductRepository extends JpaRepository<BarcodeProduct, String> { }
