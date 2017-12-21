CREATE TABLE geometriaaineisto (
  id                    SERIAL PRIMARY KEY,
  nimi                  VARCHAR(128) NOT NULL,
  tiedostonimi          TEXT         NOT NULL,
  "voimassaolo-alkaa"   TIMESTAMP,
  "voimassaolo-paattyy" TIMESTAMP
);

INSERT INTO geometriaaineisto (nimi, tiedostonimi, "voimassaolo-alkaa", "voimassaolo-paattyy") VALUES
  ('urakat', 'PTV_Hoitourakat10_2016', '2016-10-01 11:01:00.000000', '2017-9-30 11:00:00.000000');

INSERT INTO geometriaaineisto (nimi, tiedostonimi, "voimassaolo-alkaa", "voimassaolo-paattyy") VALUES
  ('urakat', 'PTV_Hoitourakat10_2017', '2017-10-01 11:01:00.000000', '2018-09-30 11:00:00.000000');

INSERT INTO geometriaaineisto (nimi, tiedostonimi, "voimassaolo-alkaa", "voimassaolo-paattyy") VALUES
  ('urakat', 'PTV_Hoitourakat10_2017', '2018-10-01 11:01:00.000000', '2019-09-30 11:00:00.000000');