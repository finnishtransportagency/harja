-- name: poista-kanavasillat!
UPDATE kan_silta
SET muokkaaja = 'POISTO';

-- name: luo-kanavasilta<!
INSERT INTO kan_silta
(sijainti,
 turvalaitenro,
 tyyppi,
 kiintea,
 nimi,
 vaylat,
 arvot,
 poistettu)
VALUES (:sijainti :: GEOMETRY,
        :turvalaitenro,
        :tyyppi :: VV_TURVALAITETYYPPI,
        :kiintea,
        :nimi,
        :vaylat :: INTEGER [],
        :arvot :: JSONB,
        FALSE)
ON CONFLICT (turvalaitenro)
  DO UPDATE
    SET
      sijainti      = :sijainti :: GEOMETRY,
      turvalaitenro = :turvalaitenro,
      tyyppi        = :tyyppi :: VV_TURVALAITETYYPPI,
      kiintea       = :kiintea,
      nimi          = :nimi,
      vaylat        = :vaylat :: INTEGER [],
      arvot         = :arvot :: JSONB,
      poistettu     = FALSE;

