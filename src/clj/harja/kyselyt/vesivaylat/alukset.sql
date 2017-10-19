-- name: hae-urakan-alukset
SELECT
  mmsi,
  nimi,
  au.lisatiedot
FROM vv_alus a
  LEFT JOIN vv_alus_urakka au ON au.alus = a.mmsi
                              AND au.poistettu IS NOT TRUE
WHERE urakka = :urakka
      AND a.poistettu IS NOT TRUE
ORDER BY mmsi;

-- name: hae-urakoitsijan-alukset
SELECT
  mmsi,
  a.nimi,
  a.lisatiedot
FROM vv_alus a
WHERE a.urakoitsija = :urakoitsija
AND a.poistettu IS NOT TRUE
ORDER BY mmsi;

-- name: hae-urakan-alus-mmsilla
SELECT
  mmsi,
  nimi,
  au.lisatiedot
FROM vv_alus a
  LEFT JOIN vv_alus_urakka au ON au.alus = a.mmsi
                              AND au.poistettu IS NOT TRUE
WHERE urakka = :urakka
      AND a.poistettu IS NOT TRUE
      AND mmsi = :mmsi;

-- name: hae-alusten-reitit
SELECT
  alus AS "alus-mmsi",
  -- yhdistetään pisteet reittiviivaksi. Nimetään sijainniksi, koska
  -- sitä nimeä on käytetty ympäri harjan..
  ST_Simplify (ST_MakeLine(array_agg(sijainti :: GEOMETRY)), 200, true) AS sijainti
FROM vv_alus_sijainti
WHERE (:alukset IS NULL OR alus IN (:alukset))
AND aika BETWEEN :alku AND :loppu
GROUP BY alus;

-- name: hae-alusten-reitit-pisteineen
SELECT
  alus AS "alus-mmsi",
  -- yhdistetään pisteet reittiviivaksi. Nimetään sijainniksi, koska
  -- sitä nimeä on käytetty ympäri harjan..
  ST_Simplify (ST_MakeLine(array_agg(sijainti :: GEOMETRY)), 200, true) AS sijainti,
  -- Taulukkoon aikaleimat ja pisteet, jotta voidaan kartalla näyttää,
  -- missä laiva on milloinkin ollut
  array_agg((aika, sijainti)) AS pisteet
FROM vv_alus_sijainti
WHERE (:alukset IS NULL OR alus IN (:alukset))
      AND aika BETWEEN :alku AND :loppu
GROUP BY alus;
