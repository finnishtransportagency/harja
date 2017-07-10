-- name: poista-vaylat!
UPDATE vv_vayla
SET poistettu = TRUE
WHERE tunniste IS NOT NULL;

-- name: luo-vayla<!
INSERT INTO vv_vayla
(sijainti,
 tunniste,
 arvot,
 poistettu)
VALUES (:sijainti :: GEOMETRY,
        :tunniste,
        :arvot :: JSONB,
        FALSE)
ON CONFLICT (tunniste)
  DO UPDATE
    SET
      sijainti  = :sijainti :: GEOMETRY,
      arvot     = :arvot :: JSONB,
      poistettu = FALSE;

