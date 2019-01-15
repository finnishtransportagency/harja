-- name: hae-toteumat
-- fetch-size: 64
-- row-fn: muunna-toteuma
-- Hakee kaikki toteumat
WITH haetut_toteumat AS (
  --Käytetään tämmöstä WITH hommaa, jotta tämä kysely käyttäisi molempia indeksejä (alku ja envelope)
    SELECT *
    FROM toteuma
    WHERE alkanut >= :alku AND ST_Intersects(envelope, :sijainti)
)
SELECT t.id,
  t.tyyppi,
  t.reitti,
  t.alkanut, t.paattynyt,
  t.suorittajan_nimi AS suorittaja_nimi,
  (SELECT array_agg(row(tt.toimenpidekoodi, tt.maara, tpk.yksikko, tpk.nimi))
   FROM toteuma_tehtava tt
     JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
   WHERE tt.toteuma = t.id) as tehtavat,
  t.tr_numero AS tierekisteriosoite_numero,
  t.tr_alkuosa AS tierekisteriosoite_alkuosa,
  t.tr_alkuetaisyys AS tierekisteriosoite_alkuetaisyys,
  t.tr_loppuosa AS tierekisteriosoite_loppuosa,
  t.tr_loppuetaisyys AS tierekisteriosoite_loppuetaisyys
FROM haetut_toteumat t
WHERE ST_Intersects(ST_CollectionHomogenize(t.reitti), :sijainti)
      AND t.paattynyt <= :loppu

-- name: hae-varustetoteumat
-- fetch-size: 64
-- row-fn: muunna-toteuma
-- Hakee kaikki toteumat
SELECT t.id,
       t.tyyppi as toteumatyyppi,
       t.reitti as sijainti,
       t.alkanut, t.paattynyt,
       t.suorittajan_nimi AS suorittaja_nimi,
       vt.tr_numero AS tierekisteriosoite_numero,
       vt.tr_alkuosa AS tierekisteriosoite_alkuosa,
       vt.tr_alkuetaisyys AS tierekisteriosoite_alkuetaisyys,
       vt.tr_loppuosa AS tierekisteriosoite_loppuosa,
       vt.tr_loppuetaisyys AS tierekisteriosoite_loppuetaisyys,
       vt.tunniste,
       vt.kuntoluokka,
       vt.tietolaji,
       vt.alkupvm, vt.loppupvm, vt.toimenpide,
       vt.arvot
  FROM varustetoteuma vt
       JOIN toteuma t ON vt.toteuma = t.id
 WHERE ST_Intersects(t.envelope, :sijainti)
   AND ST_Intersects(ST_CollectionHomogenize(t.reitti), :sijainti)
   AND t.alkanut >= :alku
   AND t.paattynyt <= :loppu

-- name: hae-tarkastukset
-- fetch-size: 64
SELECT
  t.id,
  t.tyyppi,
  t.laadunalitus,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END                                                        AS tekija,
  t.aika,
  t.tarkastaja,
  t.havainnot,
  t.sijainti,
  (SELECT array_agg(nimi)
   FROM tarkastus_vakiohavainto t_vh
     JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
   WHERE tarkastus = t.id)                                   AS vakiohavainnot,
  thm.talvihoitoluokka     AS talvihoitomittaus_hoitoluokka,
  thm.lumimaara            AS talvihoitomittaus_lumimaara,
  thm.tasaisuus            AS talvihoitomittaus_tasaisuus,
  thm.kitka                AS talvihoitomittaus_kitka,
  thm.lampotila_tie        AS talvihoitomittaus_lampotila_tie,
  thm.lampotila_ilma       AS talvihoitomittaus_lampotila_ilma,
  stm.hoitoluokka          AS soratiemittaus_hoitoluokka,
  stm.tasaisuus            AS soratiemittaus_tasaisuus,
  stm.kiinteys             AS soratiemittaus_kiinteys,
  stm.polyavyys            AS soratiemittaus_polyavyys,
  stm.sivukaltevuus        AS soratiemittaus_sivukaltevuus,
  t.tr_numero AS tierekisteriosoite_numero,
  t.tr_alkuosa AS tierekisteriosoite_alkuosa,
  t.tr_alkuetaisyys AS tierekisteriosoite_alkuetaisyys,
  t.tr_loppuosa AS tierekisteriosoite_loppuosa,
  t.tr_loppuetaisyys AS tierekisteriosoite_loppuetaisyys
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
WHERE sijainti IS NOT NULL AND
      ST_Intersects(t.sijainti, :sijainti) AND
      (t.aika BETWEEN :alku AND :loppu);

