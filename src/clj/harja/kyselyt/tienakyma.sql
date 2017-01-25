-- name: hae-toteumat
-- Hakee kaikki toteumat
SELECT t.id,
       t.tyyppi,
       t.reitti,
       t.alkanut, t.paattynyt,
       t.suorittajan_nimi AS suorittaja_nimi,
       tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
       tt.maara AS tehtava_maara,
       tpk.yksikko AS tehtava_yksikko,
       tpk.nimi AS tehtava_toimenpide,
       rp.sijainti AS reittipiste_sijainti,
       rp.aika AS reittipiste_aika
  FROM toteuma t
       JOIN urakka u ON t.urakka=u.id
       JOIN toteuma_tehtava tt ON tt.toteuma = t.id
       JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
       JOIN toimenpidekoodi tpk3 ON tpk.emo = tpk3.id
       JOIN kayttaja k ON t.luoja = k.id
       LEFT JOIN reittipiste rp ON (t.id = rp.toteuma AND
                                    rp.aika BETWEEN :alku AND :loppu)

 WHERE ST_Intersects(t.envelope, :sijainti)
   AND ST_Intersects(ST_CollectionHomogenize(t.reitti), :sijainti)
   AND ((t.alkanut BETWEEN :alku AND :loppu) OR
        (t.paattynyt BETWEEN :alku AND :loppu))

-- name: hae-tarkastukset
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
