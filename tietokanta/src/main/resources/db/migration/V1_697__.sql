ALTER TABLE tielupa
  ALTER COLUMN "johtolupa-tienalituksia" TYPE TEXT,
  ALTER COLUMN "johtolupa-tienylityksia" TYPE TEXT,
  ALTER COLUMN "johtolupa-silta-asennuksia" TYPE TEXT,
  ALTER COLUMN "liittymalupa-arvioitu-kokonaisliikenne" TYPE TEXT,
  ALTER COLUMN "liittymalupa-arvioitu-kuorma-autoliikenne" TYPE TEXT,
  ALTER COLUMN "liittymalupa-nykyisen-liittyman-numero" TYPE TEXT,
  ALTER COLUMN "opastelupa-alkuperainen-lupanro" TYPE TEXT,
  ALTER COLUMN "vesihuoltolupa-tienylityksia" TYPE TEXT,
  ALTER COLUMN "vesihuoltolupa-tienalituksia" TYPE TEXT,
  ALTER COLUMN "vesihuoltolupa-silta-asennuksia" TYPE TEXT,
  DROP COLUMN kaapeliasennukset,
  DROP COLUMN opasteet;

DROP TYPE TIELUVAN_KAAPELIASENNUS;
DROP TYPE TIELUVAN_OPASTE;

CREATE TYPE TIELUVAN_KAAPELIASENNUS AS (
  laite                    TEXT,
  asennustyyppi            TEXT,
  kommentit                TEXT,
  "maakaapelia-metreissa"  DECIMAL,
  "ilmakaapelia-metreissa" DECIMAL,
  nopeusrajoitus           TEXT,
  liikennemaara            DECIMAL,
  tie                      INTEGER,
  aosa                     INTEGER,
  aet                      INTEGER,
  losa                     INTEGER,
  let                      INTEGER,
  ajorata                  INTEGER,
  kaista                   INTEGER,
  puoli                    INTEGER,
  karttapvm                DATE,
  geometria                GEOMETRY
);

CREATE TYPE TIELUVAN_OPASTE AS (
  tulostenumero TEXT,
  kuvaus        TEXT,
  tie           INTEGER,
  aosa          INTEGER,
  aet           INTEGER,
  losa          INTEGER,
  let           INTEGER,
  ajorata       INTEGER,
  kaista        INTEGER,
  puoli         INTEGER,
  karttapvm     DATE,
  geometria     GEOMETRY
);

ALTER TABLE tielupa
  ADD COLUMN kaapeliasennukset TIELUVAN_KAAPELIASENNUS [],
  ADD COLUMN opasteet TIELUVAN_OPASTE [];
