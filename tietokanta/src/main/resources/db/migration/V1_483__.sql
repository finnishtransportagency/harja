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
            CASE
              WHEN (rivi.tyo->>'tyyppi')='ajoradan-paallyste' THEN 'ajoradan_paallyste'::maaramuutos_tyon_tyyppi
              ELSE (rivi.tyo->>'tyyppi')::maaramuutos_tyon_tyyppi -- Kaikki muut voi käsitellä sellaisenaan
            END,
            rivi.tyo->>'tyo',
            rivi.tyo->>'yksikko',
            (rivi.tyo->>'tilattu-maara')::NUMERIC,
            (rivi.tyo->>'toteutunut-maara')::NUMERIC,
            (rivi.tyo->>'yksikkohinta')::NUMERIC,
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

-- Perustuu: http://stackoverflow.com/questions/23490965/postgresql-remove-attribute-from-json-column
CREATE OR REPLACE FUNCTION "poista_json_avaimet"("json_data" json, VARIADIC "avaimet" TEXT[])
  RETURNS json
LANGUAGE sql
IMMUTABLE
STRICT
AS $function$
SELECT COALESCE(
    (SELECT ('{' || string_agg(to_json("key") || ':' || "value", ',') || '}')
     FROM json_each("json_data")
     WHERE "key" <> ALL ("avaimet")),
    '{}'
)::json
$function$;

UPDATE paallystysilmoitus
SET ilmoitustiedot = poista_json_avaimet(ilmoitustiedot::JSON, 'tyot');

DROP FUNCTION poista_json_avaimet(json, TEXT[]);