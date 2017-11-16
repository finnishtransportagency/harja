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
      turvalaitenro        = :turvalaitenro,
      nimi                 = :nimi,
      sijainti             = :sijainti :: GEOMETRY,
      sijaintikuvaus       = :sijaintikuvaus,
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
      vaylat               = :vaylat :: INTEGER []