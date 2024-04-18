CREATE TABLE lupaus_vaihtoehto_ryhma (
    id SERIAL PRIMARY KEY,
    "ryhma-otsikko" TEXT,
    luotu TIMESTAMP NOT NULL DEFAULT NOW(),
    muokattu TIMESTAMP
);

ALTER TABLE lupaus_vaihtoehto 
ADD "vaihtoehto-askel" INTEGER;
COMMENT ON column lupaus_vaihtoehto."vaihtoehto-askel" IS 'Arvo jonka perusteella vaihtoehdot jaotellaan erillisiin ryhmiin.';


ALTER TABLE lupaus_vaihtoehto 
ADD "vaihtoehto-seuraava-ryhma-id" INTEGER;
COMMENT ON column lupaus_vaihtoehto."vaihtoehto-seuraava-ryhma-id" IS 'Arvot koostuvat vaihtoehto-askel arvoista. Arvo määrittää mikä ryhmä tulisi näkyviin seuraavaksi.';

ALTER TABLE lupaus_vaihtoehto 
ADD "vaihtoehto-ryhma-otsikko-id" INTEGER REFERENCES lupaus_vaihtoehto_ryhma(id);
COMMENT ON column lupaus_vaihtoehto."vaihtoehto-ryhma-otsikko-id" IS 'Vaihtehtojen ryhmän otsikon viittaus.';


-- Päivitetty funktio jolla lisätään lupaukselle vaihtoehdot

CREATE OR REPLACE FUNCTION luo_lupauksen_vaihtoehto(
    lupauksen_jarjestys INTEGER,
    lupauksen_urakan_alkuvuosi INTEGER,
    vaihtoehto_str TEXT,
    pistemaara INTEGER,
    vaihtoehto_askel INTEGER,
    vaihtoehto_seuraava_ryhma_id INTEGER,
    vaihtoehto_ryhma_otsikko_id INTEGER)
    RETURNS VOID AS $$
BEGIN
    INSERT INTO lupaus_vaihtoehto ("lupaus-id", vaihtoehto, pisteet, "vaihtoehto-askel","vaihtoehto-seuraava-ryhma-id","vaihtoehto-ryhma-otsikko-id")
    VALUES ((SELECT id FROM lupaus WHERE "urakan-alkuvuosi" = lupauksen_urakan_alkuvuosi AND jarjestys = lupauksen_jarjestys),
   vaihtoehto_str, 
   pistemaara,
   vaihtoehto_askel,
   vaihtoehto_seuraava_ryhma_id,
   vaihtoehto_ryhma_otsikko_id);
 
END;
$$ LANGUAGE plpgsql;
