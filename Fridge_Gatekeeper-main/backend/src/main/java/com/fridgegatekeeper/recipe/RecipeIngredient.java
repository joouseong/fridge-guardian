package com.fridgegatekeeper.recipe;
import com.fridgegatekeeper.ingredient.Unit;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="recipe_ingredients")
public class RecipeIngredient {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="recipe_id", nullable=false) private Recipe recipe;
    @Column(nullable=false, length=80) private String ingredientName;
    @Column(nullable=false, precision=12, scale=3) private BigDecimal requiredQuantity;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Unit unit;
    protected RecipeIngredient() {}
    public RecipeIngredient(Recipe recipe,String ingredientName,BigDecimal requiredQuantity,Unit unit) {
        this.recipe=recipe; this.ingredientName=ingredientName; this.requiredQuantity=requiredQuantity; this.unit=unit;
    }
    public Long getId() { return id; }
    public String getIngredientName() { return ingredientName; }
    public BigDecimal getRequiredQuantity() { return requiredQuantity; }
    public Unit getUnit() { return unit; }
}
