-- Näiden kyselyiden tulisi pääsääntöisesti palauttaa data samassa muodossa kuin asioiden
-- omissa näkymissä, jotta tiedot saadaan näytettyä oikein kartan infopaneelissa.

-- name: hae-ilmoitukset
SELECT
  i.id,
  i.urakka,
  i.ilmoitusid,
  i.tunniste,
  i.ilmoitettu,
  i.valitetty,
  i.yhteydenottopyynto,
  i.otsikko,
  i.paikankuvaus,
  i.lisatieto,
  i.tila,
  i.ilmoitustyyppi,
  i.selitteet,
  i.urakkatyyppi,

  ST_Simplify(i.sijainti, :toleranssi) AS sijainti,
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
WHERE
  ((:alku :: DATE IS NULL AND :loppu :: DATE IS NULL)
   OR i.ilmoitettu BETWEEN :alku AND :loppu) AND
  (i.urakka IS NULL OR i.urakka IN (:urakat)) AND
  i.ilmoitustyyppi :: TEXT IN (:tyypit);

-- name: hae-laatupoikkeamat
SELECT
  lp.id,
  lp.aika,
  lp.kohde,
  lp.tekija,
  lp.kuvaus,
  ST_Simplify(lp.sijainti, :toleranssi) AS sijainti,
  lp.tarkastuspiste,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  lp.kasittelyaika                    AS paatos_kasittelyaika,
  lp.paatos                           AS paatos_paatos,
  lp.kasittelytapa                    AS paatos_kasittelytapa,
  lp.perustelu                        AS paatos_perustelu,
  lp.muu_kasittelytapa                AS paatos_muukasittelytapa,
  lp.selvitys_pyydetty                AS selvityspyydetty,

  lp.tr_numero,
  lp.tr_alkuosa,
  lp.tr_alkuetaisyys,
  lp.tr_loppuosa,
  lp.tr_loppuetaisyys,
  ypk.nimi AS yllapitokohde_nimi,
  ypk.kohdenumero AS yllapitokohde_numero,
  ypk.tr_numero AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys AS yllapitokohde_tr_loppuetaisyys
FROM laatupoikkeama lp
  JOIN kayttaja k ON lp.luoja = k.id
  LEFT JOIN yllapitokohde ypk ON lp.yllapitokohde = ypk.id
  LEFT JOIN urakka u ON lp.urakka = u.id
WHERE ((lp.urakka IN (:urakat) AND u.urakkanro IS NOT NULL)
       OR lp.urakka IS NULL)
      AND (lp.aika BETWEEN :alku AND :loppu OR
           lp.kasittelyaika BETWEEN :alku AND :loppu) AND
      lp.tekija :: TEXT IN (:tekijat)
      AND lp.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (lp.yllapitokohde IS NULL
           OR
           lp.yllapitokohde IS NOT NULL AND
           (SELECT poistettu FROM yllapitokohde WHERE id = lp.yllapitokohde) IS NOT TRUE);

-- name: hae-tarkastukset
-- fetch-size: 64
-- row-fn: geo/muunna-reitti
SELECT
  ST_Simplify(t.sijainti, :toleranssi) AS reitti,
  t.tyyppi,
  t.laadunalitus,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END                                  AS tekija,
  -- Talvihoito- ja soratiemittauksesta riittää tieto, onko niitä tarkastuksella
  CASE WHEN
    thm.lumimaara IS NULL AND
    thm.tasaisuus IS NULL AND
    thm.kitka IS NULL AND
    thm.lampotila_ilma IS NULL AND
    thm.lampotila_tie IS NULL
    THEN NULL
  ELSE 'Talvihoitomittaus'
  END AS talvihoitomittaus,
  CASE WHEN
    stm.tasaisuus IS NULL AND
    stm.kiinteys IS NULL AND
    stm.polyavyys IS NULL AND
    stm.sivukaltevuus IS NULL
    THEN NULL
  ELSE 'Soratiemittaus'
  END AS soratiemittaus,
  -- Vakiohavainnot otetaan merkkijonona, koska tyyliin vaikuttaa tietyt avainsanat (esim. "Luminen")
  (SELECT array_agg(nimi)
   FROM tarkastus_vakiohavainto t_vh
     JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
   WHERE tarkastus = t.id)             AS vakiohavainnot,
  t.havainnot AS havainnot
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
  -- Talvi- ja soratiemittaukset
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
  LEFT JOIN urakka u ON t.urakka = u.id
