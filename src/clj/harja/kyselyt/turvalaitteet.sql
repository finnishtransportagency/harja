-- name: poista-turvalaitteet!
UPDATE vv_turvalaite
SET poistettu = TRUE;

-- name: luo-turvalaite<!
INSERT INTO vv_turvalaite
(sijainti,
 tunniste,
 turvalaitenro,
 vaylat,
 arvot,
 poistettu)
VALUES (:sijainti :: GEOMETRY,
        :tunniste,
        :turvalaitenro,
        :vaylat :: INTEGER [],
        :arvot :: JSONB,
        FALSE)
ON CONFLICT (tunniste)
  DO UPDATE
    SET
      sijainti      = :sijainti :: GEOMETRY,
      tunniste      = :tunniste,
      turvalaitenro = :turvalaitenro,
      vaylat        = :vaylat :: INTEGER [],
      arvot         = :arvot :: JSONB,
      poistettu     = FALSE;

