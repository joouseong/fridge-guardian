CREATE TABLE users (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(254) NOT NULL,
 password VARCHAR(100) NOT NULL, nickname VARCHAR(30) NOT NULL,
 CONSTRAINT uk_users_email UNIQUE (email)
);
CREATE TABLE ingredients (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL, category VARCHAR(20) NOT NULL,
 quantity DECIMAL(12,3) NOT NULL, unit VARCHAR(20) NOT NULL,
 purchase_date DATE NOT NULL, expiration_date DATE NOT NULL,
 storage_type VARCHAR(20) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_ingredients_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT ck_ingredients_quantity CHECK (quantity > 0),
 CONSTRAINT ck_ingredients_dates CHECK (expiration_date >= purchase_date)
);
CREATE INDEX idx_ingredients_user_expiration ON ingredients(user_id, expiration_date);
CREATE TABLE recipes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
 description VARCHAR(500) NOT NULL, cooking_time INTEGER NOT NULL,
 difficulty VARCHAR(20) NOT NULL, servings INTEGER NOT NULL, instructions TEXT NOT NULL,
 calories INTEGER NOT NULL, protein DECIMAL(8,2) NOT NULL, carbs DECIMAL(8,2) NOT NULL, fat DECIMAL(8,2) NOT NULL,
 CONSTRAINT uk_recipes_name UNIQUE (name), CONSTRAINT ck_recipe_servings CHECK (servings > 0)
);
CREATE TABLE recipe_ingredients (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, recipe_id BIGINT NOT NULL,
 ingredient_name VARCHAR(80) NOT NULL, required_quantity DECIMAL(12,3) NOT NULL, unit VARCHAR(20) NOT NULL,
 CONSTRAINT fk_recipe_ingredient_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
 CONSTRAINT uk_recipe_ingredient UNIQUE (recipe_id, ingredient_name),
 CONSTRAINT ck_recipe_quantity CHECK (required_quantity > 0)
);
