CREATE TABLE geometriaiaineisto (
  id                    SERIAL PRIMARY KEY,
  nimi                  VARCHAR(128) NOT NULL,
  osoite                TEXT         NOT NULL,
  "voimassaolo-alkaa"   TIMESTAMP,
  "voimassaolo-paattyy" TIMESTAMP
)