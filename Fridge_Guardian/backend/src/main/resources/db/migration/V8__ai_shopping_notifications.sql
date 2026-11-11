-- AI가 만든 레시피 이력. 검증 전 생성물이므로 공통 recipes 테이블과 분리합니다.
CREATE TABLE ai_recipes (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 title VARCHAR(150) NOT NULL,
 content LONGTEXT NOT NULL,
 source_ingredients VARCHAR(2000) NOT NULL,
 servings INT NOT NULL,
 model VARCHAR(50) NOT NULL,
 favorite BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_ai_recipe_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_ai_recipes_user_created ON ai_recipes(user_id, created_at);

-- 부족한 재료를 담아 두는 앱 내 장바구니.
CREATE TABLE shopping_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL,
 quantity DECIMAL(12,3) NULL,
 unit VARCHAR(20) NULL,
 recipe_id BIGINT NULL,
 checked BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_shopping_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_shopping_recipe FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE SET NULL
);
CREATE INDEX idx_shopping_user ON shopping_items(user_id, checked);

-- 유통기한 알림 큐. 같은 재료·종류·날짜는 한 번만 적재합니다.
CREATE TABLE notifications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 ingredient_id BIGINT NOT NULL,
 ingredient_name VARCHAR(80) NOT NULL,
 type VARCHAR(20) NOT NULL,
 notify_date DATE NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT fk_notification_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE CASCADE,
 CONSTRAINT uk_notification UNIQUE (ingredient_id, type, notify_date)
);
CREATE INDEX idx_notification_user_status ON notifications(user_id, status);
