-- name: hae-ilmoitukset
-- Tässä on tuplaselect, koska halutaan tehdä LIMIT pelkästään ilmoituksille, ei saa koskea ilmoitustoimenpiteitä.
SELECT
  ulompi_i.id,
  ulompi_i.urakka,
  ulompi_i.tunniste,
  u.nimi as urakkanimi,
  ulompi_i.ilmoitusid,
  --ulompi_i.ilmoitettu, TODO VHAR-1754 Väliaikaisesti. Välitetty = ilmoitettu kunnes ilmoitettu-tieto otetaan käyttöön UIlla.
  ulompi_i.valitetty as ilmoitettu,
  ulompi_i.valitetty,
  ulompi_i.yhteydenottopyynto,
  ulompi_i.otsikko,
  ulompi_i.lisatieto,
  ulompi_i.ilmoitustyyppi,
  ulompi_i.selitteet,
  ulompi_i.urakkatyyppi,
  ulompi_i.tila,
  ulompi_i.sijainti,
  ulompi_i.tr_numero,
  ulompi_i.tr_alkuosa,
  ulompi_i.tr_loppuosa,
  ulompi_i.tr_alkuetaisyys,
  ulompi_i.tr_loppuetaisyys,
  ulompi_i."aiheutti-toimenpiteita",
  ulompi_i."toimenpiteet-aloitettu",
  it.id                                                              AS kuittaus_id,
  it.kuitattu                                                        AS kuittaus_kuitattu,
  it.kuittaustyyppi                                                  AS kuittaus_kuittaustyyppi,
  it.kuittaaja_henkilo_etunimi                                       AS kuittaus_kuittaaja_etunimi,
  it.kuittaaja_henkilo_sukunimi                                      AS kuittaus_kuittaaja_sukunimi,
  hy.id                                                              AS hallintayksikko_id,
  hy.nimi                                                            AS hallintayksikko_nimi
FROM ilmoitus ulompi_i
  LEFT JOIN ilmoitustoimenpide it ON it.ilmoitus = ulompi_i.id
  LEFT JOIN urakka u ON ulompi_i.urakka = u.id
  LEFT JOIN organisaatio hy ON (u.hallintayksikko = hy.id AND hy.tyyppi = 'hallintayksikko')
