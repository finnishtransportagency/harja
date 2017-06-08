ALTER TABLE vv_hinnoittelu_toimenpide
    DROP CONSTRAINT "vv_hinnoittelu_toimenpide_toimenpide-id_hinnoittelu-id_key";

CREATE UNIQUE INDEX uniikki_yhdistelma on vv_hinnoittelu_toimenpide ("toimenpide-id", "hinnoittelu-id") WHERE poistettu IS NOT TRUE;