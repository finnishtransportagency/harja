-- name: hae-ilmoitukset
SELECT
  i.id,
  i.urakka,
  i.ilmoitusid,
  i.ilmoitettu,
  i.valitetty,
  i.yhteydenottopyynto,
  i.vapaateksti,
  i.ilmoitustyyppi,
  i.selitteet,
  i.urakkatyyppi,
  i.suljettu,

  i.sijainti,
  i.tr_numero,
  i.tr_alkuosa,
  i.tr_loppuosa,
  i.tr_alkuetaisyys,
  i.tr_loppuetaisyys,

  i.ilmoittaja_etunimi,
  i.ilmoittaja_sukunimi,
  i.ilmoittaja_tyopuhelin,
  i.ilmoittaja_matkapuhelin,
  i.ilmoittaja_sahkoposti,
  i.ilmoittaja_tyyppi,

  i.lahettaja_etunimi,
  i.lahettaja_sukunimi,
  i.lahettaja_puhelinnumero,
  i.lahettaja_sahkoposti,

  k.id                               AS kuittaus_id,
  k.kuitattu                         AS kuittaus_kuitattu,
  k.vapaateksti                      AS kuittaus_vapaateksti,
  k.kuittaustyyppi                   AS kuittaus_kuittaustyyppi,

  k.kuittaaja_henkilo_etunimi        AS kuittaus_kuittaaja_etunimi,
  k.kuittaaja_henkilo_sukunimi       AS kuittaus_kuittaaja_sukunimi,
  k.kuittaaja_henkilo_matkapuhelin   AS kuittaus_kuittaaja_matkapuhelin,
  k.kuittaaja_henkilo_tyopuhelin     AS kuittaus_kuittaaja_tyopuhelin,
  k.kuittaaja_henkilo_sahkoposti     AS kuittaus_kuittaaja_sahkoposti,
  k.kuittaaja_organisaatio_nimi      AS kuittaus_kuittaaja_organisaatio,
  k.kuittaaja_organisaatio_ytunnus   AS kuittaus_kuittaaja_ytunnus,

  k.kasittelija_henkilo_etunimi      AS kuittaus_kasittelija_etunimi,
  k.kasittelija_henkilo_sukunimi     AS kuittaus_kasittelija_sukunimi,
  k.kasittelija_henkilo_matkapuhelin AS kuittaus_kasittelija_matkapuhelin,
  k.kasittelija_henkilo_tyopuhelin   AS kuittaus_kasittelija_tyopuhelin,
  k.kasittelija_henkilo_sahkoposti   AS kuittaus_kasittelija_sahkoposti,
  k.kasittelija_organisaatio_nimi    AS kuittaus_kasittelija_organisaatio,
  k.kasittelija_organisaatio_ytunnus AS kuittaus_kasittelija_ytunnus


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
         (SELECT alue
          FROM organisaatio
          WHERE id = :hallintayksikko),
         i.sijainti :: GEOMETRY)) OR

    -- Tai urakan tasolla..
    -- Joko ilmoituksen urakka-id osuu valittuun urakkaan..
    (i.urakka = :urakka OR
    -- Tai ilmoitukselle ei ole annettu urakka-id:tä, mutta se on urakan alueella
     (i.urakka IS NULL AND
      st_contains((SELECT alue
                   FROM urakoiden_alueet
                   WHERE id = :urakka), i.sijainti :: GEOMETRY)))
  ) AND

  -- Tarkasta että ilmoituksen saapumisajankohta sopii hakuehtoihin
  (
    (:alku_annettu IS FALSE AND :loppu_annettu IS FALSE) OR
    (:loppu_annettu IS FALSE AND i.ilmoitettu :: DATE >= :alku) OR
    (:alku_annettu IS FALSE AND i.ilmoitettu :: DATE <= :loppu) OR
    (i.ilmoitettu BETWEEN :alku AND :loppu)
  ) AND

  -- Tarkasta ilmoituksen tyypit
  (
    :tyypit_annettu IS FALSE OR
    i.ilmoitustyyppi :: TEXT IN (:tyypit)
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
  )
