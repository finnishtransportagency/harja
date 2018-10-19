-- name: tallenna-sonjan-tila<!
INSERT INTO harjatila (palvelin, tila, "osa-alue", paivitetty)
  VALUES (:palvelin, :tila::JSONB, :osa-alue, NOW())
  ON CONFLICT (palvelin, "osa-alue") DO UPDATE SET tila = :tila::JSONB;

-- name: hae-sonjan-tila
SELECT palvelin, tila, paivitetty
FROM harjatila
WHERE "osa-alue"='sonja';