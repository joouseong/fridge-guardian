package com.fridgegatekeeper.recipe;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="recipes")
public class Recipe {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=100) private String name;
    @Column(nullable=false, length=500) private String description;
    @Column(nullable=false) private int cookingTime;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private Difficulty difficulty;
    @Column(nullable=false) private int servings;
    @Column(nullable=false, columnDefinition="TEXT") private String instructions;
    @Column(nullable=false) private int calories;
    @Column(nullable=false, precision=8, scale=2) private BigDecimal protein;
    @Column(nullable=false, precision=8, scale=2) private BigDecimal carbs;
    @Column(nullable=false, precision=8, scale=2) private BigDecimal fat;
    @OneToMany(mappedBy="recipe", cascade=CascadeType.ALL, orphanRemoval=true)
    @OrderBy("id ASC") private List<RecipeIngredient> ingredients=new ArrayList<>();
    protected Recipe() {}
    public Recipe(String name,String description,int cookingTime,Difficulty difficulty,int servings,
                  String instructions,int calories,BigDecimal protein,BigDecimal carbs,BigDecimal fat) {
        this.name=name; this.description=description; this.cookingTime=cookingTime; this.difficulty=difficulty;
        this.servings=servings; this.instructions=instructions; this.calories=calories;
        this.protein=protein; this.carbs=carbs; this.fat=fat;
    }
    public void addIngredient(RecipeIngredient ingredient) { ingredients.add(ingredient); }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getCookingTime() { return cookingTime; }
    public Difficulty getDifficulty() { return difficulty; }
    public int getServings() { return servings; }
    public String getInstructions() { return instructions; }
    public List<String> getSteps() { return instructions.lines().toList(); }
    public int getCalories() { return calories; }
    public BigDecimal getProtein() { return protein; }
    public BigDecimal getCarbs() { return carbs; }
    public BigDecimal getFat() { return fat; }
    public List<RecipeIngredient> getIngredients() { return ingredients; }
}