WHERE ulompi_i.id IN
      (SELECT id FROM ilmoitus sisempi_i WHERE
       -- Tarkasta että ilmoituksen geometria sopii hakuehtoihin
      (sisempi_i.urakka IS NULL OR sisempi_i.urakka IN (:urakat)) AND

      -- Tarkasta että ilmoituksen ilmoitusajankohta sopii hakuehtoihin
      -- TODO VHAR-1754 Väliaikaisesti. Ilmoitettu korvattu sarakkeella valitetty kolme kertaa
      ((:alku_annettu IS FALSE AND :loppu_annettu IS FALSE) OR
       (:loppu_annettu IS FALSE AND sisempi_i.valitetty  >= :alku) OR
       (:alku_annettu IS FALSE AND sisempi_i.valitetty  <= :loppu) OR
       (sisempi_i.valitetty  BETWEEN :alku AND :loppu)) AND

      -- Tarkasta että ilmoituksen toimenpiteiden aloitus sopii hakuehtoihin
      ((:toimenpiteet_alku_annettu IS FALSE AND :toimenpiteet_loppu_annettu IS FALSE) OR
       (:toimenpiteet_loppu_annettu IS FALSE AND sisempi_i."toimenpiteet-aloitettu"  >= :toimenpiteet_alku) OR
       (:toimenpiteet_alku_annettu IS FALSE AND sisempi_i."toimenpiteet-aloitettu"  <= :toimenpiteet_loppu) OR
       (sisempi_i."toimenpiteet-aloitettu"  BETWEEN :toimenpiteet_alku AND :toimenpiteet_loppu)) AND

      -- Tarkista ilmoituksen tilat
      ((:kuittaamattomat IS TRUE AND sisempi_i.tila = 'kuittaamaton' :: ilmoituksen_tila) OR
       (:vastaanotetut IS TRUE AND sisempi_i.tila = 'vastaanotettu' :: ilmoituksen_tila) OR
       (:aloitetut IS TRUE AND sisempi_i.tila = 'aloitettu' :: ilmoituksen_tila) OR
       (:lopetetut IS TRUE AND sisempi_i.tila = 'lopetettu' :: ilmoituksen_tila)) AND

      -- Tarkasta ilmoituksen tyypit
      (:tyypit_annettu IS FALSE OR sisempi_i.ilmoitustyyppi :: TEXT IN (:tyypit)) AND

      -- Tarkasta vapaatekstihakuehto
      (:teksti_annettu IS FALSE OR (sisempi_i.otsikko LIKE :teksti OR sisempi_i.paikankuvaus LIKE :teksti OR sisempi_i.lisatieto LIKE :teksti)) AND

      -- Tarkasta selitehakuehto
      (:selite_annettu IS FALSE OR (sisempi_i.selitteet @> ARRAY [:selite :: TEXT])) AND

      -- Rajaa tienumerolla
      (:tr-numero::INTEGER IS NULL OR tr_numero = :tr-numero) AND

      -- Rajaa tunnisteella
      (:tunniste_annettu IS FALSE OR (sisempi_i.tunniste ILIKE :tunniste)) AND

      -- Rajaa ilmoittajan nimellä
      (:ilmoittaja-nimi::TEXT IS NULL OR
       CONCAT(sisempi_i.ilmoittaja_etunimi,' ',sisempi_i.ilmoittaja_sukunimi) ILIKE :ilmoittaja-nimi) AND

      -- Rajaa ilmoittajan puhelinnumerolla
      (:ilmoittaja-puhelin::TEXT IS NULL OR
       sisempi_i.ilmoittaja_matkapuhelin LIKE :ilmoittaja-puhelin)
      -- TODO VHAR-1754 Väliaikaisesti. Ilmoitettu korvattu sarakkeella valitetty kaksi kertaa.
       ORDER BY sisempi_i.valitetty DESC
       LIMIT :max-maara::INTEGER)
ORDER BY ulompi_i.valitetty DESC, it.kuitattu DESC;

-- name: hae-ilmoitukset-raportille
SELECT
  i.urakka,
  -- i.ilmoitettu,
  i.valitetty as ilmoitettu,        -- TODO VHAR-1754 Väliaikaisesti. Välitetty = ilmoitettu kunnes ilmoitettu-tieto otetaan käyttöön UIlla.
  i.ilmoitustyyppi,
  hy.id                                                              AS hallintayksikko_id,
  hy.nimi                                                            AS hallintayksikko_nimi,
  lpad(cast(hy.elynumero as varchar), 2, '0')                        AS hallintayksikko_elynumero
FROM ilmoitus i
  LEFT JOIN urakka u ON i.urakka = u.id
  LEFT JOIN organisaatio hy ON (u.hallintayksikko = hy.id AND hy.tyyppi = 'hallintayksikko')
WHERE i.id IN
      (SELECT x.id
       FROM ilmoitus x
         LEFT JOIN urakka u2 ON x.urakka = u2.id
       WHERE
         (x.urakka IS NULL
          OR :urakat_annettu IS FALSE
          OR (:urakat_annettu IS TRUE AND x.urakka IN (:urakat))) AND
         (:urakkatyyppi_annettu IS FALSE OR u2.tyyppi = :urakkatyyppi::urakkatyyppi) AND
         (x.urakka IS NULL OR u2.urakkanro IS NOT NULL) AND -- Ei-testiurakka
         -- Tarkasta että ilmoituksen saapumisajankohta sopii hakuehtoihin
         ((:alku_annettu IS FALSE AND :loppu_annettu IS FALSE) OR
          -- TODO VHAR-1754 Väliaikaisesti. Ilmoitettu on korvattu sarakkeella valitetty kolme kertaa.
          (:loppu_annettu IS FALSE AND x.valitetty >= :alku) OR
          (:alku_annettu IS FALSE AND x.valitetty <= :loppu) OR
          (x.valitetty BETWEEN :alku AND :loppu)))
