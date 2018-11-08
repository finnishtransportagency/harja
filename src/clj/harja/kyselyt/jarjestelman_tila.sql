-- name: tallenna-sonjan-tila<!
INSERT INTO jarjestelma_tila (palvelin, tila, "osa-alue", paivitetty)
  VALUES (:palvelin, :tila::JSONB, :osa-alue, NOW())
  ON CONFLICT (palvelin, "osa-alue")
    DO UPDATE SET tila = :tila::JSONB,
                  paivitetty = NOW();

-- name: hae-sonjan-tila
SELECT palvelin, tila, paivitetty
FROM jarjestelma_tila
WHERE "osa-alue"='sonja';