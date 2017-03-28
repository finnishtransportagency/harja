-- name: hae-tietyoilmoitukset
SELECT
  tti.id,
  tti.tloik_id,
  tti.paatietyoilmoitus,
  tti.tloik_paatietyoilmoitus_id,
  tti.luotu,
  tti.luoja,
  tti.muokattu,
  tti.muokkaaja,
  tti.poistettu,
  tti.poistaja,
  tti.ilmoittaja,
  tti.ilmoittaja_etunimi,
  tti.ilmoittaja_sukunimi,
  tti.ilmoittaja_matkapuhelin,
  tti.ilmoittaja_sahkoposti,
  tti.urakka,
  tti.urakka_nimi,
  tti.urakoitsija,
  tti.urakoitsijan_nimi,
  tti.urakoitsijayhteyshenkilo,
  tti.urakoitsijayhteyshenkilo_etunimi,
  tti.urakoitsijayhteyshenkilo_sukunimi,
  tti.urakoitsijayhteyshenkilo_matkapuhelin,
  tti.urakoitsijayhteyshenkilo_sahkoposti,
  tti.tilaaja,
  tti.tilaajan_nimi,
  tti.tilaajayhteyshenkilo,
  tti.tilaajayhteyshenkilo_etunimi,
  tti.tilaajayhteyshenkilo_sukunimi,
  tti.tilaajayhteyshenkilo_matkapuhelin,
  tti.tilaajayhteyshenkilo_sahkoposti,
  tti.luvan_diaarinumero,
  tti.tr_numero,
  tti.tr_alkuosa,
  tti.tr_alkuetaisyys,
  tti.tr_loppuosa,
  tti.tr_loppuetaisyys,
  tti.tien_nimi,
  tti.kunnat,
  tti.alkusijainnin_kuvaus,
  tti.loppusijainnin_kuvaus,
  tti.alku,
  tti.loppu,
  tti.viivastys_normaali_liikenteessa,
  tti.viivastys_ruuhka_aikana,
  tti.ajoneuvo_max_korkeus,
  tti.ajoneuvo_max_leveys,
  tti.ajoneuvo_max_pituus,
  tti.ajoneuvo_max_paino,
  tti.ajoittaiset_pysatykset,
  tti.ajoittain_suljettu_tie,
  tti.pysaytysten_alku,
  tti.pysaytysten_loppu,
  tti.lisatietoja,
  tti.urakkatyyppi,
  tti.tyotyypit,
  tti.sijainti,
  tti.tyoajat,
  tti.vaikutussuunta,
  tti.kaistajarjestelyt,
  tti.nopeusrajoitukset,
  tti.tienpinnat,
  tti.kiertotien_mutkaisuus,
  tti.kiertotienpinnat,
  tti.liikenteenohjaus,
  tti.liikenteenohjaaja,
  tti.huomautukset
FROM tietyoilmoitus tti
  LEFT JOIN
  kayttaja k ON k.id = tti.luoja
  LEFT JOIN
  organisaatio o ON o.id = k.organisaatio
WHERE (tti.luotu BETWEEN :alku AND :loppu) AND
      -- Joko urakan tai organisaation pitää olla sallittujen joukossa
      ((tti.urakka IS NULL OR tti.urakka IN (:urakat)) OR
       (o.id = :organisaatio)) AND
      (:sijainti :: GEOMETRY IS NULL OR st_intersects(st_buffer(:sijainti, 100), tti.sijainti)) AND
      (:luojaid :: INTEGER IS NULL OR tti.luoja = :luojaid) AND
      tti.paatietyoilmoitus IS NULL
ORDER BY tti.luotu DESC
LIMIT :maxmaara :: INTEGER;

-- name: hae-tietyoilmoitus
SELECT *
FROM tietyoilmoitus
WHERE id = :id

