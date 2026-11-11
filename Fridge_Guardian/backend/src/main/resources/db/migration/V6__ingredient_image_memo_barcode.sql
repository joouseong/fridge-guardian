-- 식재료 사진(서버가 만든 파일명만 저장), 메모, 바코드.
ALTER TABLE ingredients ADD COLUMN image_path VARCHAR(255) NULL;
ALTER TABLE ingredients ADD COLUMN memo VARCHAR(500) NULL;
ALTER TABLE ingredients ADD COLUMN barcode VARCHAR(20) NULL;

-- 바코드 조회 결과 캐시. 외부 API를 다시 호출하지 않도록 저장합니다.
CREATE TABLE barcode_products (
 barcode VARCHAR(20) PRIMARY KEY,
 product_name VARCHAR(200) NOT NULL,
 category VARCHAR(20) NULL,
 shelf_life_days INT NULL,
 source VARCHAR(30) NOT NULL,
 fetched_at DATETIME NOT NULL
);
