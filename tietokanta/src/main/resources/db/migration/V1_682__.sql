-- Luo oma taulu käyttäjien anti-CSRF tokeneille (voi olla nyt käyttäjällä usea validi sessio)
CREATE TABLE kayttaja_anti_csrf_token (
  id                SERIAL PRIMARY KEY,
  "kayttaja_id"     INTEGER REFERENCES kayttaja (id) NOT NULL,
  "anti_csrf_token" TEXT NOT NULL,
  voimassa          TIMESTAMP NOT NULL
);

CREATE INDEX kayttaja_anti_csrf_token_idx ON kayttaja_anti_csrf_token (kayttaja_id);
