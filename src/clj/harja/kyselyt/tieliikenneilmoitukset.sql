-- name: hae-ilmoitukset
-- Tässä on tuplaselect, koska halutaan tehdä LIMIT pelkästään ilmoituksille, ei saa koskea ilmoitustoimenpiteitä.
SELECT
  ulompi_i.id,
  ulompi_i.urakka,
  ulompi_i.tunniste,
  u.nimi AS urakkanimi,
  u.lyhyt_nimi AS lyhytnimi,
  CASE
      WHEN u.kesakausi_alkupvm IS NOT NULL THEN
          CONCAT(TO_CHAR(NOW(), 'YYYY'), '-', TO_CHAR(u.kesakausi_alkupvm, 'MM-DD'))
      END AS "urakka-kesakausi-alkupvm",
  CASE
      WHEN u.kesakausi_loppupvm IS NOT NULL THEN
          CONCAT(TO_CHAR(NOW(), 'YYYY'), '-', TO_CHAR(u.kesakausi_loppupvm, 'MM-DD'))
      END AS "urakka-kesakausi-loppupvm",
  ulompi_i.ilmoitusid,
  ulompi_i.ilmoitettu,
  ulompi_i.valitetty,
  ulompi_i."valitetty-urakkaan",
  ulompi_i.yhteydenottopyynto,
  ulompi_i.otsikko,
  ulompi_i.lisatieto,
  ulompi_i.ilmoitustyyppi,
  ulompi_i.selitteet,
  ulompi_i.kuvat,
  ulompi_i."emon-ilmoitusid",
  ulompi_i.aihe,
  ulompi_i.tarkenne,
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

      -- Tarkasta että ilmoituksen valitysajankohta sopii hakuehtoihin. Tarkastellaan ilmoituksen urakkaan välittymistä.
      ((:alku_annettu IS FALSE AND :loppu_annettu IS FALSE) OR
       (:loppu_annettu IS FALSE AND sisempi_i."valitetty-urakkaan"  >= :alku::TIMESTAMP) OR
       (:alku_annettu IS FALSE AND sisempi_i."valitetty-urakkaan"  <= :loppu::TIMESTAMP) OR
       (sisempi_i."valitetty-urakkaan" BETWEEN :alku::TIMESTAMP AND :loppu::TIMESTAMP)) AND

      -- Tarkasta että ilmoituksen toimenpiteiden aloitus sopii hakuehtoihin
      ((:toimenpiteet_alku_annettu IS FALSE AND :toimenpiteet_loppu_annettu IS FALSE) OR
       (:toimenpiteet_loppu_annettu IS FALSE AND sisempi_i."toimenpiteet-aloitettu"  >= :toimenpiteet_alku::TIMESTAMP) OR
       (:toimenpiteet_alku_annettu IS FALSE AND sisempi_i."toimenpiteet-aloitettu"  <= :toimenpiteet_loppu::TIMESTAMP) OR
       (sisempi_i."toimenpiteet-aloitettu" BETWEEN :toimenpiteet_alku::TIMESTAMP AND :toimenpiteet_loppu::TIMESTAMP)) AND

      -- Tarkista ilmoituksen tilat
      -- jos tila sisältää kuittaamattomat, huomioidaan myös harvinainen erikoistapaus eli ne, jotka ovat kuittaamattomia mutta joiden
      -- vastaanottoa ei ole vielä välitetty T-Loikiin, joiden tila = 'ei-valitetty'
      ((:kuittaamattomat IS TRUE AND sisempi_i.tila IN ('kuittaamaton', 'ei-valitetty')) OR
       (:vastaanotetut IS TRUE AND sisempi_i.tila = 'vastaanotettu' :: ilmoituksen_tila) OR
       (:aloitetut IS TRUE AND sisempi_i.tila = 'aloitettu' :: ilmoituksen_tila) OR
       (:lopetetut IS TRUE AND sisempi_i.tila = 'lopetettu' :: ilmoituksen_tila)) AND

      -- Tarkasta ilmoituksen tyypit
      (:tyypit_annettu IS FALSE OR sisempi_i.ilmoitustyyppi :: TEXT IN (:tyypit)) AND

      -- Tarkasta vapaatekstihakuehto
      (:teksti_annettu IS FALSE OR (sisempi_i.otsikko LIKE :teksti OR sisempi_i.paikankuvaus LIKE :teksti OR sisempi_i.lisatieto LIKE :teksti) OR
       -- HOX: Mikäli kannan ja käyttäjän välillä on eri enkoodaus, niin tämä ei toimi.
       -- Tarkista paikallisessa ympäristössä tarvittaessa SHOW CLIENT_ENCODING; ja SHOW SERVER_ENCODING;
       -- Jos ne eivät täsmää, aseta server encoding esim. näin: `update pg_database set encoding = pg_char_to_encoding('UTF8');`
       sisempi_i.selitteet::TEXT ILIKE REPLACE(TRANSLATE(:teksti, 'åäöÅÄÖ', 'aaoAAO'), ' ', '') OR
       (SELECT nimi from palautevayla_tarkenne WHERE ulkoinen_id = sisempi_i.tarkenne) LIKE :teksti OR
       (SELECT nimi from palautevayla_aihe WHERE ulkoinen_id = sisempi_i.aihe) LIKE :teksti) AND

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
       sisempi_i.ilmoittaja_matkapuhelin
           LIKE :ilmoittaja-puhelin) AND

       -- Rajaa aiheella ja tarkenteella
      (:aihe::INTEGER IS NULL OR
       sisempi_i.aihe = :aihe) AND
      (:tarkenne::INTEGER IS NULL OR
       sisempi_i.tarkenne = :tarkenne)
       ORDER BY
            CASE WHEN :lajittelu-suunta = 'nouseva' THEN sisempi_i."valitetty-urakkaan" END ASC,
            CASE WHEN :lajittelu-suunta = 'laskeva' THEN sisempi_i."valitetty-urakkaan" END DESC
       LIMIT :max-maara::INTEGER
       )