ORDER by u.nimi;

-- name: hae-ilmoitukset-ilmoitusidlla
SELECT
  ilmoitusid,
  tunniste,
  tila,
  -- ilmoitettu, -- TODO VHAR-1754 Väliaikaisesti. Välitetty = ilmoitettu kunnes ilmoitettu-tieto otetaan käyttöön UIlla.
  valitetty as ilmoitettu, -- TEMP. Ks. kommentti yllä.
  "vastaanotettu-alunperin" as "valitetty-harjaan",
  CASE
      WHEN ("vastaanotettu-alunperin" = vastaanotettu) THEN NULL
  ELSE
      vastaanotettu
  END as "paivitetty-harjaan",
  yhteydenottopyynto,
  paikankuvaus,
  lisatieto,
  otsikko,
  ilmoitustyyppi,
  selitteet,
  sijainti,
  tr_numero,
  tr_alkuosa,
  tr_loppuosa,
  tr_alkuetaisyys,
  tr_loppuetaisyys,
  ilmoittaja_etunimi,
  ilmoittaja_sukunimi,
  ilmoittaja_tyopuhelin,
  ilmoittaja_matkapuhelin,
  ilmoittaja_sahkoposti,
  lahettaja_etunimi,
  lahettaja_sukunimi,
  lahettaja_puhelinnumero,
  lahettaja_sahkoposti,
  "aiheutti-toimenpiteita"
FROM ilmoitus
WHERE ilmoitusid IN (:ilmoitusidt);

-- name: hae-ilmoitus
SELECT
  i.id,
  i.urakka,
  u.nimi as urakkanimi,
  hy.id                                    AS hallintayksikko_id,
  hy.nimi                                  AS hallintayksikko_nimi,
  i.ilmoitusid,
  -- i.ilmoitettu, -- TODO VHAR-1754 Väliaikaisesti. Välitetty = ilmoitettu kunnes ilmoitettu-tieto otetaan käyttöön UIlla.
  i.valitetty as ilmoitettu,
  i.valitetty,
  i.yhteydenottopyynto,
  i.otsikko,
  i.paikankuvaus,
  i.lisatieto,
  i.ilmoitustyyppi,
  i.selitteet,
  i.urakkatyyppi,
  i.tila,

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

  i.tunniste,
  i."aiheutti-toimenpiteita",
  i."toimenpiteet-aloitettu",

  it.id                                    AS kuittaus_id,
  it.kuitattu                              AS kuittaus_kuitattu,
  it.vakiofraasi                           AS kuittaus_vakiofraasi,
  it.vapaateksti                           AS kuittaus_vapaateksti,
  it.kuittaustyyppi                        AS kuittaus_kuittaustyyppi,
  it.kanava                                AS kuittaus_kanava,
  it.suunta                                AS kuittaus_suunta,

  it.kuittaaja_henkilo_etunimi             AS kuittaus_kuittaaja_etunimi,
  it.kuittaaja_henkilo_sukunimi            AS kuittaus_kuittaaja_sukunimi,
  it.kuittaaja_henkilo_matkapuhelin        AS kuittaus_kuittaaja_matkapuhelin,
  it.kuittaaja_henkilo_tyopuhelin          AS kuittaus_kuittaaja_tyopuhelin,
  it.kuittaaja_henkilo_sahkoposti          AS kuittaus_kuittaaja_sahkoposti,
  it.kuittaaja_organisaatio_nimi           AS kuittaus_kuittaaja_organisaatio,
  it.kuittaaja_organisaatio_ytunnus        AS kuittaus_kuittaaja_ytunnus,

  it.kasittelija_henkilo_etunimi           AS kuittaus_kasittelija_etunimi,
  it.kasittelija_henkilo_sukunimi          AS kuittaus_kasittelija_sukunimi,
  it.kasittelija_henkilo_matkapuhelin      AS kuittaus_kasittelija_matkapuhelin,
  it.kasittelija_henkilo_tyopuhelin        AS kuittaus_kasittelija_tyopuhelin,
  it.kasittelija_henkilo_sahkoposti        AS kuittaus_kasittelija_sahkoposti,
  it.kasittelija_organisaatio_nimi         AS kuittaus_kasittelija_organisaatio,
  it.kasittelija_organisaatio_ytunnus      AS kuittaus_kasittelija_ytunnus

