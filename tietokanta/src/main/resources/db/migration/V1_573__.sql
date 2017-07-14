-- Korjaa virheelliset uniikkius-säännöt
ALTER TABLE vv_hinnoittelu DROP CONSTRAINT "vv_hinnoittelu_urakka-id_nimi_key";
CREATE UNIQUE INDEX vv_hinnoittelu_uniikki_yhdistelma on vv_hinnoittelu ("urakka-id", nimi) WHERE poistettu IS NOT TRUE;

ALTER INDEX uniikki_yhdistelma RENAME TO vv_hinnoittelu_toimenpide_uniikki_yhdistelma