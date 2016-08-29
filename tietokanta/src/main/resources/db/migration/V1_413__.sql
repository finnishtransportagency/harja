-- Lisää indeksit_kaytossa kenttä
ALTER TABLE urakka ADD COLUMN indeksit_kaytossa BOOLEAN NOT NULL DEFAULT true;
