CREATE TABLE sahkelahetykset (
  id         SERIAL PRIMARY KEY,
  urakka     INTEGER REFERENCES urakka (id) NOT NULL,
  lahetetty  TIMESTAMP DEFAULT current_timestamp,
  onnistunut BOOLEAN);