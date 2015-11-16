-- name: hae-ilmoitukset
SELECT
  i.id,
  i.urakka,
  (SELECT hallintayksikko
   FROM urakka
   WHERE id = i.urakka)               AS hallintayksikko,
  i.ilmoitusid,
  i.ilmoitettu,
  i.valitetty,
  i.yhteydenottopyynto,
  -- todo: tämä täytyy korjata hakemaan oikeat tiedot
  i.lyhytselite                       AS vapaateksti,
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

  it.id                               AS kuittaus_id,
  it.kuitattu                         AS kuittaus_kuitattu,
  it.vapaateksti                      AS kuittaus_vapaateksti,
  it.kuittaustyyppi                   AS kuittaus_kuittaustyyppi,

  it.kuittaaja_henkilo_etunimi        AS kuittaus_kuittaaja_etunimi,
  it.kuittaaja_henkilo_sukunimi       AS kuittaus_kuittaaja_sukunimi,
  it.kuittaaja_henkilo_matkapuhelin   AS kuittaus_kuittaaja_matkapuhelin,
  it.kuittaaja_henkilo_tyopuhelin     AS kuittaus_kuittaaja_tyopuhelin,
  it.kuittaaja_henkilo_sahkoposti     AS kuittaus_kuittaaja_sahkoposti,
  it.kuittaaja_organisaatio_nimi      AS kuittaus_kuittaaja_organisaatio,
  it.kuittaaja_organisaatio_ytunnus   AS kuittaus_kuittaaja_ytunnus,

  it.kasittelija_henkilo_etunimi      AS kuittaus_kasittelija_etunimi,
  it.kasittelija_henkilo_sukunimi     AS kuittaus_kasittelija_sukunimi,
  it.kasittelija_henkilo_matkapuhelin AS kuittaus_kasittelija_matkapuhelin,
  it.kasittelija_henkilo_tyopuhelin   AS kuittaus_kasittelija_tyopuhelin,
  it.kasittelija_henkilo_sahkoposti   AS kuittaus_kasittelija_sahkoposti,
  it.kasittelija_organisaatio_nimi    AS kuittaus_kasittelija_organisaatio,
  it.kasittelija_organisaatio_ytunnus AS kuittaus_kasittelija_ytunnus


FROM ilmoitus i
  LEFT JOIN ilmoitustoimenpide it ON it.ilmoitus = i.id
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
    i.otsikko LIKE :teksti OR
    i.lyhytselite LIKE :teksti OR
    i.pitkaselite LIKE :teksti
  ) AND

  -- Tarkasta ilmoituksen tilat
  (
    (:suljetut IS TRUE AND :avoimet IS TRUE) OR
    (:suljetut IS FALSE AND :avoimet IS FALSE) OR
    (:suljetut IS TRUE AND i.suljettu IS TRUE) OR
    (:avoimet IS TRUE AND i.suljettu IS NOT TRUE)
  )
ORDER BY i.ilmoitettu ASC, it.kuitattu ASC;


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
 otsikko,
 lyhytselite,
 pitkaselite,
 ilmoitustyyppi,
 selitteet,
 urakkatyyppi)
VALUES
  (:urakka,
    :ilmoitusid,
    :ilmoitettu,
    :valitetty,
    :yhteydenottopyynto,
    :otsikko,
    :lyhytselite,
    :pitkaselite,
    :ilmoitustyyppi :: ilmoitustyyppi,
    :selitteet :: ilmoituksenselite [],
    :urakkatyyppi :: urakkatyyppi);

-- name: paivita-ilmoitus!
-- Päivittää havainnon
UPDATE ilmoitus
SET
  urakka             = :urakka,
  ilmoitusid         = :ilmoitusid,
  ilmoitettu         = :ilmoitettu,
  valitetty          = :valitetty,
  yhteydenottopyynto = :yhteydenottopyynto,
  otsikko            = :otsikko,
  lyhytselite        = :lyhytselite,
  pitkaselite        = :pitkaselite,
  ilmoitustyyppi     = :ilmoitustyyppi :: ilmoitustyyppi,
  selitteet          = :selitteet :: ilmoituksenselite []
WHERE id = :id;

-- name: paivita-ilmoittaja-ilmoitukselle!
UPDATE ilmoitus
SET
  ilmoittaja_etunimi      = :ilmoittaja_etunimi,
  ilmoittaja_sukunimi     = :ilmoittaja_sukunimi,
  ilmoittaja_tyopuhelin   = :ilmoittaja_tyopuhelin,
  ilmoittaja_matkapuhelin = :ilmoittaja_matkapuhelin,
  ilmoittaja_sahkoposti   = :ilmoittaja_sahkoposti,
  ilmoittaja_tyyppi       = :ilmoittaja_tyyppi :: ilmoittajatyyppi
WHERE id = :id;

-- name: paivita-lahettaja-ilmoitukselle!
UPDATE ilmoitus
SET
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
  sijainti  = POINT(:x_koordinaatti, :y_koordinaatti) :: GEOMETRY
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

-- name: hae-ilmoitustoimenpide
SELECT
  id                               AS id,
  ilmoitusid                       AS ilmoitusid,
  kuitattu                         AS kuitattu,
  vapaateksti                      AS vapaateksti,
  kuittaustyyppi                   AS kuittaustyyppi,
  kuittaaja_henkilo_etunimi        AS kuittaaja_etunimi,
  kuittaaja_henkilo_sukunimi       AS kuittaaja_sukunimi,
  kuittaaja_henkilo_matkapuhelin   AS kuittaaja_matkapuhelin,
  kuittaaja_henkilo_tyopuhelin     AS kuittaaja_tyopuhelin,
  kuittaaja_henkilo_sahkoposti     AS kuittaaja_sahkoposti,
  kuittaaja_organisaatio_nimi      AS kuittaaja_organisaatio,
  kuittaaja_organisaatio_ytunnus   AS kuittaaja_ytunnus,
  kasittelija_henkilo_etunimi      AS kasittelija_etunimi,
  kasittelija_henkilo_sukunimi     AS kasittelija_sukunimi,
  kasittelija_henkilo_matkapuhelin AS kasittelija_matkapuhelin,
  kasittelija_henkilo_tyopuhelin   AS kasittelija_tyopuhelin,
  kasittelija_henkilo_sahkoposti   AS kasittelija_sahkoposti,
  kasittelija_organisaatio_nimi    AS kasittelija_organisaatio,
  kasittelija_organisaatio_ytunnus AS kasittelija_ytunnus
FROM ilmoitustoimenpide
WHERE id = :id;

-- name: merkitse-ilmoitustoimenpide-odottamaan-vastausta!
UPDATE ilmoitustoimenpide
SET lahetysid = :lahetysid, tila = 'odottaa_vastausta'
WHERE id = :id;

-- name: merkitse-ilmoitustoimenpide-lahetetyksi!
UPDATE ilmoitustoimenpide
SET lahetetty = current_timestamp, tila = 'lahetetty'
WHERE lahetysid = :lahetysid;

-- name: merkitse-ilmoitustoimenpidelle-lahetysvirhe!
UPDATE ilmoitustoimenpide
SET tila = 'virhe'
WHERE id = :id;
