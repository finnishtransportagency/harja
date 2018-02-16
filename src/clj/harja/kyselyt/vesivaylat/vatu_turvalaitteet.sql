-- name: vie-turvalaitetauluun<!
-- vie entryn vatu-turvalaite-tauluun
INSERT INTO vatu_turvalaite
(
  turvalaitenro,
  nimi,
  koordinaatit,
  sijainti,
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
  vaylat,
  geometria,
  luotu,
  luoja
)
VALUES
  (
    :turvalaitenro,
    :nimi,
    :geometria ::geometry,
    :sijainti,
    :tyyppi,
    :kiintea,
    :tila,
    :vah_pvm,
    :toimintatila,
    :rakenne,
    :navigointilaji,
    :valaistu,
    :omistaja,
    :turvalaitenro_aiempi,
    :paavayla,
    :vaylat :: INTEGER [],
    ST_GeomFromText(:geometria) :: GEOMETRY,
    current_timestamp,
    :luoja
  )
ON CONFLICT (turvalaitenro)
  DO UPDATE
    SET
      turvalaitenro        = :turvalaitenro,
      nimi                 = :nimi,
      koordinaatit         = :geometria ::geometry,
      sijainti             = :sijainti,
      tyyppi               = :tyyppi,
      kiintea              = :kiintea,
      tila                 = :tila,
      vah_pvm              = :vah_pvm,
      toimintatila         = :toimintatila,
      rakenne              = :rakenne,
      navigointilaji       = :navigointilaji,
      valaistu             = :valaistu,
      omistaja             = :omistaja,
      turvalaitenro_aiempi = :turvalaitenro_aiempi,
      paavayla             = :paavayla,
      vaylat               = :vaylat :: INTEGER [],
      geometria            = :geometria :: GEOMETRY,
      muokattu             = current_timestamp,
      muokkaaja            = :muokkaaja;

-- name: hae-turvalaite-tunnuksella
SELECT * from vatu_turvalaite WHERE turvalaitenro = :turvalaitenro
