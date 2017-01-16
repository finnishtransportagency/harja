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