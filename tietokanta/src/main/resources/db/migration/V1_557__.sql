ALTER TABLE vv_hinnoittelu_toimenpide
  DROP CONSTRAINT "vv_hinnoittelu_toimenpide_toimenpide-id_hinnoittelu-id_key";

CREATE UNIQUE INDEX uniikki_yhdistelma on vv_hinnoittelu_toimenpide ("toimenpide-id", "hinnoittelu-id") WHERE poistettu IS NOT TRUE;

-- Oletettavasti hinnan otsikon pitää olla uniikki per hinnoittelu
CREATE UNIQUE INDEX uniikki_hinta on vv_hinta ("hinnoittelu-id", otsikko) WHERE poistettu IS NOT TRUE;

-- Hinnalla on pakko olla hinnoittelu
ALTER TABLE vv_hinta ALTER COLUMN "hinnoittelu-id" SET NOT NULL;

-- Toimenpiteen reimari-id uniikiksi
ALTER TABLE reimari_toimenpide ADD CONSTRAINT "uniikki_reimari-id" UNIQUE ("reimari-id");