ORDER BY CASE WHEN :lajittelu-suunta = 'nouseva' THEN ulompi_i."valitetty-urakkaan" END ASC,
         CASE WHEN :lajittelu-suunta = 'laskeva' THEN ulompi_i."valitetty-urakkaan" END DESC,
         it.kuitattu DESC;

-- name: hae-ilmoitukset-raportille
SELECT
  i.urakka,
  i.ilmoitettu,
  i.valitetty,
  i."valitetty-urakkaan",
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
         (:urakkatyyppi_annettu IS FALSE OR
         CASE WHEN :urakkatyyppi = 'hoito' THEN -- huomioidaan myös teiden-hoito -urakkatyyppi
          u2.tyyppi IN  ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi)
          ELSE u2.tyyppi = :urakkatyyppi::urakkatyyppi END) AND
         (x.urakka IS NULL OR u2.urakkanro IS NOT NULL) AND -- Ei-testiurakka
         -- Tarkasta että ilmoituksen saapumisajankohta sopii hakuehtoihin
         ((:alku_annettu IS FALSE AND :loppu_annettu IS FALSE) OR
          (:loppu_annettu IS FALSE AND x."valitetty-urakkaan" >= :alku) OR
          (:alku_annettu IS FALSE AND x."valitetty-urakkaan" <= :loppu) OR
          (x."valitetty-urakkaan" BETWEEN :alku AND :loppu)))
ORDER by u.nimi;

-- name: hae-ilmoitukset-ilmoitusidlla
SELECT
  ilmoitusid,
  tunniste,
  tila,
  ilmoitettu,
  "valitetty-urakkaan",
  valitetty as "valitetty-harjaan",
  "vastaanotettu-alunperin" as "vastaanotettu-harjaan",
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
  kuvat,
  "emon-ilmoitusid",
  i.aihe AS aihe_id,
  pa.nimi AS aihe_nimi,
  i.tarkenne AS tarkenne_id,
  pt.nimi AS tarkenne_nimi,
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
FROM ilmoitus i
  LEFT JOIN palautevayla_aihe pa ON i.aihe = pa.ulkoinen_id
  LEFT JOIN palautevayla_tarkenne pt ON i.tarkenne = pt.ulkoinen_id
WHERE ilmoitusid IN (:ilmoitusidt);

