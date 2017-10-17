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
  LEFT JOIN vv_alus_urakka au ON au.alus = a.mmsi
                              AND au.poistettu IS NOT TRUE
  LEFT JOIN urakka u ON u.id = au.urakka
LEFT JOIN organisaatio o ON u.urakoitsija = o.id
WHERE o.id = :urakoitsija
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
      AND mmsi = :mmsi