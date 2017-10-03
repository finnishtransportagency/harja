-- name: hae-urakan-alukset
SELECT
  mmsi,
  nimi,
  a.lisatiedot
FROM vv_alus a
  LEFT JOIN vv_alus_urakka au ON au.alus = a.mmsi
WHERE urakka = :urakka
ORDER BY mmsi;

-- name: hae-urakoitsijan-alukset
SELECT
  mmsi,
  a.nimi,
  a.lisatiedot
FROM vv_alus a
  LEFT JOIN vv_alus_urakka au ON au.alus = a.mmsi
  LEFT JOIN urakka u ON u.id = au.urakka
  LEFT JOIN organisaatio o ON u.urakoitsija = o.id
WHERE o.id = :urakoitsija
ORDER BY mmsi;

-- name: hae-alusten-reitit
SELECT
  alus,
  -- yhdistetään pisteet reittiviivaksi. Nimetään sijainniksi, koska
  -- sitä nimeä on käytetty ympäri harjan..
  ST_Simplify (ST_MakeLine(array_agg(sijainti :: GEOMETRY)), 200, true) AS sijainti,
  -- Taulukkoon aikaleimat ja pisteet, jotta voidaan kartalla näyttää,
  -- missä laiva on milloinkin ollut
  array_agg((aika, sijainti)) AS pisteet
FROM vv_alus_sijainti
GROUP BY alus;