FROM ilmoitus i
  LEFT JOIN ilmoitustoimenpide it ON it.ilmoitus = i.id
  LEFT JOIN urakka u ON i.urakka = u.id
  LEFT JOIN organisaatio hy ON (u.hallintayksikko = hy.id AND hy.tyyppi = 'hallintayksikko')
WHERE i.id = :id;

-- name: hae-ilmoitukset-idlla
SELECT
  i.id,
  i.ilmoitusid,
  i.ilmoitustyyppi,
  i.urakka,
  i.urakkatyyppi,
  -- i.ilmoitettu, -- TODO VHAR-1754 Väliaikaisesti. Välitetty = ilmoitettu kunnes ilmoitettu-tieto otetaan käyttöön UIlla.
  i.valitetty as ilmoitettu,
  i.valitetty,
  i.yhteydenottopyynto,
  i.otsikko,
  i.paikankuvaus,
  i.lisatieto,
  i.selitteet,

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
  it.id                                                              AS kuittaus_id,
  it.kuitattu                                                        AS kuittaus_kuitattu,
  it.vapaateksti                                                     AS kuittaus_vapaateksti,
  it.kuittaustyyppi                                                  AS kuittaus_kuittaustyyppi,

  it.kuittaaja_henkilo_etunimi                                       AS kuittaus_kuittaaja_etunimi,
  it.kuittaaja_henkilo_sukunimi                                      AS kuittaus_kuittaaja_sukunimi,
  it.kuittaaja_henkilo_matkapuhelin                                  AS kuittaus_kuittaaja_matkapuhelin,
  it.kuittaaja_henkilo_tyopuhelin                                    AS kuittaus_kuittaaja_tyopuhelin,
  it.kuittaaja_henkilo_sahkoposti                                    AS kuittaus_kuittaaja_sahkoposti,
  it.kuittaaja_organisaatio_nimi                                     AS kuittaus_kuittaaja_organisaatio,
  it.kuittaaja_organisaatio_ytunnus                                  AS kuittaus_kuittaaja_ytunnus,

  it.kasittelija_henkilo_etunimi                                     AS kuittaus_kasittelija_etunimi,
  it.kasittelija_henkilo_sukunimi                                    AS kuittaus_kasittelija_sukunimi,
  it.kasittelija_henkilo_matkapuhelin                                AS kuittaus_kasittelija_matkapuhelin,
  it.kasittelija_henkilo_tyopuhelin                                  AS kuittaus_kasittelija_tyopuhelin,
  it.kasittelija_henkilo_sahkoposti                                  AS kuittaus_kasittelija_sahkoposti,
  it.kasittelija_organisaatio_nimi                                   AS kuittaus_kasittelija_organisaatio,
  it.kasittelija_organisaatio_ytunnus                                AS kuittaus_kasittelija_ytunnus
FROM ilmoitus i
  LEFT JOIN ilmoitustoimenpide it ON i.id = it.ilmoitus
WHERE i.id IN (:idt);