-- name: hae-ilmoitus
SELECT
  i.id,
  i.urakka,
  u.nimi as urakkanimi,
  hy.id                                    AS hallintayksikko_id,
  hy.nimi                                  AS hallintayksikko_nimi,
  i.ilmoitusid,
  i.ilmoitettu,
  i.valitetty,
  i."valitetty-urakkaan",
  i.yhteydenottopyynto,
  i.otsikko,
  i.paikankuvaus,
  i.lisatieto,
  i.ilmoitustyyppi,
  i.selitteet,
  i.kuvat,
  i."emon-ilmoitusid",
  i.aihe,
  i.tarkenne,
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
  i.ilmoitettu,
  i.valitetty,
  i."valitetty-urakkaan",
  i.yhteydenottopyynto,
  i.otsikko,
  i.paikankuvaus,
  i.lisatieto,
  i.selitteet,
  i.aihe,
  i.tarkenne,

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
  ilmoitettu,
  valitetty as "valitetty-harjaan",
  "valitetty-urakkaan",
  "vastaanotettu-alunperin" as "vastaanotettu-harjaan",
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
  i.kuvat,
  i."emon-ilmoitusid",
  i.aihe AS aihe_id,
  i.tarkenne AS tarkenne_id,
  pa.nimi AS aihe_nimi,
  pt.nimi AS tarkenne_nimi,
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
FROM ilmoitus i
    LEFT JOIN palautevayla_aihe pa ON i.aihe = pa.ulkoinen_id
    LEFT JOIN palautevayla_tarkenne pt ON i.tarkenne = pt.ulkoinen_id
WHERE urakka = :urakka AND
      (i.muokattu > :aika OR i.luotu > :aika);

-- name: hae-ilmoitukset-ytunnuksella
WITH ilmoitus_urakat AS (SELECT u.id as id, u.urakkanro as urakkanro
                           FROM urakka u
                                JOIN organisaatio o ON o.id = u.urakoitsija AND o.ytunnus = :ytunnus
                                -- Haetaan vain käynnissäolevista urakoista. Urakat ovat vastuussa tieliikenneilmoituksista
                                -- 12 h urakan päättymisvuorokauden jälkeenkin.
                          WHERE (((u.loppupvm + interval '36 hour') >= NOW()) OR
                                (u.loppupvm IS NULL AND u.alkupvm <= NOW()))),
     loydetyt_ilmoitukset AS (SELECT distinct i.id as id, u.urakkanro
                              from ilmoitus_urakat u,
                                   ilmoitus i
                              WHERE i.urakka = u.id
                                and i."valitetty-urakkaan" between :alkuaika::TIMESTAMP AND :loppuaika::TIMESTAMP
                              union
                              SELECT distinct i.id as id, u.urakkanro
                              from ilmoitus_urakat u
                                       join ilmoitus i on i.urakka = u.id
                                       JOIN ilmoitustoimenpide it on it.ilmoitus = i.id AND
                                                                     it.kuitattu between :alkuaika::TIMESTAMP AND :loppuaika::TIMESTAMP)
SELECT
    i.ilmoitusid,
    i.tunniste,
    i.tila,
    i.ilmoitettu,
    i.valitetty as "valitetty-harjaan",
    i."valitetty-urakkaan",
    i."vastaanotettu-alunperin" as "vastaanotettu-harjaan",
    CASE
        WHEN (i."vastaanotettu-alunperin" = i.vastaanotettu) THEN NULL
        ELSE
            i.vastaanotettu
        END as "paivitetty-harjaan",
    li.urakkanro AS alueurakkanumero,
    i.ilmoitustyyppi,
    i.yhteydenottopyynto,
    i.paikankuvaus,
    i.lisatieto,
    i.otsikko,
    i.selitteet,
    i.kuvat,
    i."emon-ilmoitusid",
    i.aihe AS aihe_id,
    i.tarkenne AS tarkenne_id,
    pa.nimi AS aihe_nimi,
    pt.nimi AS tarkenne_nimi,
    i.sijainti,
    i.tr_numero as tienumero,
    i.ilmoittaja_etunimi,
    i.ilmoittaja_sukunimi,
    i.ilmoittaja_tyopuhelin,
    i.ilmoittaja_matkapuhelin,
    i.ilmoittaja_sahkoposti,
    i.lahettaja_etunimi,
    i.lahettaja_sukunimi,
    i.lahettaja_puhelinnumero,
    i.lahettaja_sahkoposti,
    i."aiheutti-toimenpiteita",
    json_agg(row_to_json(row(it.kuitattu, it.kuittaustyyppi, coalesce(it.vakiofraasi,''), coalesce(it.vapaateksti,''),
        it.kuittaaja_henkilo_etunimi,it.kuittaaja_henkilo_sukunimi, it.kuittaaja_organisaatio_nimi,
        coalesce(it.kuittaaja_organisaatio_ytunnus, ''), it.kanava))) AS kuittaukset
FROM loydetyt_ilmoitukset li
         JOIN ilmoitus i ON i.id = li.id
         LEFT JOIN ilmoitustoimenpide it on it.ilmoitus = li.id
         LEFT JOIN palautevayla_aihe pa on i.aihe = pa.ulkoinen_id
         LEFT JOIN palautevayla_tarkenne pt on i.tarkenne = pt.ulkoinen_id
     GROUP BY i.id, li.urakkanro, i."valitetty-urakkaan", pa.nimi, pt.nimi