WHERE sijainti IS NOT NULL AND
      ((t.urakka IN (:urakat) AND u.urakkanro IS NOT NULL) OR t.urakka IS NULL) AND
      (t.aika BETWEEN :alku AND :loppu) AND
      ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax)) AND
t.tyyppi :: TEXT IN (:tyypit) AND
(t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
-- Ei kuulu poistettuun ylläpitokohteeseen
AND (t.yllapitokohde IS NULL
OR
t.yllapitokohde IS NOT NULL AND
(SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE)
ORDER BY t.laadunalitus ASC;

-- name: hae-tarkastusten-asiat
-- Hakee tarkastusten asiat pisteessä
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
  yrita_tierekisteriosoite_pisteille2(
      alkupiste(t.sijainti), loppupiste(t.sijainti), 1)::TEXT AS tierekisteriosoite
FROM tarkastus t
  JOIN kayttaja k ON t.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
  LEFT JOIN yllapitokohde ypk ON t.yllapitokohde = ypk.id
  LEFT JOIN urakka u ON t.urakka = u.id
WHERE sijainti IS NOT NULL AND
      ((t.urakka IN (:urakat) AND u.urakkanro IS NOT NULL) OR t.urakka IS NULL) AND
      (t.aika BETWEEN :alku AND :loppu) AND
      ST_Distance(t.sijainti, ST_MakePoint(:x, :y)) < :toleranssi AND
t.tyyppi :: TEXT IN (:tyypit) AND
(t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
-- Ei kuulu poistettuun ylläpitokohteeseen
AND (t.yllapitokohde IS NULL
OR
t.yllapitokohde IS NOT NULL AND
(SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);


-- jarjestelma & tyokoneid perusteella uniikit tehtävät
-- name: hae-tyokoneiden-asiat
SELECT
  t.jarjestelma,
  t.tyokonetyyppi,
  t.urakkaid,
  t.tehtavat,
  o.nimi AS organisaationimi,
  u.nimi AS urakkanimi,
  MIN(t.lahetysaika) FILTER (WHERE t.lahetysaika BETWEEN :alku AND :loppu) AS "ensimmainen-havainto",
  MAX(t.lahetysaika) FILTER (WHERE t.lahetysaika BETWEEN :alku AND :loppu) AS "viimeisin-havainto"
FROM
  tyokonehavainto t
  LEFT JOIN organisaatio o ON t.organisaatio = o.id
  LEFT JOIN urakka u ON u.id = t.urakkaid
WHERE sijainti IS NOT NULL AND
      (t.urakkaid IN (:urakat) OR
       -- Jos urakkatietoa ei ole, näytetään vain oman organisaation (tai tilaajalle kaikki)
       (t.urakkaid IS NULL AND
        (:nayta-kaikki OR t.organisaatio = :organisaatio))) AND
      (t.lahetysaika BETWEEN :alku AND :loppu) AND
      ST_Distance(t.sijainti :: GEOMETRY, ST_MakePoint(:x, :y)::geometry) < :toleranssi
GROUP BY t.tyokoneid, t.jarjestelma, t.tehtavat, t.tyokonetyyppi, t.urakkaid, o.nimi, u.nimi;

-- name: hae-turvallisuuspoikkeamat
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.kasitelty,
  t.tyontekijanammatti,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.vakavuusaste,
  ST_Simplify(t.sijainti, :toleranssi) AS sijainti,
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
WHERE
  (t.urakka IS NULL OR t.urakka IN (:urakat)) AND
  (t.tapahtunut BETWEEN :alku AND :loppu OR
   t.kasitelty BETWEEN :alku AND :loppu);

-- name: hae-paallystysten-reitit
-- fetch-size: 64
-- Hakee päällystystöiden reitit karttapiirtoa varten
SELECT ypk.id,
       ypka.kohde_alku AS "kohde-alkupvm",
       ypka.paallystys_alku AS "paallystys-alkupvm",
       ypka.paallystys_loppu AS "paallystys-loppupvm",
       ypka.tiemerkinta_alku AS "tiemerkinta-alkupvm",
       ypka.tiemerkinta_loppu AS "tiemerkinta-loppupvm",
       ypka.kohde_valmis AS "kohde-valmispvm",
       ST_Simplify(ypko.sijainti, :toleranssi) AS sijainti
  FROM yllapitokohde ypk
       LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
       JOIN yllapitokohdeosa ypko ON (ypk.id = ypko.yllapitokohde AND ypko.poistettu IS NOT TRUE)
 WHERE ST_Intersects(ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax), ST_CollectionHomogenize(ypko.sijainti))
   AND ypk.poistettu IS NOT TRUE
   AND ypk.yllapitokohdetyotyyppi = 'paallystys'
   AND ypk.urakka IN (:urakat)
   AND ((:nykytilanne AND
        date_part('year', now())=ANY(ypk.vuodet) AND
        ((ypka.kohde_valmis IS NULL AND
          ypka.tiemerkinta_loppu IS NULL) OR
         (ypka.kohde_valmis IS NULL AND
          ypka.tiemerkinta_loppu IS NOT NULL AND
          (now() - ypka.tiemerkinta_loppu) < INTERVAL '7 days') OR
         (now() - ypka.kohde_valmis) < INTERVAL '7 days'))
        OR
        (:historiakuva AND (ypka.kohde_alku < :loppu
                            AND ((ypka.kohde_valmis IS NULL AND
                                  ypka.tiemerkinta_loppu IS NULL) OR
                                 (ypka.kohde_valmis > :alku) OR
                                 (ypka.tiemerkinta_loppu > :alku)))));

-- name: hae-paallystysten-viimeisin-muokkaus
-- single?: true
SELECT MAX(muokattu) FROM yllapitokohde WHERE yllapitokohdetyyppi='paallyste';

-- name: hae-paallystysten-tiedot
-- Hakee päällystysten tiedot klikkauspisteella
SELECT ypko.id,
       ypk.yllapitokohdetyotyyppi AS "yllapitokohde_yllapitokohdetyotyyppi",
       ypk.nimi as yllapitokohde_nimi,
       ypk.kohdenumero as yllapitokohde_kohdenumero,
       ypk.tr_numero as "yllapitokohde_tr-numero",
       ypk.tr_alkuosa as "yllapitokohde_tr-alkuosa",
       ypk.tr_alkuetaisyys as "yllapitokohde_tr-alkuetaisyys",
       ypk.tr_loppuosa as "yllapitokohde_tr-loppuosa",
       ypk.tr_loppuetaisyys as "yllapitokohde_tr-loppuetaisyys",
       ypko.nimi,
       ypko.tr_numero AS "tr-numero",
       ypko.tr_alkuosa AS "tr-alkuosa",
       ypko.tr_alkuetaisyys AS "tr-alkuetaisyys",
       ypko.tr_loppuosa AS "tr-loppuosa",
       ypko.tr_loppuetaisyys AS "tr-loppuetaisyys",
       ypk.nykyinen_paallyste AS "yllapitokohde_nykyinen-paallyste",
       ypk.keskimaarainen_vuorokausiliikenne AS "yllapitokohde_keskimaarainen-vuorokausiliikenne",
       ypko.toimenpide,
       ypka.kohde_alku AS "yllapitokohde_kohde-alkupvm",
       ypka.paallystys_alku AS "yllapitokohde_paallystys-alkupvm",
       ypka.paallystys_loppu AS "yllapitokohde_paallystys-loppupvm",
       ypka.tiemerkinta_alku AS "yllapitokohde_tiemerkinta-alkupvm",
       ypka.tiemerkinta_loppu AS "yllapitokohde_tiemerkinta-loppupvm",
       ypka.kohde_valmis AS "yllapitokohde_kohde-valmispvm",
       u.nimi AS yllapitokohde_urakka,
       o.nimi AS yllapitokohde_urakoitsija,
       ypk.id AS "yllapitokohde-id"
  FROM yllapitokohdeosa ypko
       JOIN yllapitokohde ypk ON ypk.id = ypko.yllapitokohde
       LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
       JOIN urakka u ON ypk.urakka = u.id
       JOIN organisaatio o ON u.urakoitsija = o.id
 WHERE ST_Distance(ypko.sijainti, ST_MakePoint(:x,:y)) < :toleranssi
   AND ypk.yllapitokohdetyotyyppi = 'paallystys'
   AND ypk.urakka IN (:urakat)
   AND ((:nykytilanne AND
         date_part('year', now())=ANY(ypk.vuodet) AND
         ((ypka.kohde_valmis IS NULL AND
           ypka.tiemerkinta_loppu IS NULL) OR
         (ypka.kohde_valmis IS NULL AND
          ypka.tiemerkinta_loppu IS NOT NULL AND
          (now() - ypka.tiemerkinta_loppu) < INTERVAL '7 days') OR
         (now() - ypka.kohde_valmis) < INTERVAL '7 days'))
        OR
        (:historiakuva AND (ypka.kohde_alku < :loppu
                            AND ((ypka.kohde_valmis IS NULL AND
                                  ypka.tiemerkinta_loppu IS NULL) OR
                                 (ypka.kohde_valmis > :alku) OR
                                 (ypka.tiemerkinta_loppu > :alku)))));

-- name: hae-paikkaukset-nykytilanteeseen
-- Hakee nykytilanteeseen kaikki paikkauskohteet, jotka eivät ole valmiita tai ovat
-- valmistuneet viikon sisällä.
SELECT
  ypk.id,
  pi.id                                 AS "paallystysilmoitus-id",
  pi.tila                               AS "paallystysilmoitus-tila",
  ypk.kohdenumero,
  ypk.nimi,
  ypk.nykyinen_paallyste                AS "nykyinen-paallyste",
  ypk.keskimaarainen_vuorokausiliikenne AS "keskimaarainen-vuorokausiliikenne",
  yllapitoluokka,
  ypk.tr_numero                         AS "tr-numero",
  ypk.tr_alkuosa                        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  ypk.tr_ajorata                        AS "tr-ajorata",
  ypk.tr_kaista                         AS "tr-kaista",
  ypk.yhaid,
  ypk.yllapitokohdetyyppi,
  ypk.yllapitokohdetyotyyppi,
  ypka.kohde_alku AS "kohde-alkupvm",
  ypka.paallystys_alku AS "paallystys-alkupvm",
  ypka.paallystys_loppu AS "paallystys-loppupvm",
  ypka.tiemerkinta_alku AS "tiemerkinta-alkupvm",
  ypka.tiemerkinta_loppu AS "tiemerkinta-loppupvm",
  ypka.kohde_valmis AS "kohde-valmispvm",
  o.nimi                                AS "urakoitsija",
  u.nimi AS "urakka"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN urakka u ON ypk.urakka = u.id
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN organisaatio o ON (SELECT urakoitsija FROM urakka WHERE id = ypk.urakka) = o.id
WHERE ypk.poistettu IS NOT TRUE
      AND ypk.yllapitokohdetyotyyppi = 'paikkaus'
      AND ypk.urakka IN (:urakat);

-- name: hae-paikkaukset-historiakuvaan
-- Hakee historiakuvaan kaikki paikkauskohteet, jotka ovat olleet aktiivisia
-- annetulla aikavälillä
SELECT
  ypk.id,
  pi.id                                 AS "paallystysilmoitus-id",
  pi.tila                               AS "paallystysilmoitus-tila",
  ypk.kohdenumero,
  ypk.nimi,
  ypk.nykyinen_paallyste                AS "nykyinen-paallyste",
  ypk.keskimaarainen_vuorokausiliikenne AS "keskimaarainen-vuorokausiliikenne",
  yllapitoluokka,
  ypk.tr_numero                         AS "tr-numero",
  ypk.tr_alkuosa                        AS "tr-alkuosa",
  ypk.tr_alkuetaisyys                   AS "tr-alkuetaisyys",
  ypk.tr_loppuosa                       AS "tr-loppuosa",
  ypk.tr_loppuetaisyys                  AS "tr-loppuetaisyys",
  ypk.tr_ajorata                        AS "tr-ajorata",
  ypk.tr_kaista                         AS "tr-kaista",
  ypk.yhaid,
  ypk.yllapitokohdetyyppi,
  ypk.yllapitokohdetyotyyppi,
  ypka.kohde_alku AS "kohde-alkupvm",
  ypka.paallystys_alku AS "paallystys-alkupvm",
  ypka.paallystys_loppu AS "paallystys-loppupvm",
  ypka.tiemerkinta_alku AS "tiemerkinta-alkupvm",
  ypka.tiemerkinta_loppu AS "tiemerkinta-loppupvm",
  ypka.kohde_valmis AS "kohde-valmispvm",
  o.nimi                                AS "urakoitsija",
  u.nimi AS "urakka"
FROM yllapitokohde ypk
  LEFT JOIN paallystysilmoitus pi ON pi.paallystyskohde = ypk.id
                                     AND pi.poistettu IS NOT TRUE
  LEFT JOIN urakka u ON ypk.urakka = u.id
  LEFT JOIN yllapitokohteen_aikataulu ypka ON ypka.yllapitokohde = ypk.id
  LEFT JOIN organisaatio o ON (SELECT urakoitsija FROM urakka WHERE id = ypk.urakka) = o.id
WHERE ypk.poistettu IS NOT TRUE
      AND ypk.yllapitokohdetyotyyppi = 'paikkaus'
      AND ypk.urakka IN (:urakat);

-- name: hae-toteumat
-- fetch-size: 64
-- row-fn: muunna-reitti
SELECT
  t.tyyppi,
  ST_Simplify(t.reitti, :toleranssi) as reitti,
  tt.toimenpidekoodi          AS tehtava_toimenpidekoodi,
  tpk.nimi                    AS tehtava_toimenpide
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
                    AND (t.alkanut BETWEEN :alku::DATE - interval '1 day' AND :loppu) -- nopeutus ks. selitys seur. SQL
                    AND (t.alkanut, t.paattynyt) OVERLAPS (:alku, :loppu)
                    AND tt.toimenpidekoodi IN (:toimenpidekoodit)
                    AND tt.poistettu IS NOT TRUE
                    AND t.poistettu IS NOT TRUE
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
WHERE (t.urakka IN (:urakat) OR t.urakka IS NULL) AND
      ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax));

-- name: hae-toteumien-selitteet
SELECT
  tt.toimenpidekoodi AS toimenpidekoodi,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS toimenpide
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
    -- Selitteiden haku oli tuskaisen hidas, ja tässä oli yksi nopeutuskeino.
    -- taulun alkanut-sarake on indeksoitu, joten tällä hakuehdolla saadaan nopeasti pudotettua
    -- pois rivit, jotka varmasti eivät osu :alku-:loppu välille.
    -- OVERLAPS ehto tarvitaan, jotta saadaan esim 0-2h hakuehdolla näkyviin toteumat, jotka ovat
    -- alkaneet 3h sitten. Tällaiset toteumat menevät myös BETWEEN vertailusta läpi kasvatetun intervallin
    -- ansiosta. Teoriassa motnta päivää kestävät toteumat voisivat tippua tästä "vahingossa", mutta oikeassa
    -- maailmassa sellaisia ei ole.
                    AND (t.alkanut BETWEEN :alku::DATE - interval '1 day' AND :loppu)
                    AND (t.alkanut, t.paattynyt) OVERLAPS (:alku, :loppu)
                    AND tt.toimenpidekoodi IN (:toimenpidekoodit)
                    AND tt.poistettu IS NOT TRUE
                    AND t.poistettu IS NOT TRUE
WHERE (t.urakka IN (:urakat) OR t.urakka IS NULL) AND
      ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax));

