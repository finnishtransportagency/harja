-- Hoitoluokka taulun hakuja varten

CREATE OR REPLACE FUNCTION hoitoluokka_pisteelle
  (piste geometry, tietolaji hoitoluokan_tietolajitunniste, treshold INTEGER)
  RETURNS INTEGER
AS $$
SELECT hoitoluokka
  FROM hoitoluokka
 WHERE ST_DWithin(geometria, piste, treshold) AND
       tietolajitunniste = tietolaji
 ORDER BY ST_Length(ST_ShortestLine(geometria, piste)) ASC
 LIMIT 1;
$$ LANGUAGE SQL IMMUTABLE;
