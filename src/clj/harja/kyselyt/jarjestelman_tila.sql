-- name: tallenna-jarjestelman-tila<!
INSERT INTO jarjestelman_tila (palvelimen_osoite, palvelimen_versio, tila, "osa-alue", paivitetty)
  VALUES (:palvelimen-osoite, :palvelimen-versio, :tila::JSONB, :osa-alue, NOW())
  ON CONFLICT (palvelimen_osoite, palvelimen_versio, "osa-alue")
    DO UPDATE SET tila = :tila::JSONB,
                  paivitetty = NOW();

-- name: hae-jarjestelman-tila
SELECT palvelimen_osoite, palvelimen_versio, tila, paivitetty
FROM jarjestelman_tila
WHERE "osa-alue"=:osa-alue AND
      (:kehitys? IS TRUE OR
       palvelimen_versio = :palvelimen-versio)
ORDER BY paivitetty DESC;