-- name: hae-toteumien-asiat
-- Hakee karttaa klikattaessa toteuma-ajat valituille tehtäville
SELECT
  t.id,
  t.alkanut AS alkanut,
  t.paattynyt AS paattynyt,
  t.suorittajan_nimi AS suorittaja_nimi,
  t.tyokonetyyppi,
  t.tyokoneen_lisatieto AS tyokonelisatieto,
  tpk.nimi           AS tehtava_toimenpide,
  tt.maara           AS tehtava_maara,
  tpk.yksikko        AS tehtava_yksikko,
  tt.toteuma         AS tehtava_id,
  tpk.nimi AS toimenpide,
  mk.nimi AS materiaalitoteuma_materiaali_nimi,
  mk.yksikko AS materiaalitoteuma_materiaali_yksikko,
  tm.maara AS materiaalitoteuma_maara,
  tm.id AS materiaalitoteuma_id,
  -- tarvitaanko viela kun interpolointi hakee myos?
  yrita_tierekisteriosoite_pisteille2(
      alkupiste(t.reitti), loppupiste(t.reitti), 1)::TEXT AS tierekisteriosoite
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
                    AND (t.alkanut BETWEEN :alku::DATE - interval '1 day' AND :loppu) -- nopeutus ks. selitys ed. SQL
                    AND (t.alkanut, t.paattynyt) OVERLAPS (:alku, :loppu)
                    AND tt.toimenpidekoodi IN (:toimenpidekoodit)
                    AND tt.poistettu IS NOT TRUE
                    AND t.poistettu IS NOT TRUE
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  LEFT JOIN toteuma_materiaali tm ON t.id = tm.toteuma AND tm.poistettu IS NOT TRUE
  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE (t.urakka IN (:urakat) OR t.urakka IS NULL)
                    AND (t.alkanut BETWEEN :alku::DATE - interval '1 day' AND :loppu) -- nopeutus ks. selitys ed. SQL
                    AND (t.alkanut, t.paattynyt) OVERLAPS (:alku, :loppu)
                    AND ST_Distance(t.reitti, ST_MakePoint(:x,:y)) < :toleranssi;

