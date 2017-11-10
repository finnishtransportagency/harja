-- name: poista-turvalaitteet!
UPDATE vv_turvalaite
SET poistettu = TRUE;

-- name: luo-turvalaite<!
INSERT INTO vv_turvalaite
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



-- name: vie-turvalaitetauluun<!
-- vie entryn vatu-turvalaite-tauluun
INSERT INTO vatu_turvalaite
(
  turvalaitenro,
  nimi,
  sijainti,
  sijaintikuvaus,
  tyyppi,
  tarkenne,
  tila,
  vah_pvm,
  toimintatila,
  rakenne,
  navigointilaji,
  valaistu,
  omistaja,
  turvalaitenro_aiempi,
  paavayla,
  vaylat
)
VALUES
(
  :turvalaitenro,
  :nimi,
  :sijainti :: GEOMETRY,
  :sijaintikuvaus,
  :tyyppi,
  :tarkenne,
  :tila,
  :vah_pvm,
  :toimintatila,
  :rakenne,
  :navigointilaji,
  :valaistu,
  :omistaja,
  :turvalaitenro_aiempi,
  :paavayla,
  :vaylat :: INTEGER []
)
ON CONFLICT (turvalaitenro)
  DO UPDATE
    SET
  :turvalaitenro,
  :nimi,
  :sijainti :: GEOMETRY,
  :sijaintikuvaus,
  :tyyppi,
  :tarkenne,
  :tila,
  :vah_pvm,
  :toimintatila,
  :rakenne,
  :navigointilaji,
  :valaistu,
  :omistaja,
  :turvalaitenro_aiempi,
  :paavayla,
  :vaylat :: INTEGER []