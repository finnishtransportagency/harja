CREATE TABLE sahkelahetys (
  id         SERIAL PRIMARY KEY,
  urakka     INTEGER REFERENCES urakka (id)      NOT NULL,
  lahetetty  TIMESTAMP DEFAULT current_timestamp NOT NULL,
  onnistunut BOOLEAN,
  UNIQUE (urakka)
);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sahke', 'urakan-lahetys');