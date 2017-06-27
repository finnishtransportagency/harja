<<<<<<< HEAD
-- Toimenpideinstanssille VV-spesifistä tietoa
CREATE TABLE toimenpideinstanssi_vesivaylat (
  "toimenpideinstanssi-id" integer NOT NULL REFERENCES toimenpideinstanssi(id),
   vaylatyyppi VV_VAYLATYYPPI NOT NULL
);

CREATE INDEX toimenpideinstanssi_vesivaylat_index ON toimenpideinstanssi_vesivaylat ("toimenpideinstanssi-id");
=======
ALTER TABLE reimari_toimenpide DROP COLUMN "lisatyo";
>>>>>>> develop