-- name: hae-muuttuneet-ilmoitukset
SELECT
  ilmoitusid,
  tunniste,
  tila,
  -- ilmoitettu, -- TODO VHAR-1754 Väliaikaisesti. Välitetty = ilmoitettu kunnes ilmoitettu-tieto otetaan käyttöön UIlla.
  valitetty as ilmoitettu, -- TEMP. Ks. kommentti yllä.
  "vastaanotettu-alunperin" as "valitetty-harjaan",
  CASE
      WHEN ("vastaanotettu-alunperin" = vastaanotettu) THEN NULL
  ELSE
      vastaanotettu
  END as "paivitetty-harjaan",
  yhteydenottopyynto,
  paikankuvaus,
  lisatieto,
  otsikko,
  ilmoitustyyppi,
  selitteet,
  sijainti,
  tr_numero,
  tr_alkuosa,
  tr_loppuosa,
  tr_alkuetaisyys,
  tr_loppuetaisyys,
  ilmoittaja_etunimi,
  ilmoittaja_sukunimi,
  ilmoittaja_tyopuhelin,
  ilmoittaja_matkapuhelin,
  ilmoittaja_sahkoposti,
  lahettaja_etunimi,
  lahettaja_sukunimi,
  lahettaja_puhelinnumero,
  lahettaja_sahkoposti,
  "aiheutti-toimenpiteita"
FROM ilmoitus
WHERE urakka = :urakka AND
      (muokattu > :aika OR luotu > :aika);


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
 paikankuvaus,
 lisatieto,
 ilmoitustyyppi,
 selitteet,
 urakkatyyppi,
 tunniste,
 viestiid,
 vastaanotettu,
 "vastaanotettu-alunperin")
VALUES
  (:urakka,
    :ilmoitusid,
    :ilmoitettu,
    :valitetty,
    :yhteydenottopyynto,
    :otsikko,
    :paikankuvaus,
    :lisatieto,
    :ilmoitustyyppi :: ILMOITUSTYYPPI,
    :selitteet :: TEXT [],
    :urakkatyyppi :: URAKKATYYPPI,
   :tunniste,
   :viestiid,
   :vastaanotettu :: TIMESTAMPTZ,
   :vastaanotettu-alunperin :: TIMESTAMPTZ);

-- name: paivita-ilmoitus!
-- Päivittää ilmoituksen
UPDATE ilmoitus
SET
  urakka             = :urakka,
  ilmoitusid         = :ilmoitusid,
  ilmoitettu         = :ilmoitettu,
  valitetty          = :valitetty,
  vastaanotettu      = :vastaanotettu,
  yhteydenottopyynto = :yhteydenottopyynto,
  otsikko            = :otsikko,
  paikankuvaus       = :paikankuvaus,
  lisatieto          = :lisatieto,
  ilmoitustyyppi     = :ilmoitustyyppi :: ILMOITUSTYYPPI,
  selitteet          = :selitteet :: TEXT [],
  tunniste           = :tunniste,
  muokattu           = NOW(),
  viestiid           = :viestiid
WHERE id = :id;

-- name: paivita-ilmoittaja-ilmoitukselle!
UPDATE ilmoitus
SET
  ilmoittaja_etunimi      = :ilmoittaja_etunimi,
  ilmoittaja_sukunimi     = :ilmoittaja_sukunimi,
  ilmoittaja_tyopuhelin   = :ilmoittaja_tyopuhelin,
  ilmoittaja_matkapuhelin = :ilmoittaja_matkapuhelin,
  ilmoittaja_sahkoposti   = :ilmoittaja_sahkoposti,
  ilmoittaja_tyyppi       = :ilmoittaja_tyyppi
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

-- name: hae-ilmoitustoimenpide
SELECT
  id                               AS id,
  ilmoitus                         AS ilmoitus,
  ilmoitusid                       AS ilmoitusid,
  kuitattu                         AS kuitattu,
  vakiofraasi                      AS vakiofraasi,
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

-- name: merkitse-ilmoitustoimenpidelle-lahetysvirhe-idlla!
UPDATE ilmoitustoimenpide
SET tila = 'virhe'
WHERE id = :id;

