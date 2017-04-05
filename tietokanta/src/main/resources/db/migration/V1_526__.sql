CREATE TABLE sahkelahetys (
  id         SERIAL PRIMARY KEY,
  urakka     INTEGER REFERENCES urakka (id) NOT NULL,
  lahetetty  TIMESTAMP DEFAULT current_timestamp,
  onnistunut BOOLEAN);

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('sahke', 'urakan-lahetys');