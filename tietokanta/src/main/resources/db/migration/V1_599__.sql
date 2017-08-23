-- Lisää vv_hinnoittelu taululle ryhmittelytieto.

-- Tämä on tarkoitettu pääasiassa frontille, jotta hinnat voidaan näyttää oikeiden otsikoiden alla
-- Frontin tulee jatkossa vv_hintaa lähetettäessään kertoa ryhmä
CREATE TYPE vv_hinta_ryhma AS ENUM ('tyo', 'komponentti', 'muu');
ALTER TABLE vv_hinta ADD COLUMN ryhma vv_hinta_ryhma;

-- Lisää olemassa olevan hinnat ryhmiin
UPDATE vv_hinta SET ryhma = 'muu' WHERE otsikko != 'Päivän hinta' AND otsikko != 'Omakustannushinta';
UPDATE vv_hinta SET ryhma = 'tyo' WHERE otsikko = 'Päivän hinta' OR otsikko = 'Omakustannushinta';

ALTER TABLE vv_hinta ALTER COLUMN ryhma SET NOT NULL;

CREATE TABLE vv_hinta_turvalaitekomponentti
(
  "hinta-id"  INTEGER REFERENCES vv_hinta (id),
  "turvalaitekomponentti-id" TEXT REFERENCES reimari_turvalaitekomponentti (id),
  UNIQUE ("hinta-id", "turvalaitekomponentti-id"),

  muokkaaja  INTEGER REFERENCES kayttaja (id),
  muokattu   TIMESTAMP,
  luoja      INTEGER REFERENCES kayttaja (id) NOT NULL,
  luotu      TIMESTAMP                        NOT NULL DEFAULT NOW(),
  poistettu  BOOLEAN                          NOT NULL DEFAULT FALSE,
  poistaja   INTEGER REFERENCES kayttaja (id)
);
