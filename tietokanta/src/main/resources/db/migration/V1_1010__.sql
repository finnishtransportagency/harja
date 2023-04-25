-- VHAR-7600
-- Valaistusurakan tunnistamisessa käytettävän urakkakoodin lähdettä ja muotoa kaavaillaan muutettavaksi.
-- Urakkakoodi saadaan Samposta urakan tietojen mukana vv_alueurakkanro-kentässä.
-- Koodi voi olla jatkossa sama kuin urakan sampo-id. Kasvatetaan urakkanro-sarakkeen pituutta 32:een,
-- vastaamaan urakka.sampo_id-kentän pituutta.

ALTER TABLE urakka
    ALTER COLUMN urakkanro TYPE VARCHAR(32);

ALTER TABLE alueurakka
    ALTER COLUMN alueurakkanro TYPE VARCHAR(32);

ALTER TABLE valaistusurakka
    ALTER COLUMN alueurakkanro TYPE VARCHAR(32),
    ALTER COLUMN valaistusurakkanro TYPE VARCHAR(32);

ALTER TABLE paallystyspalvelusopimus
    ALTER COLUMN alueurakkanro TYPE VARCHAR(32),
    ALTER COLUMN paallystyspalvelusopimusnro TYPE VARCHAR(32);

ALTER TABLE tekniset_laitteet_urakka
    ALTER COLUMN urakkanro TYPE VARCHAR(32);

ALTER TABLE siltapalvelusopimus
    ALTER COLUMN urakkanro TYPE VARCHAR(32);

ALTER TABLE analytiikka_toteumat
    ALTER COLUMN toteuma_alueurakkanumero TYPE VARCHAR(32);