-- name: osoite-reittipisteille
-- Palauttaa tierekisteriosoitteen
SELECT yrita_tierekisteriosoite_pisteelle2(:piste ::geometry, :etaisyys ::integer) as tr_osoite;

-- name: reittipisteiden-sijainnit-toteuman-reitilla
SELECT ST_ClosestPoint(t.reitti, rp.sijainti ::geometry) AS sijainti,
       rp.ordinality AS reittipiste_id,
       rp.aika AS aika
  FROM toteuma t
       JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
       LEFT JOIN LATERAL unnest(tr.reittipisteet) WITH ORDINALITY rp ON TRUE
 WHERE t.id = :toteuma-id
   AND rp.ordinality IN (:reittipiste-idt);

-- name: suhteellinen-paikka-pisteiden-valissa
SELECT
  ST_LineLocatePoint(v.viiva ::geometry, ST_ClosestPoint (v.viiva ::geometry, :piste ::geometry) ::geometry) AS paikka
FROM
  (SELECT ST_MakeLine(:rp1 ::geometry, :rp2 ::geometry) AS viiva) v;

-- name: hae-tyokoneselitteet
-- Hakee työkoneiden selitteet
SELECT
  t.tehtavat,
  MAX(t.lahetysaika) AS viimeisin
FROM tyokonehavainto t
WHERE ST_distance(t.sijainti::GEOMETRY, st_makepoint(:keskipiste_x, :keskipiste_y)) < :sade AND
(t.urakkaid IN (:urakat) OR
-- Jos urakkatietoa ei ole, näytetään vain oman organisaation (tai tilaajalle kaikki)
(t.urakkaid IS NULL AND
(:nayta-kaikki OR t.organisaatio = :organisaatio))) AND
-- Rajaa toimenpiteellä
(t.tehtavat && :toimenpiteet :: suoritettavatehtava []) AND
-- Rajaa ajalla
(t.lahetysaika BETWEEN :alku AND :loppu)
GROUP BY t.tehtavat;

