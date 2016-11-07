ALTER TABLE suolasakko ADD COLUMN vainsakkomaara NUMERIC DEFAULT NULL;

ALTER TYPE hk_suolasakko ADD ATTRIBUTE vainsakkomaara NUMERIC;
ALTER TYPE hk_suolasakko ADD ATTRIBUTE suolankayton_bonusraja NUMERIC;
