-- name: hae-urakoitsijan-alukset
SELECT
  mmsi,
  a.nimi,
  a.lisatiedot,
  (SELECT EXISTS(SELECT * FROM vv_alus_urakka au WHERE
                                                  au.alus = a.mmsi
                                                  AND au.urakka = :urakka
                                                  AND poistettu IS NOT TRUE))
                 AS "kaytossa-urakassa?",
  au.lisatiedot AS "urakan-aluksen-kayton-lisatiedot",
    (SELECT ARRAY(SELECT urakka FROM vv_alus_urakka auv WHERE auv.alus = a.mmsi
                                                        AND auv.poistettu IS NOT TRUE)
  AS "kaytossa-urakoissa")
FROM vv_alus a
  LEFT JOIN vv_alus_urakka au ON au.alus = a.mmsi
                                 AND au.poistettu IS NOT TRUE
WHERE a.urakoitsija = :urakoitsija
      AND a.poistettu IS NOT TRUE
ORDER BY mmsi;

-- name: hae-urakoitsijan-alus-mmsilla
SELECT
  mmsi,
  nimi,
  lisatiedot,
  urakoitsija AS "urakoitsija-id",
FROM vv_alus a
WHERE urakoitsija = :urakoitsija
      AND mmsi = :mmsi;

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
