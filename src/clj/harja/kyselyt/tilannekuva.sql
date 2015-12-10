-- name: hae-ilmoitukset
SELECT
  i.id,
  i.urakka,
  i.ilmoitusid,
  i.ilmoitettu,
  i.valitetty,
  i.yhteydenottopyynto,
  i.otsikko,
  i.lyhytselite,
  i.pitkaselite,
  -- selitteet
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
  (i.urakka IS NULL OR i.urakka IN (:urakat)) AND
  (:avoimet IS TRUE AND i.suljettu IS NOT TRUE OR
   :suljetut IS TRUE AND i.suljettu IS TRUE) AND
  i.ilmoitustyyppi :: TEXT IN (:tyypit);


-- name: hae-laatupoikkeamat
SELECT
  l.id,
  l.aika,
  l.kohde,
  l.tekija,
  l.kuvaus,
  l.sijainti,
  l.tarkastuspiste,
  CONCAT(k.etunimi, ' ', k.sukunimi) AS tekijanimi,
  l.kasittelyaika                    AS paatos_kasittelyaika,
  l.paatos                           AS paatos_paatos,
  l.kasittelytapa                    AS paatos_kasittelytapa,
  l.perustelu                        AS paatos_perustelu,
  l.muu_kasittelytapa                AS paatos_muukasittelytapa,
  l.selvitys_pyydetty                AS selvityspyydetty,

  l.tr_numero,
  l.tr_alkuosa,
  l.tr_alkuetaisyys,
  l.tr_loppuosa,
  l.tr_loppuetaisyys
FROM laatupoikkeama l
  JOIN kayttaja k ON l.luoja = k.id
WHERE (l.urakka IN (:urakat) OR l.urakka IS NULL)
      AND (l.luotu BETWEEN :alku AND :loppu OR
           l.muokattu BETWEEN :alku AND :loppu OR
           l.aika BETWEEN :alku AND :loppu OR
           l.kasittelyaika BETWEEN :alku AND :loppu)
      AND l.poistettu IS NOT TRUE;

-- name: hae-tarkastukset
SELECT
  t.id,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.sijainti,
  t.tarkastaja,
  t.mittaaja,
  t.havainnot,
  t.tyyppi
FROM tarkastus t
WHERE (t.urakka IN (:urakat) OR t.urakka IS NULL)
      AND (t.luotu BETWEEN :alku AND :loppu OR
           t.muokattu BETWEEN :alku AND :loppu OR
           t.aika BETWEEN :alku AND :loppu);

-- name: hae-turvallisuuspoikkeamat
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.paattynyt,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyotehtava,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.sijainti,
  t.tr_numero,
  t.tr_alkuetaisyys,
  t.tr_loppuetaisyys,
  t.tr_alkuosa,
  t.tr_loppuosa,
  t.tyyppi,

  k.id              AS korjaavatoimenpide_id,
  k.kuvaus          AS korjaavatoimenpide_kuvaus,
  k.suoritettu      AS korjaavatoimenpide_suoritettu,
  k.vastaavahenkilo AS korjaavatoimenpide_vastaavahenkilo
FROM turvallisuuspoikkeama t
  LEFT JOIN korjaavatoimenpide k ON t.id = k.turvallisuuspoikkeama
                                    AND k.poistettu IS NOT TRUE
WHERE
  (t.urakka IS NULL OR t.urakka IN (:urakat)) AND
  (t.tapahtunut :: DATE BETWEEN :alku AND :loppu OR
   t.paattynyt BETWEEN :alku AND :loppu OR
   t.kasitelty BETWEEN :alku AND :loppu OR
   t.luotu BETWEEN :alku AND :loppu OR
   t.muokattu BETWEEN :alku AND :loppu);

-- name: hae-paallystykset
SELECT
  pk.id,
  pi.id   AS paallystysilmoitus_id,
  pi.tila AS paallystysilmoitus_tila,
  pi.aloituspvm,
  pi.valmispvm_paallystys,
  pi.valmispvm_kohde,
  kohdenumero,
  pk.nimi,
  sopimuksen_mukaiset_tyot,
  muu_tyo,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
  muutoshinta,
  pko.sijainti,
  pi.tila
FROM paallystysilmoitus pi
  LEFT JOIN paallystyskohde pk ON pi.paallystyskohde = pk.id
  LEFT JOIN paallystyskohdeosa pko ON pko.paallystyskohde = pk.id
