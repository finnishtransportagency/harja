CREATE TABLE urakan_tyotunnit (
  id                 SERIAL PRIMARY KEY,
  urakka             INTEGER REFERENCES urakka (id)      NOT NULL,
  vuosi              INTEGER                             NOT NULL,
  vuosikolmannes     INTEGER                             NOT NULL,
  tyotunnit          INTEGER                             NOT NULL,
  lahetetty          TIMESTAMP,
  lahetys_onnistunut BOOLEAN,
  UNIQUE (urakka, vuosi, vuosikolmannes),
  CONSTRAINT validi_vuosikolmannes CHECK (vuosikolmannes >= 1 AND vuosikolmannes <= 3)
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('turi', 'urakan-tyotunnit')