<<<<<<< HEAD
-- Tarkastusajolta pois tyyppi, ei enää käytössä
ALTER TABLE tarkastusajo DROP COLUMN tyyppi;
=======
ALTER TABLE suolasakko ADD COLUMN vainsakkomaara NUMERIC DEFAULT NULL;

ALTER TYPE hk_suolasakko ADD ATTRIBUTE vainsakkomaara NUMERIC;
ALTER TYPE hk_suolasakko ADD ATTRIBUTE suolankayton_bonusraja NUMERIC;
>>>>>>> develop
