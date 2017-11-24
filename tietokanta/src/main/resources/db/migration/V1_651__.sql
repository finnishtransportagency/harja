-- Poistetaan vanha data + taulut
TRUNCATE kan_kanava CASCADE;
ALTER TABLE kan_hairio DROP COLUMN kohde;
ALTER TABLE kan_toimenpide DROP COLUMN kohde;
ALTER TABLE kan_liikennetapahtuma DROP COLUMN "kohde-id";

DROP TABLE kan_kohde_urakka;
DROP TABLE kan_kohde;
DROP TABLE kan_kanava;


CREATE TABLE kan_kohdekokonaisuus (
  id SERIAL PRIMARY KEY,
  nimi TEXT NOT NULL,
  sijainti GEOMETRY,

  luotu               TIMESTAMP DEFAULT NOW(),
  luoja               INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu            TIMESTAMP,
  muokkaaja           INTEGER REFERENCES kayttaja (id),
  poistettu           BOOLEAN   DEFAULT FALSE,
  poistaja            INTEGER REFERENCES kayttaja (id)
);

CREATE TABLE kan_kohde (
  id SERIAL PRIMARY KEY,
  "kohdekokonaisuus-id" INTEGER REFERENCES kan_kohdekokonaisuus NOT NULL,
  nimi TEXT NOT NULL,
  sijainti GEOMETRY,
  "ylos-id" INTEGER REFERENCES kan_kohde(id),
  "alas-id" INTEGER REFERENCES kan_kohde(id),

  luotu               TIMESTAMP DEFAULT NOW(),
  luoja               INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu            TIMESTAMP,
  muokkaaja           INTEGER REFERENCES kayttaja (id),
  poistettu           BOOLEAN   DEFAULT FALSE,
  poistaja            INTEGER REFERENCES kayttaja (id)
);

CREATE TABLE kan_kohde_urakka (
  "urakka-id" INTEGER REFERENCES urakka(id),
  "kohde-id" INTEGER REFERENCES kan_kohde(id),

  luotu               TIMESTAMP DEFAULT NOW(),
  luoja               INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu            TIMESTAMP,
  muokkaaja           INTEGER REFERENCES kayttaja (id),
  poistettu           BOOLEAN   DEFAULT FALSE,
  poistaja            INTEGER REFERENCES kayttaja (id)
);

CREATE TYPE KOHTEENOSA_TYYPPI AS ENUM ('sulku', 'silta', 'rautatiesilta');

CREATE TABLE kan_kohteenosa(
  id SERIAL PRIMARY KEY,
  tyyppi KOHTEENOSA_TYYPPI NOT NULL,
  nimi TEXT,
  oletuspalvelumuoto liikennetapahtuma_palvelumuoto,
  "kohde-id" INTEGER REFERENCES kan_kohde(id) NOT NULL,

  luotu               TIMESTAMP DEFAULT NOW(),
  luoja               INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu            TIMESTAMP,
  muokkaaja           INTEGER REFERENCES kayttaja (id),
  poistettu           BOOLEAN   DEFAULT FALSE,
  poistaja            INTEGER REFERENCES kayttaja (id)
);

ALTER TABLE kan_hairio
  ADD COLUMN "kohde-id" INTEGER REFERENCES kan_kohde (id),
  ADD COLUMN "kohteenosa-id" INTEGER REFERENCES kan_kohteenosa (id),
  ADD COLUMN poistaja INTEGER REFERENCES kayttaja (id);

ALTER TABLE kan_toimenpide
  ADD COLUMN "kohde-id" INTEGER REFERENCES kan_kohde(id),
  ADD COLUMN "kohteenosa-id" INTEGER REFERENCES kan_kohteenosa(id);

-- Katso R__osa_kuuluu_kohteeseen.sql

ALTER TABLE kan_liikennetapahtuma
  ADD COLUMN "kohde-id" INTEGER REFERENCES kan_kohde (id),
  DROP COLUMN palvelumuoto,
  DROP COLUMN "palvelumuoto-lkm",
  DROP COLUMN "sulku-toimenpide",
  DROP COLUMN "sulku-palvelumuoto",
  DROP COLUMN "sulku-lkm",
  DROP COLUMN "silta-avaus",
  DROP COLUMN "silta-palvelumuoto",
  DROP COLUMN "silta-lkm";

DROP TYPE SULUTUS_TOIMENPIDETYYPPI;

CREATE TYPE liikennetapahtuma_toimenpidetyyppi AS ENUM ('sulutus', 'tyhjennys', 'avaus', 'ei-avausta');

CREATE TABLE kan_liikennetapahtuma_osa(
  id SERIAL PRIMARY KEY,
  "kohde-id" INTEGER REFERENCES kan_kohde(id) NOT NULL,
  "kohteenosa-id" INTEGER REFERENCES kan_kohteenosa(id) NOT NULL,
  "liikennetapahtuma-id" INTEGER REFERENCES kan_liikennetapahtuma(id) NOT NULL,
  toimenpide liikennetapahtuma_toimenpidetyyppi NOT NULL,
  palvelumuoto liikennetapahtuma_palvelumuoto
  CONSTRAINT avaamattomalla_sillalla_ei_palvelumuotoa CHECK
  ((palvelumuoto IS NOT NULL AND toimenpide != 'ei-avausta') OR (palvelumuoto IS NULL AND toimenpide = 'ei-avausta')),
  lkm INTEGER
  CONSTRAINT vain_itsepalvelua_enemman_kuin_yksi CHECK
  (lkm = 1 OR palvelumuoto = 'itse' OR toimenpide = 'ei-avausta'),

  luotu               TIMESTAMP DEFAULT NOW(),
  luoja               INTEGER REFERENCES kayttaja (id)   NOT NULL,
  muokattu            TIMESTAMP,
  muokkaaja           INTEGER REFERENCES kayttaja (id),
  poistettu           BOOLEAN   DEFAULT FALSE,
  poistaja            INTEGER REFERENCES kayttaja (id)
);

-- Katso R__toimenpide_kohteen_mukaan.sql, R__liikennetapahtuma_sama_kohde.sql