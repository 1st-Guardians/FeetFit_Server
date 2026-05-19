-- Existing local MySQL tables may still have NOT NULL constraints from the old User entity.
-- Run this once against the feetfit database to allow Kakao users to be created
-- before onboarding profile fields are filled.
ALTER TABLE users MODIFY COLUMN age INT NULL;
ALTER TABLE users MODIFY COLUMN height_cm FLOAT NULL;
ALTER TABLE users MODIFY COLUMN weight_kg FLOAT NULL;
ALTER TABLE users MODIFY COLUMN gender ENUM('FEMALE', 'MALE') NULL;
