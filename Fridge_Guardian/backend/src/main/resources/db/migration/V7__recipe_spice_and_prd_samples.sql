-- 매운맛 단계(0=순한맛 ~ 3=아주 매움). 개인화 필터가 사용자의 선호와 비교합니다.
ALTER TABLE recipes ADD COLUMN spicy_level INT NOT NULL DEFAULT 0;
UPDATE recipes SET spicy_level = 2 WHERE id = 1;
UPDATE recipes SET spicy_level = 1 WHERE id IN (2, 8, 12);

-- PRD의 기본 제공 샘플 레시피 8종 이름에 맞춥니다. (기존 4번·9번은 같은 요리이므로 이름만 정리)
UPDATE recipes SET name = '계란볶음밥' WHERE id = 4;
UPDATE recipes SET name = '토마토 계란볶음' WHERE id = 9;

-- PRD 샘플 중 아직 없던 5종. freshfridge_capstone 프로토타입의 조리 순서를 가져와 수량·영양값을 추가했습니다.
-- 수량은 2인분 기준, 영양값은 1인분당 예시 추정치이며 공인 식품 영양 DB의 값이 아닙니다.
INSERT INTO recipes(id,name,description,cooking_time,difficulty,servings,instructions,calories,protein,carbs,fat,spicy_level) VALUES
(17,'두부계란전','두부와 계란을 활용한 단백질 반찬',20,'EASY',2,'두부의 물기를 키친타월로 제거하고 먹기 좋게 썹니다.
계란을 풀어 소금으로 간합니다.
두부에 계란물을 묻혀 기름 두른 팬에 앞뒤로 노릇하게 굽습니다.
두부 속까지 뜨겁고 계란이 충분히 익었는지 확인합니다.',260,18,6,18,0),
(18,'닭가슴살 샐러드','가볍고 단백질이 풍부한 식사',20,'EASY',2,'닭가슴살을 속까지 충분히 익혀 식힌 뒤 먹기 좋게 찢거나 썹니다.
양상추와 토마토를 씻어 먹기 좋은 크기로 준비합니다.
모든 재료를 그릇에 담고 원하는 드레싱을 곁들입니다.',240,32,10,8,0),
(19,'감자계란국','부드럽고 간단한 국 요리',25,'MEDIUM',2,'감자를 얇게 썰어 조리용 물 600ml에 넣고 끓입니다.
감자가 익으면 잘게 썬 대파를 넣습니다.
풀어 둔 계란을 천천히 두르고 소금으로 간을 맞춘 뒤 계란이 익을 때까지 끓입니다.',180,9,24,5,0),
(20,'참치마요 덮밥','전자레인지와 간단한 재료로 가능한 한 끼',10,'EASY',2,'참치의 기름을 충분히 뺍니다.
참치와 마요네즈를 고루 섞습니다.
밥 위에 올리고 김을 잘게 부숴 뿌립니다.',590,26,78,18,0),
(21,'소고기 양파볶음','소고기와 양파를 달달하게 볶은 반찬',20,'MEDIUM',2,'양파를 채 썹니다.
팬에 소고기를 볶아 겉면이 익으면 양파를 넣습니다.
간장으로 간을 하고 고기가 속까지 충분히 익도록 함께 볶습니다.',340,27,10,21,0);

INSERT INTO recipe_ingredients(recipe_id,ingredient_name,required_quantity,unit) VALUES
(17,'두부',1,'BLOCK'),
(17,'계란',3,'PIECE'),
(17,'소금',2,'GRAM'),
(17,'식용유',15,'MILLILITER'),
(18,'닭가슴살',200,'GRAM'),
(18,'양상추',100,'GRAM'),
(18,'토마토',2,'PIECE'),
(19,'감자',200,'GRAM'),
(19,'계란',2,'PIECE'),
(19,'대파',0.25,'BUNCH'),
(19,'소금',2,'GRAM'),
(20,'참치',150,'GRAM'),
(20,'밥',400,'GRAM'),
(20,'마요네즈',30,'GRAM'),
(20,'김',2,'GRAM'),
(21,'소고기',250,'GRAM'),
(21,'양파',1,'PIECE'),
(21,'간장',20,'MILLILITER');
