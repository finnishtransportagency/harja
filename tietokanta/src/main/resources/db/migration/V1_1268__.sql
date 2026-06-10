-- Lisätään sanktio-tauluun maaraystapa-kenttä tekstimuotoisena
ALTER TABLE sanktio
    ADD COLUMN IF NOT EXISTS "maaraystapa" text;