-- name: merkitse-ilmoitustoimenpidelle-lahetysvirhe-lahetysidlla!
UPDATE ilmoitustoimenpide
SET tila = 'virhe'
WHERE lahetysid = :lahetysid;

-- name: onko-ilmoitukselle-vastaanottokuittausta
SELECT id
FROM ilmoitustoimenpide
WHERE ilmoitus = (SELECT id
                  FROM ilmoitus
                  WHERE ilmoitusid = :ilmoitusid
                  LIMIT 1) AND
      kuittaustyyppi = 'vastaanotto';

-- name: luo-ilmoitustoimenpide<!
INSERT INTO ilmoitustoimenpide
(ilmoitus,
 ilmoitusid,
 kuitattu,
 vakiofraasi,
 vapaateksti,
 kuittaustyyppi,
 suunta,
 kanava,
 tila,
 kuittaaja_henkilo_etunimi,
 kuittaaja_henkilo_sukunimi,
 kuittaaja_henkilo_matkapuhelin,
 kuittaaja_henkilo_tyopuhelin,
 kuittaaja_henkilo_sahkoposti,
 kuittaaja_organisaatio_nimi,
 kuittaaja_organisaatio_ytunnus,
 kasittelija_henkilo_etunimi,
 kasittelija_henkilo_sukunimi,
 kasittelija_henkilo_matkapuhelin,
 kasittelija_henkilo_tyopuhelin,
 kasittelija_henkilo_sahkoposti,
 kasittelija_organisaatio_nimi,
 kasittelija_organisaatio_ytunnus)
VALUES
  (:ilmoitus,
    :ilmoitusid,
    current_timestamp,
    :vakiofraasi,
    :vapaateksti,
    :kuittaustyyppi,
    :suunta :: viestisuunta,
    :kanava :: viestikanava,
    :tila :: lahetyksen_tila,
    :kuittaaja_henkilo_etunimi,
    :kuittaaja_henkilo_sukunimi,
    :kuittaaja_henkilo_matkapuhelin,
    :kuittaaja_henkilo_tyopuhelin,
    :kuittaaja_henkilo_sahkoposti,
   :kuittaaja_organisaatio_nimi,
   :kuittaaja_organisaatio_ytunnus,
   :kasittelija_henkilo_etunimi,
   :kasittelija_henkilo_sukunimi,
   :kasittelija_henkilo_matkapuhelin,
   :kasittelija_henkilo_tyopuhelin,
   :kasittelija_henkilo_sahkoposti,
   :kasittelija_organisaatio_nimi,
   :kasittelija_organisaatio_ytunnus);

-- name: hae-ilmoitus-ilmoitus-idlla
-- Hakee ilmoituksen T-LOIK ilmoitus id:n perusteella
SELECT
  id,
  ilmoitusid,
  tunniste,
  ilmoitustyyppi,
  urakka
FROM ilmoitus
WHERE ilmoitusid = :ilmoitusid;

-- name: hae-ilmoitukset-asiakaspalauteluokittain
-- Hakee summat kaikille asiakaspalauteluokille jaoteltuina ilmoitustyypeittäin
SELECT
  apl.nimi,
  i.ilmoitustyyppi,
  (coalesce(SUM(1), 0)) AS numero
FROM asiakaspalauteluokka apl
  JOIN ilmoitus i
    ON i.selitteet && apl.selitteet AND
       (:urakka_id :: INTEGER IS NULL OR i.urakka = :urakka_id) AND
       (i.urakka IS NULL OR (SELECT urakkanro FROM urakka WHERE id = i.urakka) IS NOT NULL) AND -- Ei-testiurakka
       (:urakkatyyppi::urakkatyyppi IS NULL OR (SELECT tyyppi FROM urakka WHERE id = i.urakka) = :urakkatyyppi::urakkatyyppi) AND
       (:hallintayksikko_id :: INTEGER IS NULL OR i.urakka IN (SELECT id
                                                               FROM urakka
                                                               WHERE hallintayksikko = :hallintayksikko_id)) AND
        -- TODO VHAR-1754 Väliaikaisesti. Ilmoitettu on korvattu sarakkeella valitetty kaksi kertaa.
       (:alkupvm :: DATE IS NULL OR i.valitetty >= :alkupvm) AND
       (:loppupvm :: DATE IS NULL OR i.valitetty <= :loppupvm)