WHERE pk.poistettu IS NOT TRUE AND
      (pi.tila :: TEXT != 'valmis' OR
       (now() - pi.valmispvm_kohde) < INTERVAL '7 days');

-- name: hae-paikkaukset
SELECT
  pk.id,
  pi.id   AS paallystysilmoitus_id,
  pi.tila AS paallystysilmoitus_tila,
  pi.aloituspvm,
  pi.valmispvm_paikkaus,
  pi.valmispvm_kohde,
  kohdenumero,
  pk.nimi,
  sopimuksen_mukaiset_tyot,
  muu_tyo,
  arvonvahennykset,
  bitumi_indeksi,
  kaasuindeksi,
  pko.sijainti,
  pi.tila
FROM paikkausilmoitus pi
  LEFT JOIN paallystyskohde pk ON pi.paikkauskohde = pk.id
  LEFT JOIN paallystyskohdeosa pko ON pko.paallystyskohde = pk.id
WHERE pk.poistettu IS NOT TRUE AND
      (pi.tila :: TEXT != 'valmis' OR
       (now() - pi.valmispvm_kohde) < INTERVAL '7 days');

-- name: hae-toteumat
SELECT
  t.id,
  t.urakka,
  t.sopimus,
  t.alkanut,
  t.paattynyt,
  t.tyyppi,
  t.lisatieto,
  t.suorittajan_ytunnus           AS suorittaja_ytunnus,
  t.suorittajan_nimi              AS suorittaja_nimi,
  t.ulkoinen_id                   AS ulkoinenid,

  tt.id                           AS tehtava_id,
  tt.toimenpidekoodi              AS tehtava_toimenpidekoodi,
  tt.maara                        AS tehtava_maara,
  tt.paivan_hinta                 AS tehtava_paivanhinta,
  tt.lisatieto                    AS tehtava_lisatieto,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS tehtava_toimenpide,

  tm.id                           AS materiaali_id,
  tm.maara                        AS materiaali_maara,

  mk.id                           AS materiaali_materiaali_id,
  mk.nimi                         AS materiaali_materiaali_nimi,
  mk.kohdistettava                AS materiaali_materiaali_kohdistettava,

  rp.id                           AS reittipiste_id,
  rp.aika                         AS reittipiste_aika,
  rp.sijainti                     AS reittipiste_sijainti,

  rt.id                           AS reittipiste_tehtava_id,
  rt.toimenpidekoodi              AS reittipiste_tehtava_toimenpidekoodi,
  rt.maara                        AS reittipiste_tehtava_maara,
  (SELECT nimi
   FROM toimenpidekoodi tpk
   WHERE id = tt.toimenpidekoodi) AS reittipiste_tehtava_toimenpide,


  rm.id                           AS reittipiste_materiaali_id,
  rm.materiaalikoodi              AS reittipiste_materiaali_materiaalikoodi,
  rm.maara                        AS reittipiste_materiaali_maara,
  mk.nimi                         AS reittipiste_materiaali_nimi
FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND t.alkanut >= :alkupvm
                          AND t.paattynyt <= :loppupvm
                          AND tt.toimenpidekoodi IN (:toimenpidekoodit)
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE
  INNER JOIN reittipiste rp ON rp.toteuma = t.id
                               -- Haettavan reittipisteen pitää ensinnäkin mahtua kartalla näkyvälle alueelle
                               AND (st_contains((ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax)), rp.sijainti :: GEOMETRY))
  LEFT JOIN reitti_materiaali rm ON rm.reittipiste = rp.id
  LEFT JOIN reitti_tehtava rt ON rt.reittipiste = rp.id
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
                                     AND tm.poistettu IS NOT TRUE
  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE t.urakka = :urakka OR :rajaa_urakalla IS FALSE
                            -- Sen jälkeen tarkastetaan hallintayksiköllä/urakalla suodattaminen
                            AND (
                              -- Joko ei suodateta HY:llä/urakalla
                              (:hallintayksikko_annettu IS FALSE AND :rajaa_urakalla IS FALSE) OR
                              -- tai suodatetaan vain HY:llä..
                              (:rajaa_urakalla IS FALSE AND
                               st_contains((SELECT alue
                                            FROM organisaatio
                                            WHERE id = :hallintayksikko),
                                           rp.sijainti :: GEOMETRY)) OR
                              -- Tai suodatetaan urakalla
                              (st_contains((SELECT alue
                                            FROM urakoiden_alueet
                                            WHERE id = :urakka),
                                           rp.sijainti :: GEOMETRY)))
ORDER BY rp.aika ASC;