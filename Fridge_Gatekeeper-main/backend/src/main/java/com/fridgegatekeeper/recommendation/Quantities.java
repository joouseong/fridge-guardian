package com.fridgegatekeeper.recommendation;
import com.fridgegatekeeper.ingredient.Unit;
import java.math.BigDecimal;
import java.util.Optional;

public final class Quantities {
    private Quantities() {}
    public static Optional<BigDecimal> convert(BigDecimal quantity,Unit from,Unit to) {
        if (from==to) return Optional.of(quantity);
        if ((from==Unit.KILOGRAM && to==Unit.GRAM) || (from==Unit.LITER && to==Unit.MILLILITER))
            return Optional.of(quantity.multiply(BigDecimal.valueOf(1000)));
        if ((from==Unit.GRAM && to==Unit.KILOGRAM) || (from==Unit.MILLILITER && to==Unit.LITER))
            return Optional.of(quantity.movePointLeft(3));
        // '한 팩'의 무게는 제품마다 달라 임의로 g이나 개로 바꾸지 않습니다.
        return Optional.empty();
    }
}
