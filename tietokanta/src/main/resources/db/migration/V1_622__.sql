CREATE TABLE geometriaaineisto (
  id                    SERIAL PRIMARY KEY,
  nimi                  VARCHAR(128) NOT NULL,
  tiedostonimi          TEXT         NOT NULL,
  "voimassaolo-alkaa"   TIMESTAMP,
  "voimassaolo-paattyy" TIMESTAMP
)