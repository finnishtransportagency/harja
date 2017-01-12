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

-- Migratoi olemassa olevien päällystysilmoitusten ilmoitustiedot-JSONista taloudellisen osan
-- tiedot uuteen tauluun

-- TODO KESKEN
CREATE OR REPLACE FUNCTION muunna_paallystysilmoitusten_maaramuutokset() RETURNS VOID AS
$BODY$
DECLARE
  rivi RECORD;
BEGIN
  FOR rivi IN SELECT
                paallystyskohde,
                luoja,
                json_array_elements((ilmoitustiedot->'tyot')::JSON) AS tyo
              FROM paallystysilmoitus
  LOOP
    INSERT INTO yllapitokohteen_maaramuutos (yllapitokohde,
                                             tyon_tyyppi,
                                             tyo,
                                             yksikko,
                                             tilattu_maara,
                                             toteutunut_maara,
                                             yksikkohinta,
                                             luoja)
    VALUES (rivi.paallystyskohde,
            'ajoradan_paallyste'::maaramuutos_tyon_tyyppi, -- TODO rivi.tyo->'tyyppi' JA MUUNTO ENUMIKSI. MITEN?
            rivi.tyo->'tyo',
            rivi.tyo->'yksikko',
            rivi.tyo->'tilattu-maara',
            rivi.tyo->'toteutunut-maara',
            rivi.tyo->'yksikkohinta',
            rivi.luoja);
  END LOOP;
  RETURN;
END
$BODY$
LANGUAGE 'plpgsql';

SELECT * FROM muunna_paallystysilmoitusten_maaramuutokset();
DROP FUNCTION muunna_paallystysilmoitusten_maaramuutokset(); -- Ei tarvi tehdä kuin kerran

-- Poista olemassa olevista päällystysilmoituksista taloustiedot, jotta data
-- on uuden skeeman mukainen

-- TODO