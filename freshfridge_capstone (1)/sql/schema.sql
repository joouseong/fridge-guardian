-- =========================================================
-- FreshFridge 데이터베이스 생성 스크립트
-- MySQL 8.x 기준
-- =========================================================

-- CREATE DATABASE IF NOT EXISTS freshfridge
--   CHARACTER SET utf8mb4
--   COLLATE utf8mb4_unicode_ci;

-- USE freshfridge;

DROP TABLE IF EXISTS recipe_ingredients;
DROP TABLE IF EXISTS recipes;
DROP TABLE IF EXISTS ingredients;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ingredients (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(100) NOT NULL,
  category VARCHAR(50) NULL,
  quantity DECIMAL(10,2) NOT NULL DEFAULT 1,
  unit VARCHAR(20) NOT NULL DEFAULT '개',
  purchase_date DATE NULL,
  expiry_date DATE NOT NULL,
  storage_location ENUM('냉장', '냉동', '실온') NOT NULL DEFAULT '냉장',
  memo VARCHAR(500) NULL,
  image_path VARCHAR(255) NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  CONSTRAINT fk_ingredients_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,

  INDEX idx_ingredients_user_expiry (user_id, expiry_date),
  INDEX idx_ingredients_name (name)
);

CREATE TABLE recipes (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(300) NULL,
  cooking_time INT NOT NULL DEFAULT 15,
  difficulty ENUM('쉬움', '보통', '어려움') NOT NULL DEFAULT '쉬움',
  instructions TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE recipe_ingredients (
  id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  recipe_id BIGINT UNSIGNED NOT NULL,
  ingredient_name VARCHAR(100) NOT NULL,

  CONSTRAINT fk_recipe_ingredients_recipe
    FOREIGN KEY (recipe_id) REFERENCES recipes(id)
    ON DELETE CASCADE,

  INDEX idx_recipe_ingredient_name (ingredient_name)
);

-- ---------------------------------------------------------
-- 기본 레시피 샘플 데이터
-- ---------------------------------------------------------
INSERT INTO recipes (name, description, cooking_time, difficulty, instructions) VALUES
('계란볶음밥', '간단하고 빠르게 만들 수 있는 한 끼 식사', 15, '쉬움',
'1. 팬에 기름을 두르고 대파를 볶습니다.\n2. 계란을 넣고 스크램블합니다.\n3. 밥을 넣어 함께 볶습니다.\n4. 간장으로 간을 맞춥니다.'),
('김치볶음밥', '김치와 밥으로 만드는 대표적인 자취 요리', 15, '쉬움',
'1. 김치를 먹기 좋은 크기로 자릅니다.\n2. 팬에 김치를 볶습니다.\n3. 밥을 넣어 함께 볶습니다.\n4. 계란후라이를 올리면 완성입니다.'),
('두부계란전', '두부와 계란을 활용한 단백질 반찬', 20, '쉬움',
'1. 두부의 물기를 제거하고 먹기 좋게 자릅니다.\n2. 계란을 풀어 소금으로 간합니다.\n3. 두부에 계란물을 묻혀 팬에 앞뒤로 굽습니다.'),
('닭가슴살 샐러드', '가볍고 단백질이 풍부한 식사', 20, '쉬움',
'1. 닭가슴살을 익혀 먹기 좋게 자릅니다.\n2. 양상추와 토마토를 씻어 준비합니다.\n3. 모든 재료를 그릇에 담고 원하는 드레싱을 곁들입니다.'),
('감자계란국', '부드럽고 간단한 국 요리', 25, '보통',
'1. 감자를 얇게 썰어 물에 끓입니다.\n2. 감자가 익으면 대파를 넣습니다.\n3. 풀어둔 계란을 천천히 넣고 간을 맞춥니다.'),
('참치마요 덮밥', '전자레인지와 간단한 재료로 가능한 한 끼', 10, '쉬움',
'1. 참치의 기름을 제거합니다.\n2. 참치와 마요네즈를 섞습니다.\n3. 밥 위에 올리고 김가루를 뿌립니다.'),
('토마토 계란볶음', '토마토와 계란을 이용한 간단한 볶음요리', 15, '쉬움',
'1. 토마토를 먹기 좋게 자릅니다.\n2. 계란을 먼저 볶아 접시에 덜어둡니다.\n3. 토마토를 볶은 뒤 계란을 다시 넣어 섞습니다.'),
('소고기 양파볶음', '소고기와 양파를 달달하게 볶은 반찬', 20, '보통',
'1. 양파를 채 썹니다.\n2. 소고기를 팬에 볶습니다.\n3. 양파를 넣고 간장으로 간하여 함께 볶습니다.');

INSERT INTO recipe_ingredients (recipe_id, ingredient_name) VALUES
(1, '계란'), (1, '밥'), (1, '대파'), (1, '간장'),
(2, '김치'), (2, '밥'), (2, '계란'),
(3, '두부'), (3, '계란'),
(4, '닭가슴살'), (4, '양상추'), (4, '토마토'),
(5, '감자'), (5, '계란'), (5, '대파'),
(6, '참치'), (6, '밥'), (6, '마요네즈'), (6, '김'),
(7, '토마토'), (7, '계란'),
(8, '소고기'), (8, '양파'), (8, '간장');