-- name: hae-tyokonereitit-kartalle
-- fetch-size: 64
-- hae myös suunta!
SELECT
  t.tyokoneid,
  t.jarjestelma,
  t.tehtavat,
  t.tyokonetyyppi,
  ST_MakeLine(array_agg(t.sijainti ORDER BY t.lahetysaika ASC)::GEOMETRY[]) AS reitti
FROM tyokonehavainto t
WHERE
  ST_distance(t.sijainti::GEOMETRY, st_makepoint(:keskipiste_x, :keskipiste_y)) < :sade AND
(t.urakkaid IN (:urakat) OR
-- Jos urakkatietoa ei ole, näytetään vain oman organisaation (tai tilaajalle kaikki)
(t.urakkaid IS NULL AND
(:nayta-kaikki OR t.organisaatio = :organisaatio))) AND
-- Rajaa toimenpiteellä
(t.tehtavat && :toimenpiteet :: suoritettavatehtava []) AND
-- Rajaa ajalla
(t.lahetysaika BETWEEN :alku AND :loppu)
GROUP BY t.tyokoneid, t.jarjestelma, t.tehtavat, t.tyokonetyyppi;


-- name: hae-toimenpidekoodit
SELECT
  id
FROM toimenpidekoodi
WHERE suoritettavatehtava :: TEXT IN (:toimenpiteet);