ORDER BY i.ilmoitettu ASC, k.kuitattu ASC;


-- name: hae-id-ilmoitus-idlla
-- Hakee id:n ilmoituksen id:llä
SELECT id
FROM ilmoitus
WHERE ilmoitusid = :ilmoitusid;

-- name: luo-ilmoitus<!
-- Luo uuden havainnon
INSERT INTO ilmoitus
(urakka,
 ilmoitusid,
 ilmoitettu,
 valitetty,
 yhteydenottopyynto,
 vapaateksti,
 ilmoitustyyppi,
 selitteet,
 urakkatyyppi,
 ilmoittaja_etunimi,
 ilmoittaja_sukunimi,
 ilmoittaja_tyopuhelin,
 ilmoittaja_matkapuhelin,
 ilmoittaja_sahkoposti,
 ilmoittaja_tyyppi,
 lahettaja_etunimi,
 lahettaja_sukunimi,
 lahettaja_puhelinnumero,
 lahettaja_sahkoposti)
VALUES
  (:urakka,
   :ilmoitusid,
   :ilmoitettu,
   :valitetty,
   :yhteydenottopyynto,
   :vapaateksti,
   :ilmoitustyyppi :: ilmoitustyyppi,
   :selitteet :: ilmoituksenselite [],
   :urakkatyyppi :: urakkatyyppi,
   :ilmoittaja_etunimi,
   :ilmoittaja_sukunimi,
   :ilmoittaja_tyopuhelin,
   :ilmoittaja_matkapuhelin,
   :ilmoittaja_sahkoposti,
   :ilmoittaja_tyyppi :: ilmoittajatyyppi,
   :lahettaja_etunimi,
   :lahettaja_sukunimi,
   :lahettaja_puhelinnumero,
   :lahettaja_sahkoposti);

-- name: paivita-ilmoitus!
-- Päivittää havainnon
UPDATE ilmoitus
SET
  urakka                  = :urakka,
  ilmoitusid              = :ilmoitusid,
  ilmoitettu              = :ilmoitettu,
  valitetty               = :valitetty,
  yhteydenottopyynto      = :yhteydenottopyynto,
  vapaateksti             = :vapaateksti,
  ilmoitustyyppi          = :ilmoitustyyppi :: ilmoitustyyppi,
  selitteet               = :selitteet :: ilmoituksenselite [],
  ilmoittaja_etunimi      = :ilmoittaja_etunimi,
  ilmoittaja_sukunimi     = :ilmoittaja_sukunimi,
  ilmoittaja_tyopuhelin   = :ilmoittaja_tyopuhelin,
  ilmoittaja_matkapuhelin = :ilmoittaja_matkapuhelin,
  ilmoittaja_sahkoposti   = :ilmoittaja_sahkoposti,
  ilmoittaja_tyyppi       = :ilmoittaja_tyyppi :: ilmoittajatyyppi,
  lahettaja_etunimi       = :lahettaja_etunimi,
  lahettaja_sukunimi      = :lahettaja_sukunimi,
  lahettaja_puhelinnumero = :lahettaja_puhelinnumero,
  lahettaja_sahkoposti    = :lahettaja_sahkoposti
WHERE id = :id;

-- name: aseta-ilmoituksen-sijainti!
-- Asettaa ilmoituksen sijaintitiedot. Joudutaan tekemään erikseen YESQL:n 20 muuttujan rajoituksen vuoksi.
UPDATE ilmoitus
SET
  tr_numero = :tr_numero,
  sijainti  = POINT(:x_koordinaatti, :y_koordinaatti)
WHERE id = :id;

-- name: hae-ilmoituksen-urakka
-- Hakee sijainnin ja urakan tyypin perusteella urakan, johon ilmoitus liittyy. Urakan täytyy myös olla käynnissä.
SELECT u.id
FROM urakoiden_alueet ua
  JOIN urakka u ON ua.id = u.id
WHERE
  ua.tyyppi = :urakkatyyppi :: urakkatyyppi AND
  (st_contains(ua.alue, ST_MakePoint(:x, :y))) AND
  (u.loppupvm IS NULL OR u.loppupvm > current_timestamp);