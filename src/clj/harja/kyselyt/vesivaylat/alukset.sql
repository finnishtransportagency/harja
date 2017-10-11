-- name: hae-urakan-alukset
SELECT
  mmsi,
  nimi,
  au.lisatiedot
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