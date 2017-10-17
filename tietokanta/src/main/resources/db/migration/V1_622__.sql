CREATE TABLE geometriaaineisto (
  id                    SERIAL PRIMARY KEY,
  nimi                  VARCHAR(128) NOT NULL,
  tiedostonimi          TEXT         NOT NULL,
  "voimassaolo-alkaa"   TIMESTAMP,
  "voimassaolo-paattyy" TIMESTAMP
);

CREATE UNIQUE INDEX uniikki_geometriaaineisto_nimi on geometriaaineisto(nimi);