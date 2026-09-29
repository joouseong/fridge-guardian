package com.fridgeguardian.ingredient.barcode;

import com.fridgeguardian.common.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BarcodeService {
    private static final Pattern CODE = Pattern.compile("\\d{6,14}");
    private final BarcodeProductRepository products;
    private final FoodSafetyBarcodeClient client;
    private final Clock clock;

    public BarcodeService(BarcodeProductRepository products, FoodSafetyBarcodeClient client, Clock clock) {
        this.products = products; this.client = client; this.clock = clock;
    }

    /** suggestedExpirationDate는 오늘 + 기본 유통기간의 제안값입니다. 실제 제품의 표시 기한을 확인해 수정해야 합니다. */
    public record Response(String barcode, String name, String category, Integer shelfLifeDays,
                           LocalDate suggestedExpirationDate, String source) { }

    /** 저장된 결과 → 외부 API 순으로 조회합니다. 외부 호출에는 DB 트랜잭션을 열어 두지 않습니다. */
    public Response lookup(String barcode) {
        if (barcode == null || !CODE.matcher(barcode).matches()) {
            throw ApiException.badRequest("바코드는 숫자 6~14자리여야 합니다.");
        }
        BarcodeProduct product = products.findById(barcode).orElseGet(() -> fetchAndStore(barcode));
        LocalDate today = LocalDate.now(clock);
        return new Response(product.getBarcode(), product.getProductName(), product.getCategory(),
            product.getShelfLifeDays(),
            product.getShelfLifeDays() == null ? null : today.plusDays(product.getShelfLifeDays()), product.getSource());
    }

    private BarcodeProduct fetchAndStore(String barcode) {
        Optional<FoodSafetyBarcodeClient.Found> found = client.find(barcode);
        if (found.isEmpty()) {
            throw ApiException.notFound("바코드 정보를 찾을 수 없어요. 식재료 이름을 직접 입력해 주세요.");
        }
        // 이 API는 가공식품 정보이므로 카테고리는 가공식품으로 제안합니다.
        return products.save(new BarcodeProduct(barcode, found.get().productName(), "PROCESSED",
            found.get().shelfLifeDays(), "FOODSAFETY_C005", LocalDateTime.now(clock)));
    }
}
