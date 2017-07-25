-- name: poista-turvalaitteet!
UPDATE vv_turvalaite
SET poistettu = TRUE;

-- name: luo-turvalaite<!
INSERT INTO vv_turvalaite
(sijainti,
 tunniste,
 turvalaitenro,
 tyyppi,
 kiintea,
 nimi,
 vaylat,
 arvot,
 poistettu)
VALUES (:sijainti :: GEOMETRY,
        :tunniste,
        :turvalaitenro,
        :tyyppi :: VV_TURVALAITETYYPPI,
        :kiintea,
        :nimi,
        :vaylat :: INTEGER [],
        :arvot :: JSONB,
        FALSE)
ON CONFLICT (tunniste)
  DO UPDATE
    SET
      sijainti      = :sijainti :: GEOMETRY,
      tunniste      = :tunniste,
      turvalaitenro = :turvalaitenro,
      tyyppi        = :tyyppi :: VV_TURVALAITETYYPPI,
      kiintea       = :kiintea,
      nimi          = :nimi,
      vaylat        = :vaylat :: INTEGER [],
      arvot         = :arvot :: JSONB,
      poistettu     = FALSE;

