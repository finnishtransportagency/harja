<<<<<<< HEAD
-- Muuta "Matkat" hinnan nimeksi "Matkaulut"
UPDATE vv_hinta SET otsikko = 'Matkakulut' WHERE otsikko = 'Matkat';
=======
-- Toimenpideinstanssille VV-spesifistä tietoa
CREATE TABLE toimenpideinstanssi_vesivaylat (
  "toimenpideinstanssi-id" integer NOT NULL REFERENCES toimenpideinstanssi(id) UNIQUE,
  vaylatyyppi VV_VAYLATYYPPI NOT NULL
);

CREATE INDEX toimenpideinstanssi_vesivaylat_index ON toimenpideinstanssi_vesivaylat ("toimenpideinstanssi-id");
>>>>>>> develop
