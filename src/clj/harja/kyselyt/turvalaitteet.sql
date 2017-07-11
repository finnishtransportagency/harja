-- name: poista-turvalaitteet!
UPDATE vv_turvalaite
SET poistettu = TRUE;

-- name: luo-turvalaite<!
INSERT INTO vv_turvalaite
(sijainti,
 tunniste,
 turvalaitenro,
 arvot,
 poistettu)
VALUES (:sijainti :: GEOMETRY,
        :tunniste,
        :turvalaitenro,
        :arvot :: JSONB,
        FALSE)
ON CONFLICT (tunniste)
  DO UPDATE
    SET
      sijainti  = :sijainti :: GEOMETRY,
      arvot     = :arvot :: JSONB,
      poistettu = FALSE;

