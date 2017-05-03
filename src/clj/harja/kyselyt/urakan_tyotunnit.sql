-- name: hae-urakan-tyotunnit
SELECT
  vuosi,
  vuosikolmannes,
  tyotunnit
FROM urakan_tyotunnit
WHERE urakka = :urakka;
