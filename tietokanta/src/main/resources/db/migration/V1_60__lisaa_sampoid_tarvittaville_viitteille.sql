ALTER TABLE urakka ADD COLUMN hanke_sampoid varchar(16);
ALTER TABLE toimenpideinstanssi ADD COLUMN urakka_sampoid varchar(16);
ALTER TABLE sopimus ADD COLUMN urakka_sampoid varchar(16);
ALTER TABLE sopimus ALTER sampoid DROP NOT NULL;
