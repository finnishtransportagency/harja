-- name: poista-turvalaitteet!
UPDATE vv_turvalaite
SET poistettu = TRUE;

-- name: luo-turvalaite<!
INSERT INTO vv_turvalaite
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