-- name: hae-tietyoilmoituksen-vaiheet
SELECT
  tti.id,
  tti.tloik_id,
  tti.paatietyoilmoitus,
  tti.tloik_paatietyoilmoitus_id,
  tti.luotu,
  tti.luoja,
  tti.muokattu,
  tti.muokkaaja,
  tti.poistettu,
  tti.poistaja,
  tti.ilmoittaja,
  tti.ilmoittaja_etunimi,
  tti.ilmoittaja_sukunimi,
  tti.ilmoittaja_matkapuhelin,
  tti.ilmoittaja_sahkoposti,
  tti.urakka,
  tti.urakka_nimi,
  tti.urakoitsija,
  tti.urakoitsijan_nimi,
  tti.urakoitsijayhteyshenkilo,
  tti.urakoitsijayhteyshenkilo_etunimi,
  tti.urakoitsijayhteyshenkilo_sukunimi,
  tti.urakoitsijayhteyshenkilo_matkapuhelin,
  tti.urakoitsijayhteyshenkilo_sahkoposti,
  tti.tilaaja,
  tti.tilaajan_nimi,
  tti.tilaajayhteyshenkilo,
  tti.tilaajayhteyshenkilo_etunimi,
  tti.tilaajayhteyshenkilo_sukunimi,
  tti.tilaajayhteyshenkilo_matkapuhelin,
  tti.tilaajayhteyshenkilo_sahkoposti,
  tti.luvan_diaarinumero,
  tti.tr_numero,
  tti.tr_alkuosa,
  tti.tr_alkuetaisyys,
  tti.tr_loppuosa,
  tti.tr_loppuetaisyys,
  tti.tien_nimi,
  tti.kunnat,
  tti.alkusijainnin_kuvaus,
  tti.loppusijainnin_kuvaus,
  tti.alku,
  tti.loppu,
  tti.viivastys_normaali_liikenteessa,
  tti.viivastys_ruuhka_aikana,
  tti.ajoneuvo_max_korkeus,
  tti.ajoneuvo_max_leveys,
  tti.ajoneuvo_max_pituus,
  tti.ajoneuvo_max_paino,
  tti.ajoittaiset_pysatykset,
  tti.ajoittain_suljettu_tie,
  tti.pysaytysten_alku,
  tti.pysaytysten_loppu,
  tti.lisatietoja,
  tti.urakkatyyppi,
  tti.tyotyypit,
  tti.sijainti,
  tti.tyoajat,
  tti.vaikutussuunta,
  tti.kaistajarjestelyt,
  tti.nopeusrajoitukset,
  tti.tienpinnat,
  tti.kiertotien_mutkaisuus,
  tti.kiertotienpinnat,
  tti.liikenteenohjaus,
  tti.liikenteenohjaaja,
  tti.huomautukset
FROM tietyoilmoitus tti
WHERE tti.paatietyoilmoitus = :paatietyoilmoitus;

-- name: hae-yllapitokohteen-tiedot-tietyoilmoitukselle
SELECT
  ypk.id,
  ypk.tr_numero        AS "tr-numero",
  ypk.tr_alkuosa       AS "tr-alkuosa",
  ypk.tr_alkuetaisyys  AS "tr-alkuetaisyys",
  ypk.tr_loppuosa      AS "tr-loppuosa",
  ypk.tr_loppuetaisyys AS "tr-loppuetaisyys",
  ypkat.kohde_alku     AS alku,
  ypkat.kohde_valmis   AS loppu,
  u.id                 AS "urakka-id",
  u.nimi               AS "urakka-nimi",
  u.sampoid            AS "urakka-sampo-id",
  urk.id               AS "urakoitsija-id",
  urk.nimi             AS "urakoitsija-nimi",
  ely.id               AS "tilaaja-id",
  ely.nimi             AS "tilaaja-nimi"
FROM yllapitokohde ypk
  JOIN yllapitokohteen_aikataulu ypkat ON ypk.id = ypkat.yllapitokohde
  JOIN urakka u ON ypk.urakka = u.id
  JOIN organisaatio urk ON u.urakoitsija = urk.id
  JOIN organisaatio ely ON u.hallintayksikko = ely.id
WHERE ypk.id = :kohdeid;

-- name: hae-urakan-tiedot-tietyoilmoitukselle
SELECT
  u.id      AS "urakka-id",
  u.nimi    AS "urakka-nimi",
  u.sampoid AS "urakka-sampo-id",
  urk.id    AS "urakoitsija-id",
  urk.nimi  AS "urakoitsija-nimi",
  ely.id    AS "tilaaja-id",
  ely.nimi  AS "tilaaja-nimi"
FROM urakka u
  JOIN organisaatio urk ON u.urakoitsija = urk.id
  JOIN organisaatio ely ON u.hallintayksikko = ely.id
WHERE u.id = :urakkaid;
