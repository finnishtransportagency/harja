-- name: hae-ilmoitukset
SELECT
  i.id, i.urakka, i.ilmoitusid, i.ilmoitettu, i.valitetty, i.yhteydenottopyynto, i.vapaateksti,
  i.ilmoitustyyppi, i.selitteet, i.urakkatyyppi, i.suljettu,

  i.sijainti, i.tr_numero, i.tr_alkuosa, i.tr_loppuosa, i.tr_alkuetaisyys , i.tr_loppuetaisyys,

  i.ilmoittaja_etunimi, i.ilmoittaja_sukunimi,
  i.ilmoittaja_tyopuhelin, i.ilmoittaja_matkapuhelin, i.ilmoittaja_sahkoposti, i.ilmoittaja_tyyppi,

  i.lahettaja_etunimi, i.lahettaja_sukunimi, i.lahettaja_puhelinnumero, i.lahettaja_sahkoposti,

  k.id as kuittaus_id, k.kuitattu as kuittaus_kuitattu, k.vapaateksti as kuittaus_vapaateksti,
  k.kuittaustyyppi as kuittaus_kuittaustyyppi,

  k.kuittaaja_henkilo_etunimi as kuittaus_kuittaaja_etunimi,
  k.kuittaaja_henkilo_sukunimi as kuittaus_kuittaaja_sukunimi,
  k.kuittaaja_henkilo_matkapuhelin as kuittaus_kuittaaja_matkapuhelin,
  k.kuittaaja_henkilo_tyopuhelin as kuittaus_kuittaaja_tyopuhelin,
  k.kuittaaja_henkilo_sahkoposti as kuittaus_kuittaaja_sahkoposti,
  k.kuittaaja_organisaatio_nimi as kuittaus_kuittaaja_organisaatio,
  k.kuittaaja_organisaatio_ytunnus as kuittaus_kuittaaja_ytunnus,

  k.kasittelija_henkilo_etunimi as kuittaus_kasittelija_etunimi,
  k.kasittelija_henkilo_sukunimi as kuittaus_kasittelija_sukunimi,
  k.kasittelija_henkilo_matkapuhelin as kuittaus_kasittelija_matkapuhelin,
  k.kasittelija_henkilo_tyopuhelin as kuittaus_kasittelija_tyopuhelin,
  k.kasittelija_henkilo_sahkoposti as kuittaus_kasittelija_sahkoposti,
  k.kasittelija_organisaatio_nimi as kuittaus_kasittelija_organisaatio,
  k.kasittelija_organisaatio_ytunnus as kuittaus_kasittelija_ytunnus


FROM ilmoitus i
  LEFT JOIN kuittaus k ON k.ilmoitus = i.id
  WHERE
    -- Tarkasta että ilmoituksen geometria sopii hakuehtoihin
    (
      -- Joko haetaan koko maasta
      (:hallintayksikko_annettu IS FALSE AND :urakka_annettu IS FALSE) OR

      -- Tai hallintayksikön tasolla
      (:urakka_annettu IS FALSE AND
        st_contains(
          (SELECT alue FROM organisaatio WHERE id=:hallintayksikko),
          i.sijainti::GEOMETRY)) OR

      -- Tai urakan tasolla
      (st_contains((SELECT alue FROM urakka WHERE id=:urakka), i.sijainti::GEOMETRY))
    ) AND

    -- Tarkasta että ilmoituksen saapumisajankohta sopii hakuehtoihin
    (
      (:alku_annettu IS FALSE AND :loppu_annettu IS FALSE) OR
      (:alku_annettu IS FALSE AND i.ilmoitettu::DATE <= :loppu) OR
      (:loppu_annettu IS FALSE AND i.ilmoitettu::DATE >= :alku) OR
      (i.ilmoitettu BETWEEN :alku AND :loppu)
    ) AND

    -- Tarkasta ilmoituksen tyypit
    (
      :tyypit_annettu IS FALSE OR
      i.ilmoitustyyppi::TEXT IN (:tyypit)
    ) AND

    -- Tarkasta vapaatekstihakuehto
    (
      :teksti_annettu IS FALSE OR
      i.vapaateksti LIKE :teksti
    ) AND

    -- Tarkasta ilmoituksen tilat
    (
      (:suljetut IS TRUE AND :avoimet IS TRUE) OR
      (:suljetut IS FALSE AND :avoimet IS FALSE) OR
      (:suljetut IS TRUE AND i.suljettu IS TRUE) OR
      (:avoimet IS TRUE AND i.suljettu IS NOT TRUE)
    ) ORDER BY k.kuitattu ASC;