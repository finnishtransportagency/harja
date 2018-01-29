-- name: luo-kanavasilta<!
INSERT INTO kan_silta
(siltanro,
 nimi,
 tunnus,
 kayttotarkoitus,
 tila,
 pituus,
 rakennetiedot,
 tieosoitteet,
 sijainti_lev,
 sijainti_pit,
 geometria,
 avattu,
 trex_muutettu,
 trex_oid,
 trex_sivu,
 luoja,
 luotu,
 poistettu)
VALUES (
  :siltanro,
  :nimi,
  :tunnus,
  :kayttotarkoitus :: TEXT [],
  :tila,
  :pituus,
  :rakennetiedot :: TEXT [],
  :tieosoitteet :: TR_OSOITE_LAAJENNETTU [],
  :sijainti_lev,
  :sijainti_pit,
  st_makepoint(:sijainti_pit, :sijainti_lev) :: GEOMETRY,
  :avattu,
  :trex_muutettu,
  :trex_oid,
  :trex_sivu,
  :luoja,
  CURRENT_TIMESTAMP,
  :poistettu)
ON CONFLICT (siltanro)
  DO UPDATE
    SET
      siltanro      = :siltanro,
      nimi          = :nimi,
      tunnus = :tunnus,
      kayttotarkoitus = :kayttotarkoitus :: TEXT [],
      tila = :tila,
      pituus = :pituus,
      rakennetiedot = :rakennetiedot :: TEXT [],
      tieosoitteet  = :tieosoitteet :: TR_OSOITE_LAAJENNETTU [],
      sijainti_lev = :sijainti_lev,
      sijainti_pit = :sijainti_pit,
      geometria = st_makepoint(:sijainti_pit, :sijainti_lev) :: GEOMETRY,
      avattu = :avattu,
      trex_muutettu = :trex_muutettu,
      trex_oid = :trex_oid,
      trex_sivu = :trex_sivu,
      muokkaaja = :muokkaaja,
      muokattu      = CURRENT_TIMESTAMP,
      poistettu = :poistettu;

--name:hae-kanavasiltakohde-tunnuksella
SELECT
  s.siltanro,
  s.nimi,
  s.tunnus,
  s.kayttotarkoitus,
  s.tila,
  s.pituus,
  s.rakennetiedot,
  s.tieosoitteet,
  s.sijainti_lev,
  s.sijainti_pit,
  s.avattu,
  s.trex_muutettu,
  s.trex_oid,
  s.trex_sivu,
  s.poistettu,
  k.nimi as kohteenosan_nimi
FROM kan_silta AS s,
  kan_kohteenosa AS k
WHERE s.siltanro = :siltanumero AND
      k.lahdetunnus = s.siltanro;
