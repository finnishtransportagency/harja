CREATE TYPE turvallisuuspoikkeamatyyppi AS ENUM (
  'turvallisuuspoikkeama',
  'prosessipoikkeama',
  'tyoturvallisuuspoikkeama'
);

CREATE TABLE turvallisuuspoikkeama(
  id SERIAL PRIMARY KEY,
  urakka INTEGER REFERENCES urakka (id),

  tapahtunut TIMESTAMP,
  paattynyt TIMESTAMP,
  kasitelty TIMESTAMP,

  tyontekijanammatti TEXT,
  tyotehtava TEXT,
  kuvaus TEXT,
  vammat TEXT,
  sairauspoissaolopaivat INTEGER,
  sairaalavuorokaudet INTEGER,

  luotu TIMESTAMP,
  luoja INTEGER REFERENCES kayttaja (id),
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja (id),

  sijainti POINT,
  tr_numero INTEGER,
  tr_alkuosa INTEGER,
  tr_loppuosa INTEGER,
  tr_loppuetaisyys INTEGER,

  tyyppi turvallisuuspoikkeamatyyppi[]
);

CREATE TABLE turvallisuuspoikkeama_liite (
  turvallisuuspoikkeama INTEGER REFERENCES turvallisuuspoikkeama (id),
  liite INTEGER REFERENCES liite (id)
);

CREATE TABLE korjaavatoimenpide(
  id SERIAL PRIMARY KEY,
  turvallisuuspoikkeama INTEGER REFERENCES turvallisuuspoikkeama (id),
  kuvaus TEXT,
  suoritettu TIMESTAMP,
  vastaavahenkilo TEXT
)