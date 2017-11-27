CREATE TABLE tielupa (
  id                         SERIAL PRIMARY KEY,
  "ulkoinen-tunniste"        INTEGER                              NOT NULL,
  tyyppi                     VARCHAR(128)                         NOT NULL,
  "paatoksen-diaarinumero"   VARCHAR(128)                         NOT NULL,
  saapumispvm                DATE,
  myontamispvm               DATE,
  "voimassaolon-alkupvm"     DATE,
  "voimassaolon-loppupvm"    DATE,
  otsikko                    VARCHAR(2048)                        NOT NULL,
  "katselmus-url"            TEXT,
  ely                        INTEGER REFERENCES organisaatio (id) NOT NULL,
  urakka                     INTEGER REFERENCES urakka (id),
  "urakan-nimi"              VARCHAR(512),
  kunta                      VARCHAR(256)                         NOT NULL,
  "kohteen-lahiosoite"       VARCHAR(512),
  "kohteen-postinumero"      VARCHAR(5),
  "kohteen-postitoimipaikka" VARCHAR(512),
  "tien-nimi"                VARCHAR(512),
  sijainnit                  GEOMETRY

)