-- name: hae-ilmoitukset
SELECT i.id, i.urakka, i.ilmoitusid, i.tunniste, i.ilmoitettu,
       i.valitetty, i.yhteydenottopyynto, i.otsikko,
       i.paikankuvaus, i.lisatieto, i.tila, i.ilmoitustyyppi,
       i.selitteet, i.urakkatyyppi,
       i.sijainti,
       i.tr_numero, i.tr_alkuosa, i.tr_alkuetaisyys,
       i.tr_loppuosa, i.tr_loppuetaisyys,
       i.ilmoittaja_etunimi, i.ilmoittaja_sukunimi,

       it.id                               AS kuittaus_id,
       it.kuitattu                         AS kuittaus_kuitattu,
       it.vapaateksti                      AS kuittaus_vapaateksti,
       it.kuittaustyyppi                   AS kuittaus_kuittaustyyppi,
       it.kanava                           AS kuittaus_kanava,
       it.vakiofraasi                      AS kuittaus_vakiofraasi,

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
       it.kasittelija_organisaatio_ytunnus AS kuittaus_kasittelija_ytunnus,

       EXISTS(SELECT * FROM ilmoitustoimenpide WHERE ilmoitus = i.id
                AND kuittaustyyppi = 'vastaanotto') as vastaanotettu,
       EXISTS(SELECT * FROM ilmoitustoimenpide WHERE ilmoitus = i.id
                 AND kuittaustyyppi = 'aloitus') as aloitettu,
       EXISTS(SELECT * FROM ilmoitustoimenpide WHERE ilmoitus = i.id
                 AND kuittaustyyppi = 'lopetus') as lopetettu
  FROM ilmoitus i
       LEFT JOIN ilmoitustoimenpide it ON it.ilmoitus = i.id
 WHERE (i.ilmoitettu BETWEEN :alku AND :loppu)
   AND ST_DWithin(i.sijainti, :sijainti, 25);


-- name: hae-turvallisuuspoikkeamat
-- row-fn: muunna-turvallisuuspoikkeama
-- fetch-size: 64
SELECT t.id,
       t.urakka,
       t.tapahtunut,
       t.kasitelty,
       t.tyontekijanammatti,
       t.kuvaus,
       t.vammat,
       t.sairauspoissaolopaivat,
       t.sairaalavuorokaudet,
       t.vakavuusaste,
       t.sijainti,
       t.tr_numero,
       t.tr_alkuetaisyys,
       t.tr_loppuetaisyys,
       t.tr_alkuosa,
       t.tr_loppuosa,
       t.tyyppi,
       (SELECT array_agg(row(k.id, k.kuvaus, k.suoritettu))
          FROM korjaavatoimenpide k
	 WHERE k.turvallisuuspoikkeama = t.id
	       AND k.poistettu IS NOT TRUE) AS korjaavattoimenpiteet
  FROM turvallisuuspoikkeama t
 WHERE ST_DWithin(t.sijainti, :sijainti, 25) AND
       (t.tapahtunut :: DATE BETWEEN :alku AND :loppu OR
        t.kasitelty BETWEEN :alku AND :loppu);

-- name: hae-laatupoikkeamat
-- fetch-size: 64
SELECT l.id, l.aika, l.kohde, l.tekija, l.kuvaus, l.sijainti, l.tarkastuspiste,
       CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
       l.kasittelyaika                    AS paatos_kasittelyaika,
       l.paatos                           AS paatos_paatos,
       l.kasittelytapa                    AS paatos_kasittelytapa,
       l.perustelu                        AS paatos_perustelu,
       l.muu_kasittelytapa                AS paatos_muukasittelytapa,
       l.selvitys_pyydetty                AS selvityspyydetty,
       l.tr_numero, l.tr_alkuosa, l.tr_alkuetaisyys, l.tr_loppuosa, l.tr_loppuetaisyys,
       ypk.nimi AS yllapitokohde_nimi,
       ypk.kohdenumero AS yllapitokohde_numero,
       ypk.tr_numero AS yllapitokohde_tr_numero,
       ypk.tr_alkuosa AS yllapitokohde_tr_alkuosa,
       ypk.tr_alkuetaisyys AS yllapitokohde_tr_alkuetaisyys,
       ypk.tr_loppuosa AS yllapitokohde_tr_loppuosa,
       ypk.tr_loppuetaisyys AS yllapitokohde_tr_loppuetaisyys
  FROM laatupoikkeama l
       JOIN kayttaja k ON l.luoja = k.id
       LEFT JOIN yllapitokohde ypk ON l.yllapitokohde = ypk.id
 WHERE ST_Intersects(ST_CollectionHomogenize(l.sijainti), :sijainti)
       AND (l.aika BETWEEN :alku AND :loppu OR
            l.kasittelyaika BETWEEN :alku AND :loppu)
       AND l.poistettu IS NOT TRUE

-- name: hae-reittipisteet
-- Hakee yhden annetun toteuman reittipisteet
SELECT (x.rp).aika, (x.rp).sijainti
  FROM (SELECT unnest(reittipisteet) AS rp
          FROM toteuman_reittipisteet
	 WHERE toteuma = :toteuma-id) x