ORDER BY i."valitetty-urakkaan" ASC
LIMIT 10000;

-- name: hae-id-ilmoitus-idlla
-- Hakee id:n ilmoitus-id:llä
SELECT id
FROM ilmoitus
WHERE ilmoitusid = :ilmoitusid;

-- name: hae-id-ja-urakka-ilmoitus-idlla
-- Hakee ilmoituksen id:n ja urakan ilmoitus-id:llä
SELECT id, urakka, "valitetty-urakkaan", valitetty
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
 "vastaanotettu-alunperin",
 "valitetty-urakkaan",
 aihe,
 tarkenne,
 kuvat,
 "emon-ilmoitusid",
 tila)
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
   :vastaanotettu-alunperin :: TIMESTAMPTZ,
   :valitetty-urakkaan :: TIMESTAMP,
   :aihe,
   :tarkenne,
   :kuvat :: TEXT [],
   :emon-ilmoitusid,
   'ei-valitetty'::ilmoituksen_tila);

-- name: paivita-ilmoitus!
-- Päivittää ilmoituksen
UPDATE ilmoitus
SET urakka               = :urakka,
    ilmoitusid           = :ilmoitusid,
    ilmoitettu           = :ilmoitettu,
    valitetty            = :valitetty,
    "valitetty-urakkaan" = :valitetty-urakkaan,
    vastaanotettu = :vastaanotettu,
    yhteydenottopyynto = :yhteydenottopyynto,
    otsikko = :otsikko,
    paikankuvaus = :paikankuvaus,
    lisatieto = :lisatieto,
    ilmoitustyyppi = :ilmoitustyyppi :: ILMOITUSTYYPPI,
    selitteet = :selitteet :: TEXT [],
    tunniste = :tunniste,
    muokattu = NOW(),
    viestiid = :viestiid,
    aihe = :aihe,
    tarkenne = :tarkenne,
    kuvat = :kuvat::TEXT[]
WHERE id = :id;

-- name: paivita-ilmoitus-valitetty!
-- Päivittää ilmoitukseen Harja id:n perusteella tiedon siitä, että ilmoitus on välitetty T-Loikiin
-- Asettaa ilmoituksen tilan "normaaliksi" eli 'kuittaamaton'. Jos ilmoitukseen on jo ehditty tehdä ilmoitus-
-- toimenpiteitä eli kuittauksia kuten 'aloitettu', tällöin ei muuteta tilaa enää takaisinpäin.
UPDATE ilmoitus
   SET tila = 'kuittaamaton'::ilmoituksen_tila
 WHERE id = :id AND tila = 'ei-valitetty';


-- name: paivita-ilmoituksen-urakka!
-- Päivittää ilmoitusid:n perusteella urakan. Käytetään, kun on lähetetty ilmoitus ensin väärälle urakalle
UPDATE ilmoitus
SET urakka = :urakkaid
WHERE ilmoitusid = :ilmoitusid;

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
SET tila = 'virhe', ed_lahetysvirhe = NOW(), virhe_lkm = virhe_lkm + 1
WHERE id = :id;

-- name: merkitse-ilmoitustoimenpidelle-lahetysvirhe-lahetysidlla!
UPDATE ilmoitustoimenpide
SET tila = 'virhe', ed_lahetysvirhe = NOW(), virhe_lkm = virhe_lkm + 1
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
                 (i.urakka IS NULL OR
                  (SELECT urakkanro FROM urakka WHERE id = i.urakka) IS NOT NULL) AND -- Ei-testiurakka
                 (:urakkatyyppi::urakkatyyppi IS NULL OR
                  CASE
                      WHEN (:urakkatyyppi = 'hoito' OR :urakkatyyppi = 'teiden-hoito') THEN
                                  (SELECT tyyppi FROM urakka WHERE id = i.urakka) IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi)
                      ELSE
                                  (SELECT tyyppi FROM urakka WHERE id = i.urakka) = :urakkatyyppi::urakkatyyppi
                      END
                     ) AND
                 (:hallintayksikko_id :: INTEGER IS NULL OR i.urakka IN (SELECT id
                                                                         FROM urakka
                                                                         WHERE hallintayksikko = :hallintayksikko_id)) AND
                 (:alkupvm :: DATE IS NULL OR i."valitetty-urakkaan" >= :alkupvm) AND
                 (:loppupvm :: DATE IS NULL OR i."valitetty-urakkaan" <= :loppupvm)