-- name: hae-tietyomaat
-- hakee liikenneohjausaidoilla suljettujen tieosuuksien geometriat
SELECT
  st.geometria      AS "geometria",
  ypk.nimi          AS "yllapitokohteen-nimi",
  ypk.kohdenumero   AS "yllapitokohteen-numero",
  st.kaistat        AS "kaistat",
  st.ajoradat       AS "ajoradat",
  st.asetettu       AS "aika",
  st.tr_tie         AS "tie",
  st.tr_aosa        AS "aosa",
  st.tr_aet         AS "aet",
  st.tr_losa        AS "losa",
  st.tr_let         AS "let",
  st.nopeusrajoitus AS "nopeusrajoitus"
FROM tietyomaa st
  LEFT JOIN yllapitokohde ypk ON ypk.id = st.yllapitokohde
WHERE st.poistettu IS NULL
      AND st.yllapitokohde IN (SELECT id FROM yllapitokohde WHERE urakka IN (:urakat)
                                                            OR suorittava_tiemerkintaurakka IN (:urakat))
      AND ST_Intersects(ST_MakeEnvelope(:x1, :y1, :x2, :y2), st.envelope);

-- name: hae-varustetoteumat
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
       vt.arvot,
       u.id AS "urakka-id"
  FROM varustetoteuma vt
       JOIN toteuma t ON vt.toteuma = t.id
       JOIN urakka u ON t.urakka = u.id
 WHERE t.urakka IN (:urakat)
   AND ((t.alkanut BETWEEN :alku AND :loppu) OR
        (t.paattynyt BETWEEN :alku AND :loppu));


-- name: urakoitsijan-urakat
-- TODO? erillisoikeudet urakoihin UNION:lla mukaan. Rooli-excelissäkin tähän liittyvä oikeus.
SELECT
  u.id, u.hallintayksikko
FROM urakka u
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE urk.id = :organisaatio;

-- name: hallintayksikoiden-urakat
SELECT
  u.id, u.hallintayksikko
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
WHERE hal.id IN (:hallintayksikot);

-- name: hae-valittujen-urakoiden-viimeisin-toteuma
-- single?: true
SELECT max(id)
  FROM toteuma
 WHERE urakka in (:urakat);
