-- name: hae-toteumat
-- fetch-size: 64
-- row-fn: muunna-toteuma
-- Hakee kaikki toteumat
SELECT t.id,
       t.tyyppi,
       t.reitti,
       t.alkanut, t.paattynyt,
       t.suorittajan_nimi AS suorittaja_nimi,
       (SELECT array_agg(row(tt.toimenpidekoodi, tt.maara, tpk.yksikko, tpk.nimi))
          FROM toteuma_tehtava tt
 	       JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
	 WHERE tt.toteuma = t.id) as tehtavat
  FROM toteuma t
       JOIN urakka u ON t.urakka=u.id
       JOIN kayttaja k ON t.luoja = k.id
 WHERE ST_Intersects(t.envelope, :sijainti)
   AND ST_Intersects(ST_CollectionHomogenize(t.reitti), :sijainti)
   AND ((t.alkanut BETWEEN :alku AND :loppu) OR
        (t.paattynyt BETWEEN :alku AND :loppu))

-- name: hae-tarkastukset
-- fetch-size: 64
SELECT t.id, t.aika, t.tyyppi, t.tarkastaja,
       t.havainnot, t.laadunalitus,
       t.sijainti,
       CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
       THEN 'urakoitsija' :: osapuoli
       ELSE 'tilaaja' :: osapuoli
       END AS tekija
FROM tarkastus t
     JOIN kayttaja k ON t.luoja = k.id
     JOIN organisaatio o ON o.id = k.organisaatio
WHERE sijainti IS NOT NULL AND
      ST_Intersects(t.sijainti, :sijainti) AND
      (t.aika BETWEEN :alku AND :loppu)

-- name: hae-ilmoitukset
SELECT i.id, i.urakka, i.ilmoitusid, i.ilmoitettu,
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
                AND kuittaustyyppi = 'vastaanotto'::kuittaustyyppi) as vastaanotettu,
       EXISTS(SELECT * FROM ilmoitustoimenpide WHERE ilmoitus = i.id
                 AND kuittaustyyppi = 'aloitus'::kuittaustyyppi) as aloitettu,
       EXISTS(SELECT * FROM ilmoitustoimenpide WHERE ilmoitus = i.id
                 AND kuittaustyyppi = 'lopetus'::kuittaustyyppi) as lopetettu
  FROM ilmoitus i
       LEFT JOIN ilmoitustoimenpide it ON it.ilmoitus = i.id
 WHERE (i.ilmoitettu BETWEEN :alku AND :loppu)
   AND ST_DWithin(i.sijainti, :sijainti, 25);


-- name: hae-turvallisuuspoikkeamat
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
       k.id              AS korjaavatoimenpide_id,
       k.kuvaus          AS korjaavatoimenpide_kuvaus,
       k.suoritettu      AS korjaavatoimenpide_suoritettu
  FROM turvallisuuspoikkeama t
       LEFT JOIN korjaavatoimenpide k ON t.id = k.turvallisuuspoikkeama
                                    AND k.poistettu IS NOT TRUE
 WHERE ST_DWithin(t.sijainti, :sijainti, 25) AND
       (t.tapahtunut :: DATE BETWEEN :alku AND :loppu OR
        t.kasitelty BETWEEN :alku AND :loppu);

-- name: hae-laatupoikkeamat
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
 WHERE -- FIXME: sijainti ei löydä
      (ST_Intersects(l.sijainti, :sijainti) OR l.tr_numero=20)
       AND (l.aika BETWEEN :alku AND :loppu OR
            l.kasittelyaika BETWEEN :alku AND :loppu)
       AND l.poistettu IS NOT TRUE

-- name: hae-reittipisteet
-- Hakee yhden annetun toteuman reittipisteet
SELECT aika,sijainti FROM reittipiste WHERE toteuma = :toteuma-id
