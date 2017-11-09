CREATE TYPE LIIKENNETAPAHTUMA_TOIMENPIDETYYPPI AS ENUM ('sulutus', 'tyhjennys', 'sillan-avaus');
CREATE TYPE LIIKENNETAPAHTUMA_PALVELUMUOTO AS ENUM ('kauko', 'itse', 'paikallis', 'muu');

CREATE TABLE kan_liikennetapahtuma (
  id                  SERIAL PRIMARY KEY,
  "kohde-id"          INTEGER REFERENCES kan_kohde (id),
  aika                TIMESTAMP                          NOT NULL,
  toimenpide          LIIKENNETAPAHTUMA_TOIMENPIDETYYPPI NOT NULL,
  palvelumuoto        LIIKENNETAPAHTUMA_PALVELUMUOTO,
  "palvelumuoto-lkm"  INTEGER
    CONSTRAINT vain_itsepalvelua_enemman_kuin_yksi CHECK
    ("palvelumuoto-lkm" = 1 OR palvelumuoto = 'itse' OR palvelumuoto IS NULL),
  lisatieto           TEXT,
  "vesipinta-ylaraja" INTEGER,
  "vesipinta-alaraja" INTEGER,

  "kuittaaja-id"           INTEGER REFERENCES kayttaja (id),

  luotu               TIMESTAMP DEFAULT NOW(),
  luoja               INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu            TIMESTAMP,
  muokkaaja           INTEGER REFERENCES kayttaja (id),
  poistettu           BOOLEAN   DEFAULT FALSE,
  poistaja            INTEGER REFERENCES kayttaja (id)
);

CREATE OR REPLACE FUNCTION toimenpide_kohteen_mukaan_proc()
  RETURNS TRIGGER AS
$$
DECLARE kohteen_tyyppi KOHTEEN_TYYPPI;
BEGIN
  kohteen_tyyppi := (SELECT kan_kohde.tyyppi
                     FROM kan_kohde
                     WHERE id = NEW."kohde-id");
  IF ((NEW.toimenpide = 'sillan-avaus'
       AND
       (kohteen_tyyppi IN ('silta', 'sulku-ja-silta'))
       AND (NEW.palvelumuoto IS NULL))
      OR
      (NEW.toimenpide IN ('sulutus', 'tyhjennys')
       AND
       (kohteen_tyyppi IN ('sulku', 'sulku-ja-silta'))
       AND (NEW.palvelumuoto IS NOT NULL)))
  THEN
    RETURN NEW;
  ELSE
    RAISE EXCEPTION 'Liikennetapahtuman toimenpide %, % ei vastaa kohteen tyyppiä %', NEW.toimenpide, NEW.palvelumuoto, kohteen_tyyppi;
    RETURN NULL;
  END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS toimenpide_kohteen_mukaan_trigger
ON kan_liikennetapahtuma;

CREATE TRIGGER toimenpide_kohteen_mukaan_trigger
BEFORE INSERT OR UPDATE ON kan_liikennetapahtuma
FOR EACH ROW
EXECUTE PROCEDURE toimenpide_kohteen_mukaan_proc();

CREATE TYPE LIIKENNETAPAHTUMA_SUUNTA AS ENUM ('ylös', 'alas');
CREATE TYPE LIIKENNETAPAHTUMA_ALUSLAJI AS ENUM ('RAH', 'MAT', 'ÖLJ', 'HINT', 'HUV', 'PRO', 'SEK', 'LAU');

CREATE TABLE kan_liikennetapahtuma_alus (
  id                     SERIAL PRIMARY KEY,
  "liikennetapahtuma-id" INTEGER REFERENCES kan_liikennetapahtuma (id),
  nimi                   TEXT,
  laji                   LIIKENNETAPAHTUMA_ALUSLAJI         NOT NULL,
  lkm                    INTEGER                            NOT NULL
    CONSTRAINT vain_huviveneita_enemman_kuin_yksi CHECK (lkm = 1 OR laji = 'HUV'),
  matkustajalkm          INTEGER,
  suunta                 LIIKENNETAPAHTUMA_SUUNTA           NOT NULL,

  luotu                  TIMESTAMP DEFAULT NOW(),
  luoja                  INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu               TIMESTAMP,
  muokkaaja              INTEGER REFERENCES kayttaja (id),
  poistettu              BOOLEAN   DEFAULT FALSE,
  poistaja               INTEGER REFERENCES kayttaja (id)
);

CREATE TABLE kan_liikennetapahtuma_nippu (
  id                     SERIAL PRIMARY KEY,
  "liikennetapahtuma-id" INTEGER REFERENCES kan_liikennetapahtuma (id),
  lkm                    INTEGER                            NOT NULL,
  suunta                 LIIKENNETAPAHTUMA_SUUNTA           NOT NULL,

  luotu                  TIMESTAMP DEFAULT NOW(),
  luoja                  INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu               TIMESTAMP,
  muokkaaja              INTEGER REFERENCES kayttaja (id),
  poistettu              BOOLEAN   DEFAULT FALSE,
  poistaja               INTEGER REFERENCES kayttaja (id)
);