-- name: poista-vaylat!
UPDATE vv_vayla
SET poistettu = TRUE
WHERE tunniste IS NOT NULL;

-- name: luo-vayla<!
INSERT INTO vv_vayla
(sijainti,
 tunniste,
 nimi,
 arvot,
 poistettu)
VALUES (ST_GeomFromGeoJSON(:sijainti),
        :tunniste,
        :nimi,
        :arvot :: JSONB,
        FALSE)
ON CONFLICT (tunniste)
  DO UPDATE
    SET
      sijainti  = ST_GeomFromGeoJSON(:sijainti),
      arvot     = :arvot :: JSONB,
      poistettu = FALSE;

