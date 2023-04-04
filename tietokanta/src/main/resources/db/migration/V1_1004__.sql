ALTER TABLE lampotilat RENAME COLUMN pitka_keskilampotila TO keskilampotila_1981_2010;
ALTER TABLE lampotilat RENAME COLUMN pitka_keskilampotila_vanha TO keskilampotila_1971_2000;
ALTER TABLE lampotilat ADD COLUMN keskilampotila_1991_2020 NUMERIC(4,2);
