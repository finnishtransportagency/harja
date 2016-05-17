-- Toteumalla pakko olla sopimus ja urakka
ALTER TABLE toteuma ALTER COLUMN urakka SET NOT NULL;
ALTER TABLE toteuma ALTER COLUMN sopimus SET NOT NULL;