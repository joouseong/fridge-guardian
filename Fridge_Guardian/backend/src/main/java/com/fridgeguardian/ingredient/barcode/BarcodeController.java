package com.fridgeguardian.ingredient.barcode;

import com.fridgeguardian.user.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/barcodes")
public class BarcodeController {
    private final BarcodeService barcodes;

    public BarcodeController(BarcodeService barcodes) { this.barcodes = barcodes; }

    @GetMapping("/{code}")
    public BarcodeService.Response lookup(Authentication authentication, @PathVariable String code) {
        CurrentUser.id(authentication);
        return barcodes.lookup(code);
    }
}