GROUP BY CUBE(apl.nimi, i.ilmoitustyyppi);

-- name: hae-ilmoitukset-aiheutuneiden-toimenpiteiden-mukaan
SELECT
  count(*)
    FILTER (WHERE "aiheutti-toimenpiteita" IS TRUE)     AS "toimenpiteita-aiheuttaneet",
  count(*)
    FILTER (WHERE "aiheutti-toimenpiteita" IS NOT TRUE) AS "ei-toimenpiteita-aiheuttaneet",
  count(*)                                              AS "yhteensa"
FROM
  ilmoitus
  LEFT JOIN urakka u ON ilmoitus.urakka = u.id
WHERE
  (:urakka_id :: INTEGER IS NULL OR urakka = :urakka_id) AND
  (ilmoitus.urakka IS NULL OR u.urakkanro IS NOT NULL) AND
  (:urakkatyyppi::urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi::urakkatyyppi) AND
  (:hallintayksikko_id :: INTEGER IS NULL OR urakka IN (SELECT id
                                                          FROM urakka
                                                          WHERE hallintayksikko = :hallintayksikko_id)) AND
  -- TODO VHAR-1754 Väliaikaisesti. Ilmoitettu on korvattu sarakkeella valitetty kaksi kertaa.
  (:alkupvm :: DATE IS NULL OR valitetty >= :alkupvm) AND
  (:loppupvm :: DATE IS NULL OR valitetty <= :loppupvm);


-- name: hae-lahettamattomat-ilmoitustoimenpiteet
SELECT id
FROM ilmoitustoimenpide
WHERE
  (tila IS NULL OR tila = 'virhe') AND
  kuittaustyyppi != 'valitys';

-- name: hae-ilmoituksen-tieosoite
SELECT
  tr_numero as "tr-numero",
  tr_alkuosa as "tr-alkuosa",
  tr_alkuetaisyys as "tr-alkuetaisyys",
  tr_loppuosa as "tr-loppuosa",
  tr_loppuetaisyys as "tr-loppuetaisyys"
FROM ilmoitus
WHERE id = :id;

-- name: hae-ilmoituskuittausten-urakat
SELECT DISTINCT(urakka) FROM ilmoitus WHERE id IN (:ilmoitusidt);

-- name: ilmoitus-aiheutti-toimenpiteita!
UPDATE ilmoitus
SET "aiheutti-toimenpiteita" = :aiheutti-toimenpiteita
WHERE id = :id;

-- name: ilmoitus-loytyy-viesti-idlla
SELECT exists(SELECT
              FROM ilmoitus
              WHERE ilmoitusid = :ilmoitusid AND
                    viestiid = :viestiid);

-- name: ilmoituksen-alkuperainen-kesto
SELECT extract(EPOCH FROM (SELECT vastaanotettu - "vastaanotettu-alunperin"
                           FROM ilmoitus
                           WHERE id = :id));

-- name: tallenna-ilmoitusten-toimenpiteiden-aloitukset!
UPDATE ilmoitus
SET "toimenpiteet-aloitettu" = now(),
  "aiheutti-toimenpiteita"   = TRUE
WHERE id in (:idt);

-- name: peruuta-ilmoitusten-toimenpiteiden-aloitukset!
UPDATE ilmoitus
SET "toimenpiteet-aloitettu" = null,
  "aiheutti-toimenpiteita"   = false
WHERE id in (:idt);