GROUP BY CUBE (apl.nimi, i.ilmoitustyyppi);

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
  (:urakkatyyppi::urakkatyyppi IS NULL OR
   CASE
       WHEN :urakkatyyppi = 'hoito' THEN
           u.tyyppi IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi)
       ELSE
           u.tyyppi = :urakkatyyppi::urakkatyyppi
       END)
  AND
  (:hallintayksikko_id :: INTEGER IS NULL OR urakka IN (SELECT id
                                                          FROM urakka
                                                          WHERE hallintayksikko = :hallintayksikko_id)) AND
  (:alkupvm :: DATE IS NULL OR "valitetty-urakkaan" >= :alkupvm) AND
  (:loppupvm :: DATE IS NULL OR "valitetty-urakkaan" <= :loppupvm);


-- name: hae-lahettamattomat-ilmoitustoimenpiteet
SELECT id
FROM ilmoitustoimenpide
WHERE
  (tila IS NULL OR tila = 'virhe') AND
  kuittaustyyppi != 'valitys' and
  (ed_lahetysvirhe IS NULL OR
      -- mitä useampi lähetysvirhe jo takana, sitä harvemmin uudelleen lähetys
      -- Jos edellisestä lähetysvirheestä on kulunut virheiden lukumäärä * 10min, niin lähetetään uudelleen
      -- esim jo 4 yritystä epäonnistunut, vaaditaan 40min viive. Näin vältetään T-Loikin pään tukahduttamista viesteihin ongelmatilanteessa
      -- max 10 uudelleenyritystä, jonka jälkeen luovutetaan ja tarvittaessa säädetään käsipelillä
      -- 1 virhe: 10min viive, 2 virhettä: 20min, 3 virhettä: 30min... 10 virhettä: 100min, 10+ virhettä: luovuta
   (((NOW() - ed_lahetysvirhe) > (virhe_lkm * interval '10 minutes')) AND virhe_lkm < 11));

-- name: hae-myohastyneet-ilmoitustoimenpiteet
SELECT count(*) AS maara,
       array_agg(id) idt,
       (now() - kuitattu > interval '10 minutes') AS "halytys-annettava",
       myohastymisvaroitus,
       array_agg(DISTINCT ilmoitusid::TEXT) AS korrelaatioidt
FROM ilmoitustoimenpide
WHERE tila = 'odottaa_vastausta'
  AND (kanava = 'harja' OR kanava='ulkoinen_jarjestelma')
  AND kuitattu < (now() - interval '1 minute')
  AND (myohastymisvaroitus != 'halytys' OR myohastymisvaroitus IS NULL)
  AND NOT (myohastymisvaroitus IS NOT NULL AND myohastymisvaroitus = 'varoitus' AND (now() - kuitattu < interval '10 minutes'))
GROUP BY (now() - kuitattu > interval '10 minutes'), myohastymisvaroitus;

--name: merkitse-ilmoitustoimenpide-varoitus-annetuksi!
UPDATE ilmoitustoimenpide SET myohastymisvaroitus = :varoitus::kuittausvaroitus
WHERE id IN (:idt);

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
SET "aiheutti-toimenpiteita" = :aiheutti-toimenpiteita,
    muokattu = current_timestamp
WHERE id = :id;

-- name: ilmoitus-loytyy-idlla
SELECT exists(SELECT FROM ilmoitus WHERE ilmoitusid = :ilmoitusid);

-- name: ilmoitus-on-lahetetty-urakalle
SELECT exists(SELECT FROM ilmoitus i WHERE i.ilmoitusid = :ilmoitusid AND i.urakka = :urakkaid);

-- name: ilmoituksen-alkuperainen-kesto
SELECT extract(EPOCH FROM (SELECT vastaanotettu - "vastaanotettu-alunperin"
                           FROM ilmoitus
                           WHERE id = :id)) as kesto;

-- name: tallenna-ilmoitusten-toimenpiteiden-aloitukset!
UPDATE ilmoitus
SET "toimenpiteet-aloitettu" = now(),
  "aiheutti-toimenpiteita"   = TRUE,
    muokattu = current_timestamp
WHERE id in (:idt);

-- name: peruuta-ilmoitusten-toimenpiteiden-aloitukset!
UPDATE ilmoitus
SET "toimenpiteet-aloitettu" = null,
  "aiheutti-toimenpiteita"   = false,
    muokattu = current_timestamp
WHERE id in (:idt);
