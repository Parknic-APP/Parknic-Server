-- 도감의 업적 배지에 들어가는 짧은 글자
ALTER TABLE achievements
    ADD COLUMN label VARCHAR(10);

UPDATE achievements SET label = '첫' WHERE code = 'FIRST_BADGE';
UPDATE achievements SET label = '10' WHERE code = 'PARKS_10';
UPDATE achievements SET label = '20' WHERE code = 'PARKS_20';
UPDATE achievements SET label = '50' WHERE code = 'PARKS_50';
UPDATE achievements SET label = '100' WHERE code = 'PARKS_100';
UPDATE achievements SET label = '7일' WHERE code = 'STREAK_7';

ALTER TABLE achievements
    ALTER COLUMN label SET NOT NULL;
