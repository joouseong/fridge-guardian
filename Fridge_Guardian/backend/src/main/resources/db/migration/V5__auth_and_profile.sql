-- 소셜 로그인(Google/Kakao) 계정을 위해 이메일·비밀번호를 선택 값으로 바꾸고 로그인 제공자를 기록합니다.
ALTER TABLE users MODIFY COLUMN email VARCHAR(254) NULL;
ALTER TABLE users MODIFY COLUMN password VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN provider_id VARCHAR(100) NULL;
ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500) NULL;
ALTER TABLE users ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD CONSTRAINT uk_users_provider UNIQUE (provider, provider_id);

-- Refresh Token은 원문이 아니라 SHA-256 해시만 저장합니다. family_id로 탈취 후 재사용을 감지합니다.
CREATE TABLE refresh_tokens (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 token_hash VARCHAR(64) NOT NULL,
 family_id VARCHAR(36) NOT NULL,
 expires_at DATETIME NOT NULL,
 revoked_at DATETIME NULL,
 CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
 CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_family ON refresh_tokens(family_id);

-- 개인화 필터: 먹을 수 있는 최대 매운맛(0~3, 기본 3=제한 없음)과 알레르기·비선호 재료.
CREATE TABLE user_preferences (
 user_id BIGINT PRIMARY KEY,
 spicy_level INT NOT NULL DEFAULT 3,
 CONSTRAINT fk_preference_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT ck_spicy_level CHECK (spicy_level BETWEEN 0 AND 3)
);
CREATE TABLE excluded_items (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 item_type VARCHAR(20) NOT NULL,
 name VARCHAR(80) NOT NULL,
 CONSTRAINT fk_excluded_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT uk_excluded_item UNIQUE (user_id, item_type, name)
);
