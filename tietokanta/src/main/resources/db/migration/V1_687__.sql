CREATE TABLE paikkauskohde (
  id            SERIAL PRIMARY KEY,
  "luoja-id"    INTEGER REFERENCES kayttaja (id),
  "ulkoinen-id" INTEGER NOT NULL,
  "nimi"        TEXT    NOT NULL,
  CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja
  UNIQUE ("ulkoinen-id", "luoja-id")
);

CREATE TABLE paikkaustoteuma (
  id                 SERIAL PRIMARY KEY,
  "luoja-id"         INTEGER REFERENCES kayttaja (id),
  luotu              TIMESTAMP DEFAULT NOW(),
  "muokkaaja-id"     INTEGER REFERENCES kayttaja (id),
  muokattu           TIMESTAMP,
  "poistaja-id"      INTEGER REFERENCES kayttaja (id),
  poistettu          BOOLEAN                                     NOT NULL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            DEFAULT FALSE,

  "urakka-id"        INTEGER REFERENCES urakka (id)              NOT NULL,
  "paikkauskohde-id" INTEGER REFERENCES paikkauskohde (id)       NOT NULL,
  "ulkoinen-id"      INTEGER                                     NOT NULL,

  alkuaika           TIMESTAMP                                   NOT NULL,
  loppuaika          TIMESTAMP                                   NOT NULL,

  tierekisteriosoite TR_OSOITE                                   NOT NULL,

  tyomenetelma       TEXT                                        NOT NULL,
  massatyyppi        TEXT                                        NOT NULL,
  leveys             DECIMAL,
  massamenekki       INTEGER,
  raekoko            INTEGER,
  kuulamylly         TEXT,

  CONSTRAINT paikkaustoteuman_uniikki_ulkoinen_id_luoja_urakka
  UNIQUE ("ulkoinen-id", "luoja-id", "urakka-id")
);

CREATE TABLE paikkauksen_tienkohta (
  id                   SERIAL PRIMARY KEY,
  "paikkaustoteuma-id" INTEGER REFERENCES paikkaustoteuma (id),
  ajorata              INTEGER,
  reunat               INTEGER [],
  ajourat              INTEGER [],
  ajouravalit          INTEGER [],
  keskisaumat          INTEGER []
);

CREATE TABLE paikkauksen_materiaalit (
  id                   SERIAL PRIMARY KEY,
  "paikkaustoteuma-id" INTEGER REFERENCES paikkaustoteuma (id),
  esiintyma            TEXT,
  "kuulamylly-arvo"    TEXT,
  muotoarvo            TEXT,
  sideainetyyppi       TEXT,
  pitoisuus            DECIMAL,
  "lisa-aineet"        TEXT
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-paikkaustoteuma');

CREATE TABLE api_tyojono (
  id                SERIAL PRIMARY KEY,
  "tapahtuman-nimi" TEXT                    NOT NULL,
  luotu             TIMESTAMP DEFAULT NOW() NOT NULL,
  valmistunut       TIMESTAMP,
  sisalto           TEXT,
  onnistunut        BOOLEAN
);

CREATE OR REPLACE FUNCTION uusi_tapahtuma_api_tyojonossa()
  RETURNS TRIGGER AS $$
DECLARE
  id BIGINT;
BEGIN
  IF TG_OP = 'INSERT' OR TG_OP = 'UPDATE'
  THEN
    id = NEW.id;
  ELSE
    id = OLD.id;
  END IF;
  PERFORM pg_notify('api_tyojono', json_build_object('tapahtuman-nimi', NEW."tapahtuman-nimi", 'id', id) :: TEXT);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_uusi_tapahtuma_api_tyojonossa
AFTER INSERT OR UPDATE
  ON api_tyojono
FOR EACH ROW
EXECUTE PROCEDURE uusi_tapahtuma_api_tyojonossa();