<<<<<<< HEAD
-- Irrota päällystyslomakkeen määrämuutokset omaan tauluun

CREATE TYPE maaramuutos_tyon_tyyppi AS ENUM ('ajoradan_paallyste', 'pienaluetyot', 'tasaukset', 'jyrsinnat',
                                              'muut');

CREATE TABLE yllapitokohteen_maaramuutos (
  id serial PRIMARY KEY,
  yllapitokohde INTEGER REFERENCES yllapitokohde (id) NOT NULL,
  tyon_tyyppi maaramuutos_tyon_tyyppi NOT NULL,
  tyo VARCHAR(256) NOT NULL,
  yksikko VARCHAR(32) NOT NULL,
  tilattu_maara NUMERIC NOT NULL,
  toteutunut_maara NUMERIC NOT NULL,
  yksikkohinta NUMERIC NOT NULL,
  poistettu boolean DEFAULT FALSE NOT NULL,
  luoja INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu TIMESTAMP DEFAULT NOW()  NOT NULL,
  muokkaaja INTEGER REFERENCES kayttaja (id),
  muokattu TIMESTAMP
);

ALTER TABLE paallystysilmoitus DROP COLUMN muutoshinta; -- Lasketaan jatkossa yllä olevasta taulusta
ALTER TABLE paallystysilmoitus DROP COLUMN paatos_taloudellinen_osa; -- Hinnanmuutosten hyväksyminen jää pois (HAR-4090)
ALTER TABLE paallystysilmoitus DROP COLUMN perustelu_taloudellinen_osa;
ALTER TABLE paallystysilmoitus DROP COLUMN kasittelyaika_taloudellinen_osa;
ALTER TABLE paallystysilmoitus DROP COLUMN asiatarkastus_taloudellinen_osa;

-- FIXME TÄSSÄ VAIHEESSA NYKYISTEN POTTIEN ilmoitustiedot-SARAKKEESEEN JÄÄ VANHANMALLINEN JSON, JOSSA
-- TALOUSOSA MUKANA. MITEN MIGRATOIDAAN ilmoitustiedot-JSON->yllapito_maaramuutokset?
=======
-- Urakan kannalta olennaiset sanktiotyypit riippuvat urakkatyypistä
-- Urakan kannalta olennaiset sanktiotyypit riippuvat urakkatyypistä
ALTER TABLE sanktiotyyppi
  ADD COLUMN urakkatyyppi urakkatyyppi[] NOT NULL DEFAULT ARRAY['hoito']::urakkatyyppi[];
ALTER TABLE sanktiotyyppi RENAME COLUMN sanktiolaji TO _sanklaji;
ALTER TABLE sanktio RENAME COLUMN sakkoryhma TO _sakkoryhma;

ALTER TYPE sanktiolaji RENAME TO _sanklaji;
CREATE TYPE sanktiolaji AS ENUM ('A', 'B', 'C', 'muistutus', 'yllapidon_sakko', 'yllapidon_bonus', 'yllapidon_muistutus');
ALTER TABLE sanktiotyyppi ADD COLUMN sanktiolaji sanktiolaji[];
ALTER TABLE sanktio ADD COLUMN sakkoryhma sanktiolaji;

UPDATE sanktiotyyppi SET sanktiolaji = _sanklaji::varchar[]::sanktiolaji[];
UPDATE sanktio SET sakkoryhma = _sakkoryhma::text::sanktiolaji;
ALTER TABLE sanktiotyyppi DROP COLUMN _sanklaji;

-- Myös trigger päivittyi käyttämään _sakkoryhma saraketta, sekin päivitettävä
DROP TRIGGER tg_poista_muistetut_laskutusyht_sanktio ON sanktio;

CREATE TRIGGER tg_poista_muistetut_laskutusyht_sanktio
AFTER INSERT OR UPDATE
  ON sanktio
FOR EACH ROW
WHEN (NEW.sakkoryhma NOT IN ('muistutus', 'yllapidon_sakko', 'yllapidon_bonus', 'yllapidon_muistutus'))
EXECUTE PROCEDURE poista_muistetut_laskutusyht_sanktio();


ALTER TABLE sanktio DROP COLUMN _sakkoryhma;

DROP TYPE _sanklaji;


INSERT INTO sanktiotyyppi(nimi, sanktiolaji, urakkatyyppi)
VALUES
  ('Ylläpidon sakko', ARRAY['yllapidon_sakko'::sanktiolaji],
   ARRAY['paallystys', 'paikkaus', 'tiemerkinta', 'valaistus']::urakkatyyppi[]),
  ('Ylläpidon bonus', ARRAY['yllapidon_bonus'::sanktiolaji],
   ARRAY['paallystys', 'paikkaus', 'tiemerkinta', 'valaistus']::urakkatyyppi[]),
  ('Ylläpidon muistutus', ARRAY['yllapidon_muistutus'::sanktiolaji],
   ARRAY['paallystys', 'paikkaus', 'tiemerkinta', 'valaistus']::urakkatyyppi[]);
>>>>>>> develop
