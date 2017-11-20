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
    :koordinaatit,
    :sijainti,
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
    :vaylat :: INTEGER [],
    ST_GeomFromText(:geometria) :: GEOMETRY, -- ST_MakePoint on tarkempi
    current_timestamp,
    :luoja
  )
ON CONFLICT (turvalaitenro)
  DO UPDATE
    SET
      turvalaitenro        = :turvalaitenro,
      nimi                 = :nimi,
      koordinaatit         = :koordinaatit,
      sijainti             = :sijainti,
      tyyppi               = :tyyppi,
      tarkenne             = :tarkenne,
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
      muokkaaja            = :muokkaaja