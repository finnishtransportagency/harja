-- Luo oma taulu käyttäjien anti-CSRF tokeneille (voi olla nyt käyttäjällä usea validi sessio)
CREATE TABLE kayttaja_anti_csrf_token (
  id                SERIAL PRIMARY KEY,
  "kayttaja_id"     INTEGER REFERENCES kayttaja (id),
  "anti_csrf_token" TEXT,
  voimassa          TIMESTAMP
);