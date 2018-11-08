-- name: tallenna-sonjan-tila<!
INSERT INTO jarjestelman_tila (palvelin, tila, "osa-alue", paivitetty)
  VALUES (:palvelin, :tila::JSONB, :osa-alue, NOW())
  ON CONFLICT (palvelin, "osa-alue")
    DO UPDATE SET tila = :tila::JSONB,
                  paivitetty = NOW();

-- name: hae-sonjan-tila
SELECT palvelin, tila, paivitetty
FROM jarjestelman_tila
WHERE "osa-alue"='sonja';