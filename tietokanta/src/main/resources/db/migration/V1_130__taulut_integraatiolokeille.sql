CREATE TYPE integraatiosuunta AS ENUM (
  'sisään',
  'ulos'
);

CREATE TABLE integraatio (
  id          SERIAL PRIMARY KEY,
  jarjestelma VARCHAR(20) NOT NULL,
  nimi        VARCHAR(60) NOT NULL
);

CREATE TABLE integraatiotapahtuma (
  id          SERIAL PRIMARY KEY,
  integraatio INTEGER REFERENCES integraatio (id) NOT NULL,
  alkanut     TIMESTAMP,
  paattynyt   TIMESTAMP,
  lisatietoja TEXT,
  onnistunut  BOOLEAN,
  ulkoinenid  VARCHAR(60)
);

CREATE TABLE integraatioviesti (
  id                   SERIAL PRIMARY KEY,
  integraatiotapahtuma INTEGER REFERENCES integraatiotapahtuma (id) NOT NULL,
  suunta               integraatiosuunta,
  sisaltotyyppi        VARCHAR(40),
  siirtotyyppi         VARCHAR(40),
  sisalto              TEXT,
  otsikko              TEXT,
  parametrit           TEXT
);