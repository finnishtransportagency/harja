-- Hoitoluokka taulun hakuja varten

CREATE OR REPLACE FUNCTION hoitoluokka_pisteelle
  (piste geometry, tietolaji hoitoluokan_tietolajitunniste, treshold INTEGER, kielletyt_hoitoluokat INTEGER[])
  RETURNS INTEGER
AS $$
SELECT hoitoluokka
  FROM hoitoluokka
 WHERE ST_DWithin(geometria, piste, treshold) AND
       tietolajitunniste = tietolaji AND
       hoitoluokka != ALL(kielletyt_hoitoluokat)
 ORDER BY ST_Length(ST_ShortestLine(geometria, piste)) ASC
 LIMIT 1;
$$ LANGUAGE SQL IMMUTABLE;

-- Vanhan ja uuden talvihoitoluokan mäppäys päivämäärän perusteella
-- Tierekisteriin tuli uusi talvihoitoluokkakoodisto 14.6.2018 ja
-- Harjan käyttämään geometria-aineistoon 2.7.2018
CREATE OR REPLACE FUNCTION normalisoi_talvihoitoluokka (talvihoitoluokka INTEGER, pvm TIMESTAMP)
  RETURNS INTEGER AS $$
SELECT
  CASE
  WHEN talvihoitoluokka IS NULL THEN 100
  WHEN talvihoitoluokka = 99 THEN 99
  WHEN talvihoitoluokka > 11 AND talvihoitoluokka != 99 THEN 100
  WHEN pvm < '2018-07-02'::TIMESTAMP THEN
    CASE
    WHEN talvihoitoluokka = 0 THEN 1  -- Ise
    WHEN talvihoitoluokka = 1 THEN 2  -- Is
    WHEN talvihoitoluokka = 2 THEN 3  -- I
    WHEN talvihoitoluokka = 3 THEN 4  -- Ib
    WHEN talvihoitoluokka = 4 THEN 5  -- TIb -> Ic
    WHEN talvihoitoluokka = 5 THEN 6  -- II
    WHEN talvihoitoluokka = 6 THEN 7  -- III
    WHEN talvihoitoluokka = 7 THEN 9  -- K1
    WHEN talvihoitoluokka = 8 THEN 10  -- K2
    WHEN talvihoitoluokka = 9 THEN 11 -- ei talvihoitoa'
    ELSE 100 -- ei talvihoitoluokkaa / ei tunnistettu
    END
  ELSE talvihoitoluokka
  END as thl;
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION normalisoi_talvihoitoluokka (talvihoitoluokka INTEGER, pvm DATE)
  RETURNS INTEGER AS $$
SELECT * FROM normalisoi_talvihoitoluokka(talvihoitoluokka, pvm::TIMESTAMP WITHOUT TIME ZONE);
$$ LANGUAGE SQL IMMUTABLE;