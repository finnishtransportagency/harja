-- Lisää vv_hinnoittelu taululle ryhmittelytieto.
-- Tämä on tarkoitettu pääasiassa frontille, jotta hinnat voidaan näyttää oikeiden otsikoiden alla
-- Frontin tulee jatkossa vv_hintaa lähetettäessään kertoa ryhmä

CREATE TYPE vv_hinta_ryhma AS ENUM ('tyo', 'komponentti', 'muu');
ALTER TABLE vv_hinta ADD COLUMN ryhma vv_hinta_ryhma;

-- TODO ID-linkkaus ei toimi näin
--CREATE TABLE vv_hinta_turvalaitekomponentti
--(
--  "hinta-id"  INTEGER REFERENCES vv_hinta (id),
--  "turvalaitekomponentti-id" INTEGER REFERENCES reimari_turvalaitekomponentti (id),
--  UNIQUE ("hinta-id", "turvalaitekomponentti-id"),

--  muokkaaja  INTEGER REFERENCES kayttaja (id),
--  muokattu   TIMESTAMP,
--  luoja      INTEGER REFERENCES kayttaja (id) NOT NULL,
--  luotu      TIMESTAMP                        NOT NULL DEFAULT NOW(),
--  poistettu  BOOLEAN                          NOT NULL DEFAULT FALSE,
--  poistaja   INTEGER REFERENCES kayttaja (id)
--);
