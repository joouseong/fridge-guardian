-- Persist completed batch requests so a network retry cannot add the same stock twice.
CREATE TABLE ingredient_batch_requests (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 request_key VARCHAR(36) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 response_json LONGTEXT NOT NULL,
 CONSTRAINT fk_ingredient_batch_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT uk_ingredient_batch_request UNIQUE (user_id, request_key)
);
