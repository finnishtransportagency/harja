-- name: hae-urakan-toteumat
-- Listaa kaikki urakan toteumat
SELECT
  t.id,
  t.alkanut,
  t.paattynyt,
  t.tyyppi,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  t.luoja       AS luoja_id,
  o.nimi        AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma AS jarjestelman_lisaama,
  (SELECT array_agg(concat(tt.id, '^', tpk.id, '^', tpk.nimi, '^', tt.maara))
   FROM toteuma_tehtava tt
     LEFT JOIN tehtava tpk ON tt.toimenpidekoodi = tpk.id
   WHERE tt.toteuma = t.id
         AND tt.poistettu IS NOT TRUE)
                AS tehtavat
FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
WHERE
  t.urakka = :urakka
  AND t.sopimus = :sopimus
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND t.tyyppi = :tyyppi :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
GROUP BY t.id, t.alkanut, t.paattynyt, t.tyyppi, o.nimi, k.kayttajanimi, k.jarjestelma;

-- name: hae-urakan-toteuma
-- Listaa urakan toteuman id:llä
SELECT
  t.id,
  t.alkanut,
  t.paattynyt,
  t.tyyppi,
  t.suorittajan_nimi    AS suorittaja_nimi,
  t.suorittajan_ytunnus AS suorittaja_ytunnus,
  t.lisatieto,
  t.luoja               AS luojaid,
  ST_Length(t.reitti)   AS pituus,

  o.nimi                AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma         AS jarjestelmanlisaama,
  t.reitti,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,

  tt.id                 AS "tehtava_tehtava-id",
  tpk.id                AS "tehtava_tpk-id",
  tpk.nimi              AS tehtava_nimi,
  tpk.yksikko           AS tehtava_yksikko,
  tt.maara              AS tehtava_maara,
  tpi.id                AS tehtava_toimenpideinstanssi_id,
  tpi.nimi              AS tehtava_toimenpideinstanssi_nimi

FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
  JOIN toteuma_tehtava tt ON (tt.toteuma = t.id AND tt.poistettu IS NOT TRUE)
  JOIN tehtava tpk ON tt.toimenpidekoodi = tpk.id
  LEFT JOIN toimenpide emo ON tpk.emo = emo.id
  LEFT JOIN toimenpideinstanssi tpi ON emo.id = tpi.toimenpide
                                       AND tpi.urakka = t.urakka
WHERE
  t.urakka = :urakka
  AND t.id = :toteuma
  AND t.poistettu IS NOT TRUE;

-- name: toteuma-jarjestelman-lisaama
SELECT k.jarjestelma AS jarjestelmanlisaama
FROM toteuma t
  LEFT JOIN kayttaja k ON k.id = t.luoja
WHERE t.id = :toteuma;

-- name: toteuman-urakka
SELECT t.urakka
FROM toteuma t
WHERE t.id = :toteuma;

-- name: erilliskustannuksen-urakka
SELECT t.urakka
FROM erilliskustannus t
WHERE t.id = :id;

-- name: toteuman-tyyppi
SELECT t.tyyppi
FROM toteuma t
WHERE t.id = :toteuma;

-- name: tehtavan-toteuma
SELECT tt.toteuma
FROM toteuma_tehtava tt
WHERE tt.id = :tehtava;

-- name: hae-toteumien-tehtavien-summat
-- Listaa urakan toteumien tehtävien määrien summat toimenpidekoodilla ryhmiteltynä.
SELECT
  x.tpk_id,
  x.maara,
  tk.nimi
FROM (SELECT
        toimenpidekoodi AS tpk_id,
        SUM(tt.maara)   AS maara
      FROM toteuma_tehtava tt
      WHERE tt.toteuma IN (SELECT id
                           FROM toteuma t
                           WHERE t.urakka = :urakka
                                 AND t.sopimus = :sopimus
                                 AND t.alkanut >= :alkanut
                                 AND t.alkanut <= :paattynyt
                                 AND t.tyyppi = :tyyppi :: toteumatyyppi
                                 AND t.poistettu IS NOT TRUE)
            AND tt.toimenpidekoodi IN (SELECT id
                                       FROM tehtava tk
                                       WHERE (:toimenpide :: INTEGER IS NULL
                                              OR tk.emo = (SELECT toimenpide
                                                           FROM toimenpideinstanssi
                                                           WHERE id = :toimenpide))
                                             AND (:tehtava :: INTEGER IS NULL OR tk.id = :tehtava))
            AND tt.poistettu IS NOT TRUE
      GROUP BY toimenpidekoodi) x
  JOIN tehtava tk ON x.tpk_id = tk.id
ORDER BY nimi;

-- name: hae-toteuman-toteuma-materiaalit-ja-tehtavat
-- Hakee toteuma_materiaalien ja tehtävien id:t. Hyödyllinen kun poistetaan toteuma.
SELECT
  tt.id AS tehtava_id,
  tm.id AS materiaali_id
FROM toteuma t
  LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
WHERE t.id IN (:id);

-- name: hae-urakan-toteuma-paivat
-- Hakee päivät tietyllä aikavälillä, jolle urakalla on toteumia.
SELECT DISTINCT date_trunc('day', alkanut) AS paiva
FROM toteuma
WHERE urakka = :urakka
      AND sopimus = :sopimus
      AND alkanut >= :alkupvm
      AND paattynyt <= :loppupvm
      AND poistettu IS NOT TRUE;

-- name: hae-urakan-tehtavat
-- Hakee tehtävät, joita annetulle urakalle voi kirjata.
SELECT t4.id,
       t4.nimi,
       t4.yksikko
FROM tehtava t4
         LEFT JOIN toimenpideinstanssi tpi on t4.emo = tpi.toimenpide
         LEFT JOIN urakka u on u.id = tpi.urakka
WHERE t4.poistettu IS NOT TRUE
  AND t4.emo = tpi.toimenpide AND u.id = :urakka
  AND (t4.voimassaolo_alkuvuosi IS NULL OR t4.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (t4.voimassaolo_loppuvuosi IS NULL OR t4.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER);

-- name: hae-urakan-ja-sopimuksen-toteutuneet-tehtavat
-- Hakee urakan tietyntyyppiset toteutuneet tehtävät
SELECT
  tt.id                           AS tehtava_id,
  tt.toteuma                      AS toteuma_id,
  tt.toimenpidekoodi,
  tt.maara,
  t.tyyppi,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  (SELECT nimi
   FROM tehtava tpk
   WHERE id = tt.toimenpidekoodi) AS toimenpide
FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND alkanut >= :alkupvm
                          AND paattynyt <= :loppupvm
                          AND tyyppi = :tyyppi :: toteumatyyppi
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE;

-- name: hae-urakan-toteutuneet-tehtavat-kuukausiraportille
-- Hakee urakan tietyntyyppiset toteutuneet tehtävät
SELECT
  tt.id                           AS id,
  tt.maara                        AS toteutunut_maara,
  t.lisatieto                     AS lisatieto,
  t.alkanut,
  (SELECT nimi
   FROM tehtava tpk
   WHERE id = tt.toimenpidekoodi) AS nimi,
  (SELECT id
   FROM tehtava tpk
   WHERE id = tt.toimenpidekoodi) AS toimenpidekoodi_id
FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND alkanut >= :alkupvm
                          AND alkanut <= :loppupvm
                          AND tyyppi = :tyyppi :: toteumatyyppi
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE;

-- name: listaa-urakan-hoitokauden-toteumat-muut-tyot
-- Hakee urakan muutos-, lisä- ja äkilliset hoitotyötoteumat
SELECT
  tt.id, -- Jotta "sarakkeet vektoriin" toimii oikein
  tt.id              AS tehtava_id,
  tt.toteuma         AS toteuma_id,
  tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
  tt.maara           AS tehtava_maara,
  tt.lisatieto       AS tehtava_lisatieto,
  tt.paivan_hinta    AS tehtava_paivanhinta,
  t.tyyppi,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  tr_numero,
  tr_alkuetaisyys,
  tr_alkuosa,
  tr_loppuetaisyys,
  tr_loppuosa,
  reitti,
  l.id   as liite_id,
  l.nimi as liite_nimi,
  l.tyyppi as liite_tyyppi,
  l.koko as liite_koko,
  l.liite_oid as liite_oid,
  tpk.emo            AS tehtava_emo,
  tpk.nimi           AS tehtava_nimi,
  o.nimi             AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma      AS jarjestelmasta
FROM toteuma_tehtava tt
  JOIN tehtava tpk ON tpk.id = tt.toimenpidekoodi
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND alkanut >= :alkupvm
                          AND alkanut <= :loppupvm
                          AND tyyppi IN ('akillinen-hoitotyo' :: toteumatyyppi,
                                         'lisatyo' :: toteumatyyppi,
                                         'muutostyo' :: toteumatyyppi,
                                         'vahinkojen-korjaukset' :: toteumatyyppi)
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN toteuma_liite tl ON tl.toteuma = t.id
  LEFT JOIN liite l ON l.id = tl.liite
  LEFT JOIN organisaatio o ON o.id = k.organisaatio;

-- name: hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla
-- Hakee urakan tietyntyyppiset toteutuneet tehtävät tietyllä toimenpidekoodilla
SELECT
  tt.id                           AS tehtava_id,
  tt.toteuma                      AS toteuma_id,
  tt.toimenpidekoodi,
  tt.maara,
  t.tyyppi,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi,
  t.suorittajan_ytunnus,
  t.lisatieto,
  k.jarjestelma                   AS jarjestelmanlisaama,
  (SELECT nimi
   FROM tehtava tpk
   WHERE id = tt.toimenpidekoodi) AS toimenpide,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys

FROM toteuma_tehtava tt
  INNER JOIN toteuma t ON tt.toteuma = t.id
                          AND urakka = :urakka
                          AND sopimus = :sopimus
                          AND alkanut >= :alkupvm
                          AND paattynyt <= :loppupvm
                          AND tyyppi = :tyyppi :: toteumatyyppi
                          AND toimenpidekoodi = :toimenpidekoodi
                          AND tt.poistettu IS NOT TRUE
                          AND t.poistettu IS NOT TRUE
  LEFT JOIN kayttaja k ON k.id = t.luoja
ORDER BY t.alkanut DESC
LIMIT 301;

-- name: paivita-toteuma<!
UPDATE toteuma
SET alkanut           = :alkanut,
  paattynyt           = :paattynyt,
  tyyppi              = :tyyppi :: toteumatyyppi,
  muokattu            = NOW(),
  muokkaaja           = :kayttaja,
  suorittajan_nimi    = :suorittaja,
  suorittajan_ytunnus = :ytunnus,
  lisatieto           = :lisatieto,
  tr_numero           = :numero,
  tr_alkuosa          = :alkuosa,
  tr_alkuetaisyys     = :alkuetaisyys,
  tr_loppuosa         = :loppuosa,
  tr_loppuetaisyys    = :loppuetaisyys,
  poistettu           = FALSE
WHERE id = :id AND urakka = :urakka;

-- name: paivita-toteuma-ulkoisella-idlla<!
UPDATE toteuma
SET alkanut           = :alkanut,
  paattynyt           = :paattynyt,
  muokattu            = NOW(),
  muokkaaja           = :kayttaja,
  suorittajan_nimi    = :suorittajan_nimi,
  suorittajan_ytunnus = :ytunnus,
  lisatieto           = :lisatieto,
  tyyppi              = :tyyppi :: toteumatyyppi,
  sopimus             = :sopimus,
  poistettu           = FALSE,
  tyokonetyyppi       = :tyokonetyyppi,
  tyokonetunniste     = :tyokonetunniste,
  tyokoneen_lisatieto = :tyokoneen-lisatieto
WHERE ulkoinen_id = :id AND urakka = :urakka;

-- name: luo-toteuma<!
-- Luo uuden toteuman.
INSERT
INTO toteuma
(urakka, sopimus, alkanut, paattynyt, tyyppi, luotu, luoja,
 poistettu, suorittajan_nimi, suorittajan_ytunnus, lisatieto, ulkoinen_id, reitti,
 tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys, lahde,
 tyokonetyyppi, tyokonetunniste, tyokoneen_lisatieto)
VALUES (:urakka, :sopimus, :alkanut, :paattynyt, :tyyppi :: toteumatyyppi, NOW(), :kayttaja,
                 FALSE, :suorittaja, :ytunnus, :lisatieto, :ulkoinen_id, :reitti,
        :numero, :alkuosa, :alkuetaisyys, :loppuosa, :loppuetaisyys, :lahde :: lahde,
        :tyokonetyyppi, :tyokonetunniste, :tyokoneen-lisatieto);

-- name: poista-toteuma!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

-- name: poista-toteuma-tehtava!
UPDATE toteuma_tehtava
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
    WHERE toteuma = :toteuma-id AND poistettu IS NOT TRUE;

-- name: poista-toteumat-ulkoisilla-idlla-ja-luojalla!
UPDATE toteuma
SET muokattu = NOW(), muokkaaja = :kayttaja-id, poistettu = TRUE
WHERE ulkoinen_id IN (:ulkoiset-idt) AND urakka = :urakka-id AND poistettu IS NOT TRUE;

-- name: hae-poistettavien-toteumien-alkanut-ulkoisella-idlla
SELECT alkanut
  FROM toteuma t
 WHERE ulkoinen_id IN (:ulkoiset-idt) AND urakka = :urakka-id AND poistettu IS NOT TRUE;

-- name: luo-tehtava<!
-- Luo uuden tehtävän toteumalle
INSERT
INTO toteuma_tehtava
(toteuma, toimenpidekoodi, maara, luotu, luoja, poistettu, paivan_hinta, indeksi, urakka_id)
VALUES (:toteuma, :toimenpidekoodi, :maara, NOW(), :kayttaja, FALSE, :paivanhinta,
        (CASE WHEN :paivanhinta :: NUMERIC IS NULL
          THEN TRUE
         ELSE FALSE
         END), :urakka_id);

-- name: poista-toteuman-tehtavat!
UPDATE toteuma_tehtava
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE toteuma = :id AND poistettu IS NOT TRUE;

-- name: poista-tehtava!
UPDATE toteuma_tehtava
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

-- name: onko-olemassa-ulkoisella-idlla
-- Tarkistaa löytyykö toteumaa ulkoisella id:llä
SELECT EXISTS(
    SELECT ulkoinen_id
    FROM toteuma
    WHERE ulkoinen_id = :ulkoinen_id AND urakka = :urakka_id);

-- name: listaa-urakan-hoitokauden-erilliskustannukset
-- Listaa urakan erilliskustannukset
SELECT e.id,
       e.tyyppi,
       e.urakka,
       e.sopimus,
       e.toimenpideinstanssi,
       e.pvm,
       e.laskutuskuukausi,
       e.rahasumma,
       e.indeksin_nimi,
       e.lisatieto,
       e.luotu,
       e.luoja,
       (SELECT korotettuna
        from erilliskustannuksen_indeksilaskenta(e.pvm, e.indeksin_nimi, e.rahasumma,
                                                 e.urakka, e.tyyppi,
                                                 CASE
                                                     WHEN u.tyyppi = 'teiden-hoito'::urakkatyyppi THEN TRUE
                                                     ELSE FALSE
                                                     END)) AS indeksikorjattuna,
       (SELECT korotettuna
        from erilliskustannuksen_indeksilaskenta(e.pvm, e.indeksin_nimi, e.rahasumma,
                                                 e.urakka, e.tyyppi,
                                                 CASE
                                                     WHEN u.tyyppi = 'teiden-hoito'::urakkatyyppi THEN TRUE
                                                     ELSE FALSE
                                                     END)) AS "bonus-indeksikorjattuna"
FROM erilliskustannus e
         JOIN urakka u ON e.urakka = u.id
WHERE e.urakka = :urakka
  AND e.laskutuskuukausi >= :alkupvm
  AND e.laskutuskuukausi <= :loppupvm
  AND e.poistettu IS NOT TRUE;

-- name: hae-urakan-toteumatehtavat
-- Haetaan toteumatehtäviä ei materiaalitehtäviä. Esim suolaus on materiaalitehtävä.
-- Ryhmittele ja summaa tiedot toimenpidekoodin eli tehtävän perusteella. Suunnitellut toteumat
-- haetaan erikseen ja ilman suunnittelua olevat toteumat erikseen, käyttäen unionia.
-- Haetaan tarpeeksi tietoa, jotta tehtävän sisältämät erilliset toteumat voidaan hakea erikseen.
WITH osa_toteumat AS
         (SELECT tt.toimenpidekoodi  AS toimenpidekoodi,
                 SUM(tt.maara)       AS maara,
                 SUM(tm.maara)       AS materiaalimaara,
                 :urakka             AS urakka,     -- Hakuehtojen perusteella tiedetään urakka, joten käytetään sitä
                 MAX(t.id)           AS toteuma_id, -- Kaikilla on sama toimenpidekoodi, joten on sama mitä toteumaa tietojen yhdistämisessä käytetään
                 MAX(tt.id)          AS toteuma_tehtava_id,
                 MAX(t.tyyppi::TEXT) AS tyyppi      -- Kaikilla on sama tyyppi, joten otetaan vain niistä joku
          FROM toteuma t
                   JOIN toteuma_tehtava tt ON t.id = tt.toteuma AND tt.urakka_id = :urakka AND tt.poistettu = FALSE
                   LEFT JOIN toteuma_materiaali tm
                             ON t.id = tm.toteuma AND tm.urakka_id = :urakka AND tm.poistettu = FALSE
          WHERE t.urakka = :urakka
            AND (t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE)
            AND t.poistettu = FALSE
          GROUP BY tt.toimenpidekoodi)
SELECT tk.id                                     AS toimenpidekoodi_id,
       o.otsikko                                 AS toimenpide,
       tk.nimi                                   AS tehtava,
       SUM(ot.maara)                             AS maara,
       SUM(ot.materiaalimaara)                   AS materiaalimaara,
       SUM(ut.maara)                             AS suunniteltu_maara,
       -- Ei voi olla sekä rahavaraus, että käsin lisättävä tehtävä. Rahavarauksille toteumat on euroja ja ne lisätään kuluista.
       CASE
           WHEN (tk.kasin_lisattava_maara AND r.nimi IS NULL) THEN TRUE
           ELSE FALSE END                        AS kasin_lisattava_maara,
       tk.suunnitteluyksikko                     AS yk,
       CASE
           WHEN o.otsikko = '9 LISÄTYÖT'
               THEN 'lisatyo'
           ELSE 'kokonaishintainen' END          AS tyyppi,
       COALESCE(NULLIF(ru.urakkakohtainen_nimi,''), r.nimi) AS rahavaraus

FROM tehtava tk
     -- Alataso on linkitetty toimenpidekoodiin
     JOIN tehtavaryhma tr_alataso ON tr_alataso.id = tk.tehtavaryhma
     JOIN tehtavaryhmaotsikko o ON tr_alataso.tehtavaryhmaotsikko_id = o.id AND (:tehtavaryhma::TEXT IS NULL OR o.otsikko = :tehtavaryhma)
     LEFT JOIN urakka_tehtavamaara ut ON ut.urakka = :urakka AND ut."hoitokauden-alkuvuosi" = :hoitokauden_alkuvuosi
                       AND ut.poistettu IS NOT TRUE AND tk.id = ut.tehtava
     LEFT JOIN osa_toteumat ot ON tk.id = ot.toimenpidekoodi
     LEFT JOIN rahavaraus_tehtava rt on rt.tehtava_id = tk.id
     LEFT JOIN rahavaraus_urakka ru
               ON rt.rahavaraus_id = ru.rahavaraus_id
                   AND ru.urakka_id = :urakka
     LEFT JOIN rahavaraus r ON ru.rahavaraus_id = r.id
     JOIN urakka u on u.id = :urakka
WHERE -- Rajataan pois hoitoluokka- eli aluetiedot paitsi, jos niihin saa kirjata toteumia käsin
      (tk.aluetieto = false OR (tk.aluetieto = TRUE AND tk.kasin_lisattava_maara = TRUE))
  AND tk."mhu-tehtava?" = true -- Rajataan pois ne, jotka eivät ole mhu tehtäviä.
  AND (tk.voimassaolo_alkuvuosi IS NULL OR tk.voimassaolo_alkuvuosi <= date_part('year', u.alkupvm)::INTEGER)
  AND (tk.voimassaolo_loppuvuosi IS NULL OR tk.voimassaolo_loppuvuosi >= date_part('year', u.alkupvm)::INTEGER)
  -- Rajataan pois tehtävät joilla ei ole suunnitteluyksikköä ja tehtävät joiden yksikkö on euro
  -- mutta otetaan mukaan Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen ja lisätyöt
  AND ((tk.suunnitteluyksikko IS not null AND tk.suunnitteluyksikko != 'euroa') OR
      tk.yksiloiva_tunniste IN ('49b7388b-419c-47fa-9b1b-3797f1fab21d',
                               '63a2585b-5597-43ea-945c-1b25b16a06e2',
                               'b3a7a210-4ba6-4555-905c-fef7308dc5ec',
                               'e32341fc-775a-490a-8eab-c98b8849f968',
                               '0c466f20-620d-407d-87b0-3cbb41e8342e',
                               'c058933e-58d3-414d-99d1-352929aa8cf9'))
GROUP BY tk.id, tk.nimi, o.otsikko, tk.kasin_lisattava_maara, tk.suunnitteluyksikko, ot.tyyppi, r.nimi, ru.urakkakohtainen_nimi
ORDER BY o.otsikko asc, tk.nimi asc;

-- name: listaa-tehtavan-toteumat
-- Haetaan yksittäiselle tehtavalle kaikki toteumat.
-- Summataan järjestelmän kautta lisätyt toteumat yhdelle riville, koska niitä tulee kymmeniätuhansia
-- yhden hoitokauden aikana ja mikään käyttöliittymä ei pysty niitä näyttämään järkevästi.
-- Käsin lisätyt toteumat sen sijaan listataan yksitellen.
SELECT 0 as id,
       now() as alkanut,
       sum(tt.maara) as maara,
       sum(tm.maara) AS materiaalimaara,
       'nimi'  AS nimi,
       'jarjestelma' as tyyppi
FROM toteuma t
     JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.urakka_id = :urakka AND tt.poistettu = FALSE
     LEFT JOIN toteuma_materiaali tm on tm.toteuma = t.id AND tm.urakka_id = :urakka AND tm.poistettu = FALSE
     JOIN tehtava tk ON tk.id = tt.toimenpidekoodi AND tt.toimenpidekoodi = :toimenpidekoodi-id
     JOIN kayttaja k ON k.id = t.luoja AND k.jarjestelma = true
WHERE t.urakka = :urakka
  AND t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND t.poistettu = false
UNION -- Haetaan yrittäisinä riveinä käsin lisätyt toteumat
SELECT t.id,
       t.alkanut,
       tt.maara,
       tm.maara AS materiaalimaara,
       tk.nimi AS nimi,
       'muu' as tyyppi
FROM toteuma t
     JOIN toteuma_tehtava tt ON tt.toteuma = t.id AND tt.urakka_id = :urakka AND tt.poistettu = FALSE
     LEFT JOIN toteuma_materiaali tm on tm.toteuma = t.id AND tm.urakka_id = :urakka AND tm.poistettu = FALSE
     JOIN tehtava tk ON tk.id = tt.toimenpidekoodi AND tt.toimenpidekoodi = :toimenpidekoodi-id
     JOIN kayttaja k ON k.id = t.luoja AND k.jarjestelma = FALSE
WHERE t.urakka = :urakka
  AND t.alkanut BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND t.poistettu = FALSE;

-- name: hae-maarien-toteuma
-- Hae yksittäinen toteuma muokkaukseen
-- Huomaa, että toteuman määrä voi tulla toteuma_materiaali taulusta, joka tulee määrien toteumat sivulle,
-- mutta materiaalimääriä ei voida muokata. Tämä haku ei siis koskaan tule hakemaan muualta kuin toteuma_tehtava taulusta
-- määriä.
SELECT t.id        AS toteuma_id,
       CASE
           WHEN EXTRACT(MONTH FROM t.alkanut) >= 10 THEN EXTRACT(YEAR FROM t.alkanut)::INT
           WHEN EXTRACT(MONTH FROM t.alkanut) <= 9 THEN (EXTRACT(YEAR FROM t.alkanut)-1)::INT
           END AS "hoitokauden-alkuvuosi",
       tk.nimi                    AS tehtava,
       tk.id                      AS tehtava_id,
       tt.maara                   AS toteutunut,
       t.alkanut                  AS toteuma_aika,
       tk.suunnitteluyksikko      AS yksikko,
       o.otsikko                  AS toimenpide_otsikko,
       tr.id                      AS toimenpide_id,
       tt.id                      AS toteuma_tehtava_id,
       tt.lisatieto               AS lisatieto,
       t.tyyppi                   AS tyyppi,
       k.jarjestelma              AS jarjestelma,
       t.tr_numero                as sijainti_numero,
       t.tr_alkuosa               as sijainti_alku,
       t.tr_alkuetaisyys          as sijainti_alkuetaisyys,
       t.tr_loppuosa              as sijainti_loppu,
       t.tr_loppuetaisyys         as sijainti_loppuetaisyys
    FROM toteuma_tehtava tt,
         tehtava tk,
         toteuma t,
         kayttaja k,
         tehtavaryhma tr
    JOIN tehtavaryhmaotsikko o ON tr.tehtavaryhmaotsikko_id = o.id
    WHERE t.id = :id
      AND tk.id = tt.toimenpidekoodi
      AND t.id = tt.toteuma
      AND t.poistettu = FALSE
      AND tt.poistettu = FALSE
      AND t.luoja = k.id
      AND tr.id = tk.tehtavaryhma;

-- name: tallenna-erilliskustannukselle-liitteet<!
-- Lisää liitteet
INSERT INTO erilliskustannus_liite
  (bonus, liite)
VALUES (:bonus, :liite);

-- name: luo-erilliskustannus<!
-- Listaa urakan erilliskustannukset
INSERT
INTO erilliskustannus
(tyyppi, urakka, sopimus, toimenpideinstanssi, pvm,
 rahasumma, indeksin_nimi, lisatieto, luotu, luoja, laskutuskuukausi, kasittelytapa)
VALUES (:tyyppi :: erilliskustannustyyppi, :urakka, :sopimus, :toimenpideinstanssi, :pvm,
        :rahasumma, :indeksin_nimi, :lisatieto, NOW(), :luoja, :laskutuskuukausi,
        :kasittelytapa :: laatupoikkeaman_kasittelytapa);

-- name: paivita-erilliskustannus!
-- Päivitä erilliskustannus
UPDATE erilliskustannus
SET tyyppi              = :tyyppi :: erilliskustannustyyppi,
    urakka              = :urakka,
    sopimus             = :sopimus,
    toimenpideinstanssi = :toimenpideinstanssi,
    pvm                 = :pvm,
    rahasumma           = :rahasumma,
    indeksin_nimi       = :indeksin_nimi,
    kasittelytapa       = :kasittelytapa :: laatupoikkeaman_kasittelytapa,
    laskutuskuukausi    = :laskutuskuukausi,
    lisatieto           = :lisatieto,
    muokattu            = NOW(),
    muokkaaja           = :muokkaaja,
    poistettu           = :poistettu
WHERE id = :id
      AND urakka = :urakka;

-- name: poista-erilliskustannus<!
UPDATE erilliskustannus
   SET muokattu  = NOW(),
       muokkaaja = :muokkaaja,
       poistettu = TRUE
 WHERE id = :id
   AND urakka = :urakka
RETURNING *;

-- name: paivita-toteuman-tehtava!
-- Päivittää toteuman tehtävän id:llä.
UPDATE toteuma_tehtava
SET toimenpidekoodi = :toimenpidekoodi, maara = :maara, poistettu = :poistettu,
  paivan_hinta      = :paivanhinta,
  indeksi           = (CASE WHEN :paivanhinta :: NUMERIC IS NULL
    THEN TRUE
                       ELSE FALSE
                       END)
WHERE id = :id;

-- name: poista-toteuman-tehtava!
-- Poistaa toteuman tehtävän
UPDATE toteuma_tehtava
SET poistettu = TRUE
WHERE id = :id;

-- name: merkitse-toteuman-maksuera-likaiseksi!
-- Merkitsee toteumaa vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE
  tyyppi = :tyyppi :: maksueratyyppi AND
  toimenpideinstanssi IN (SELECT tpi.id
                          FROM toimenpideinstanssi tpi
                            JOIN toimenpide emo ON emo.id = tpi.toimenpide
                            JOIN tehtava tpk ON tpk.emo = emo.id
                          WHERE tpk.id = :toimenpidekoodi AND tpi.urakka = :urakka AND tpi.loppupvm > current_timestamp - INTERVAL '3 months');

-- name: merkitse-toteumatehtavien-maksuerat-likaisiksi!
-- Merkitsee toteumaa vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE
WHERE
  numero IN (SELECT m.numero
             FROM maksuera m
               JOIN toimenpideinstanssi tpi ON tpi.id = m.toimenpideinstanssi AND tpi.loppupvm > current_timestamp - INTERVAL '3 months'
               JOIN toimenpide emo ON emo.id = tpi.toimenpide
               JOIN tehtava tpk ON tpk.emo = emo.id
               JOIN toteuma_tehtava tt ON tt.toimenpidekoodi = tpk.id
               JOIN toteuma t ON t.id = tt.toteuma
             WHERE tt.id IN (:toteuma_tehtava_idt) AND t.tyyppi :: TEXT = m.tyyppi :: TEXT);

-- name: merkitse-toimenpideinstanssin-maksuera-likaiseksi!
-- Merkitsee erilliskustannuksia vastaavan maksuerän likaiseksi: lähtetetään seuraavassa päivittäisessä lähetyksessä
UPDATE maksuera
SET likainen = TRUE,
    muokattu = current_timestamp
WHERE
  tyyppi = 'muu' AND
  toimenpideinstanssi IN (SELECT id
                          FROM toimenpideinstanssi
                          WHERE id = :toimenpideinstanssi AND loppupvm > current_timestamp - INTERVAL '3 months');

-- name: onko-toteumalla-suolausta
-- single?: true
SELECT EXISTS(SELECT * FROM materiaalikoodi WHERE nimi = ANY(ARRAY_REMOVE(ARRAY[:materiaalit]::TEXT[], null))
    AND materiaalityyppi IN ('talvisuola', 'formiaatti'))
OR EXISTS(SELECT * FROM tehtava WHERE id = ANY(ARRAY_REMOVE(ARRAY[:tehtavat]::INT[], null)) AND nimi = 'Suolaus');

-- name: hae-pisteen-hoitoluokat
-- Talvihoitoluokilta estetään hoitoluokat 9, 10 ja 11, jotka ovat kevyen liikenteen väyliä, koska
-- niitä ei todellisuudessa suolata. Näissä tapauksissa GPS-piste osoittaa virheellisesti kevyen liikenteen
-- väylälle, ja halutaan kohdistaa toteuma sen sijaan lähimmälle ajoväylälle.
SELECT hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY,
                             'talvihoito'::hoitoluokan_tietolajitunniste,
			     250::INTEGER, array_remove(ARRAY[:kielletyt_hoitoluokat]::INT[], null)) AS talvihoitoluokka,
       hoitoluokka_pisteelle(ST_MakePoint(:x, :y) :: GEOMETRY,
                             'soratie'::hoitoluokan_tietolajitunniste,
			     250::INTEGER) AS soratiehoitoluokka;

-- name: luo-toteuma_tehtava<!
-- Luo uuden toteuman tehtävän
INSERT INTO toteuma_tehtava (toteuma, luotu, toimenpidekoodi, maara, luoja, paivan_hinta,
                             lisatieto, indeksi, urakka_id)
VALUES (:toteuma, NOW(), :toimenpidekoodi, :maara, :luoja, :paivan_hinta, :lisatieto,
        (CASE WHEN :paivan_hinta :: NUMERIC IS NULL
          THEN TRUE
         ELSE FALSE
         END), :urakka_id);

-- name: poista-toteuma_tehtava-toteuma-idlla!
-- Poistaa toteuman kaikki tehtävät
DELETE FROM toteuma_tehtava
WHERE toteuma = :id;

-- name: luo-toteuma-materiaali<!
-- Luo uuden toteuman materiaalin
INSERT INTO toteuma_materiaali (toteuma, luotu, materiaalikoodi, maara, luoja, urakka_id)
VALUES (:toteuma, NOW(), :materiaalikoodi, :maara, :luoja, :urakka);

-- name: poista-toteuma-materiaali-toteuma-idlla!
-- Poistaa toteuman materiaalit
DELETE FROM toteuma_materiaali
WHERE toteuma = :id;

-- name: paivita-varustetoteuman-tr-osoite!
-- Kysely piti katkaista kahtia, koska Yesql <0.5 tukee parametreja max 20
UPDATE varustetoteuma
SET
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  tr_puoli         = :tr_puoli,
  tr_ajorata       = :tr_ajorata
WHERE id = :id;

-- name: luo-varustetoteuma<!
-- Luo uuden varustetoteuman
INSERT INTO varustetoteuma (tunniste,
                            toteuma,
                            toimenpide,
                            tietolaji,
                            arvot,
                            karttapvm,
                            alkupvm,
                            loppupvm,
                            piiri,
                            kuntoluokka,
                            tierekisteriurakkakoodi,
                            luoja,
                            luotu,
                            tr_numero,
                            tr_alkuosa,
                            tr_alkuetaisyys,
                            tr_loppuosa,
                            tr_loppuetaisyys,
                            tr_puoli,
                            tr_ajorata,
                            sijainti)
VALUES (:tunniste,
  :toteuma,
  :toimenpide :: varustetoteuma_tyyppi,
  :tietolaji,
  :arvot,
  :karttapvm,
  :alkupvm,
  :loppupvm,
  :piiri,
  :kuntoluokka,
  :tierekisteriurakkakoodi,
  :kayttaja,
  NOW(),
  :tr_numero,
  :tr_alkuosa,
  :tr_alkuetaisyys,
  :tr_loppuosa,
  :tr_loppuetaisyys,
  :tr_puoli,
  :tr_ajorata,
  :sijainti);

-- name: paivita-varustetoteuma!
-- Päivittää annetun varustetoteuman
UPDATE varustetoteuma
SET
  tunniste                = :tunniste,
  toteuma                 = :toteuma,
  toimenpide              = :toimenpide :: VARUSTETOTEUMA_TYYPPI,
  tietolaji               = :tietolaji,
  arvot                   = :arvot,
  karttapvm               = :karttapvm,
  alkupvm                 = :alkupvm,
  loppupvm                = :loppupvm,
  piiri                   = :piiri,
  kuntoluokka             = :kuntoluokka,
  tierekisteriurakkakoodi = :tierekisteriurakkakoodi,
  tr_numero               = :tr_numero,
  tr_alkuosa              = :tr_alkuosa,
  tr_alkuetaisyys         = :tr_alkuetaisyys,
  tr_loppuosa             = :tr_loppuosa,
  tr_loppuetaisyys        = :tr_loppuetaisyys,
  tr_puoli                = :tr_puoli,
  tr_ajorata              = :tr_ajorata,
  sijainti                = :sijainti,
  muokkaaja               = :kayttaja,
  muokattu                = current_timestamp
WHERE id = :id;

-- name: hae-yksikkohintaisten-toiden-reitit
-- fetch-size: 64
-- row-fn: muunna-reitti
SELECT
  ST_Simplify(t.reitti, :toleranssi) AS reitti,
  tt.toimenpidekoodi                 AS tehtava_toimenpidekoodi,
  tpk.nimi                           AS tehtava_toimenpide
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN tehtava tpk ON tt.toimenpidekoodi = tpk.id
WHERE
  t.urakka = :urakka-id
  AND (:toteuma-id :: INTEGER IS NULL OR t.id = :toteuma-id)
  AND t.sopimus = :sopimus-id
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax))
  AND t.tyyppi = 'yksikkohintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpide :: INTEGER IS NULL OR
       tpk.emo = (SELECT toimenpide
                  FROM toimenpideinstanssi
                  WHERE id = :toimenpide))
  AND (:tehtava :: INTEGER IS NULL OR tpk.id = :tehtava);

-- name: hae-kokonaishintaisten-toiden-reitit
-- fetch-size: 64
-- row-fn: muunna-reitti
SELECT
  ST_Simplify(t.reitti, :toleranssi) AS reitti,
  tk.nimi                            AS tehtava_toimenpide
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN tehtava tk ON tt.toimenpidekoodi = tk.id
WHERE
  t.urakka = :urakka-id
  AND (:toteuma-id :: INTEGER IS NULL OR t.id = :toteuma-id)
  AND t.sopimus = :sopimus-id
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax))
  AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpidekoodi :: INTEGER IS NULL OR tk.id = :toimenpidekoodi);

-- name: hae-toteumien-tiedot-pisteessa
-- Hakee klikkauspisteessä olevien (valitun toimenpiteen) toteumien
-- tiedot infopaneelissa näytettäväksi.
SELECT
  t.id,
  t.alkanut,
  t.paattynyt,
  t.suorittajan_nimi                                        AS suorittaja_nimi,
  tk.nimi                                                   AS tehtava_toimenpide,
  tt.maara                                                  AS tehtava_maara,
  tk.yksikko                                                AS tehtava_yksikko,
  tt.toteuma                                                AS tehtava_id,
  tk.nimi                                                   AS toimenpide,
  mk.nimi AS materiaalitoteuma_materiaali_nimi,
  mk.yksikko AS materiaalitoteuma_materiaali_yksikko,
  tm.maara AS materiaalitoteuma_maara,
  tm.id AS materiaalitoteuma_id,
  yrita_tierekisteriosoite_pisteille2(
      alkupiste(t.reitti), loppupiste(t.reitti), 1) :: TEXT AS tierekisteriosoite
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN tehtava tk ON tt.toimenpidekoodi = tk.id
  LEFT JOIN toteuma_materiaali tm ON t.id = tm.toteuma AND tm.poistettu = FALSE
  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE
  t.urakka = :urakka-id
  AND (:toteuma-id :: INTEGER IS NULL OR t.id = :toteuma-id)
  AND t.sopimus = :sopimus-id
  AND t.alkanut >= :alkupvm
  AND t.alkanut <= :loppupvm
  AND ST_Distance84(t.reitti, ST_MakePoint(:x, :y)) < :toleranssi
  AND t.tyyppi = :tyyppi :: toteumatyyppi
  AND t.poistettu IS NOT TRUE
  AND (:toimenpidekoodi :: INTEGER IS NULL OR tk.id = :toimenpidekoodi);

-- name: hae-kokonaishintaisen-toteuman-reitti
SELECT
  mk.nimi            AS materiaali_nimi,
  tm.maara           AS materiaali_maara,
  tt.toteuma         AS toteumaid,
  t.alkanut          AS alkanut,
  t.paattynyt        AS paattynyt,
  t.reitti,
  t.suorittajan_nimi AS suorittaja_nimi,
  t.lisatieto        AS lisatieto,
  tk.nimi            AS tehtava_toimenpide,
  tt.maara           AS tehtava_maara,
  tk.id              AS tehtava_id
FROM toteuma_tehtava tt
  JOIN toteuma t ON tt.toteuma = t.id
  JOIN tehtava tk ON tt.toimenpidekoodi = tk.id
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
  LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
WHERE
  t.urakka = :urakkaid
  AND t.sopimus = :sopimusid
  AND t.id = :toteumaid
  AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
  AND t.poistettu IS NOT TRUE;

-- name: hae-urakan-kokonaishintaiset-toteumat-paivakohtaisina-summina
SELECT
  x.pvm,
  x.toimenpidekoodi,
  x.maara,
  k.jarjestelma AS jarjestelmanlisaama,
  tk.nimi       AS nimi,
  tk.yksikko    AS yksikko
FROM -- Haetaan toteuma tehtävät summattuna
  (SELECT
     t.alkanut :: DATE        AS pvm,
     tt.toimenpidekoodi,
     SUM(tt.maara)            AS maara,
     tt.luoja
   FROM toteuma_tehtava tt
     JOIN -- Haetaan ensin vain toteumat, jotka osuvat filttereihin
     -- tämän avulla planner tajuaa käyttää toteuma_tehtavan toteuma indeksiä
     (SELECT
        t.alkanut,
        t.id,
        t.reitti
      FROM toteuma t
      WHERE t.urakka = :urakkaid
            AND t.sopimus = :sopimusid
            AND t.alkanut >= :alkupvm
            AND t.alkanut <= :loppupvm
            AND t.tyyppi = 'kokonaishintainen' :: toteumatyyppi
            AND t.poistettu IS NOT TRUE) t ON t.id = tt.toteuma
   WHERE tt.poistettu IS NOT TRUE
         AND tt.toimenpidekoodi IN (SELECT id
                                    from tehtava tk
                                    WHERE (:toimenpide :: INTEGER IS NULL OR
                                           tk.emo = (SELECT toimenpide
                                                     FROM toimenpideinstanssi
                                                     WHERE id = :toimenpide))
                                          AND (:tehtava :: INTEGER IS NULL OR tk.id = :tehtava))
   GROUP BY pvm, toimenpidekoodi, luoja) x
  JOIN -- Otetaan mukaan käyttäjät järjestelmätietoa varten
  kayttaja k ON x.luoja = k.id
  JOIN -- Otetaan mukaan toimenpidekoodi nimeä ja yksikköä varten
  tehtava tk ON x.toimenpidekoodi = tk.id
ORDER BY pvm DESC;

-- name: hae-toteuman-tehtavat
SELECT
  tt.id              AS id,
  tt.toimenpidekoodi AS toimenpidekoodi,
  tk.nimi            AS nimi,
  tt.maara           AS maara,
  tk.yksikko         AS yksikko
FROM toteuma_tehtava tt
  INNER JOIN tehtava tk
    ON tk.id = tt.toimenpidekoodi
WHERE
  tt.toteuma = :toteuma_id AND tt.poistettu IS NOT TRUE;

-- name: hae-toteuman-reittipisteet
SELECT rp.aika     AS aika,
       rp.sijainti AS sijainti,
       rp.ordinality AS id
  FROM toteuma t
       JOIN toteuman_reittipisteet tr ON tr.toteuma = t.id
       JOIN LATERAL unnest(reittipisteet) WITH ORDINALITY rp ON TRUE
 WHERE t.id = :toteuma_id;

-- name: hae-toteuman-reitti-ja-tr-osoite
SELECT
  tr_numero,
  tr_alkuetaisyys,
  tr_alkuosa,
  tr_loppuetaisyys,
  tr_loppuosa,
  reitti
FROM toteuma
WHERE id = :id;

-- name: paivita-toteuma-materiaali!
-- Päivittää toteuma materiaalin tiedot
UPDATE toteuma_materiaali
SET materiaalikoodi = :materiaali,
  maara             = :maara,
  muokkaaja         = :kayttaja,
  muokattu          = now()
WHERE id = :tmid
      AND toteuma IN (SELECT id
                      FROM toteuma t
                      WHERE t.urakka = :urakka);

-- name: hae-urakan-varustetoteumat
SELECT
  vt.id,
  tunniste,
  toimenpide,
  tietolaji,
  vt.luotu,
  vt.tr_numero        AS tierekisteriosoite_numero,
  vt.tr_alkuosa       AS tierekisteriosoite_alkuosa,
  vt.tr_alkuetaisyys  AS tierekisteriosoite_alkuetaisyys,
  vt.tr_loppuosa      AS tierekisteriosoite_loppuosa,
  vt.tr_loppuetaisyys AS tierekisteriosoite_loppuetaisyys,
  piiri,
  kuntoluokka,
  karttapvm,
  tr_puoli            AS puoli,
  tr_ajorata          AS ajorata,
  t.id                AS toteumaid,
  t.urakka            AS urakkaid,
  t.alkanut,
  t.paattynyt,
  t.tyyppi            AS toteumatyyppi,
  arvot,
  tierekisteriurakkakoodi,
  vt.sijainti         AS sijainti,
  t.id                AS toteuma_id,
  t.reitti            AS toteumareitti,
  tt.id               AS toteumatehtava_id,
  tt.toimenpidekoodi  AS toteumatehtava_toimenpidekoodi,
  tt.maara            AS toteumatehtava_maara,
  tpk.nimi            AS toteumatehtava_nimi,
  t.lisatieto,
  alkupvm,
  loppupvm,
  lahetetty,
  lahetysvirhe,
  tila,
  k.etunimi           AS "luojan-etunimi",
  k.sukunimi          AS "luojan-sukunimi"
FROM varustetoteuma vt
     JOIN toteuma t ON vt.toteuma = t.id AND t.sopimus = :sopimus and t.urakka = :urakka
                        and t.alkanut between :alkupvm and :loppupvm and t.poistettu = false
     LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id  AND tt.poistettu = FALSE
     LEFT JOIN tehtava tpk ON tt.toimenpidekoodi = tpk.id
     left join kayttaja k on vt.luoja = k.id
WHERE (:rajaa_tienumerolla = FALSE OR vt.tr_numero = :tienumero)
  AND (:tietolajit :: VARCHAR [] IS NULL OR
           vt.tietolaji = ANY (:tietolajit :: VARCHAR []))
ORDER BY vt.luotu DESC
    LIMIT 2001;


-- name: hae-kokonaishintaisen-toteuman-tiedot
-- Hakee urakan kokonaishintaiset toteumat annetun päivän ja toimenpidekoodin perusteella
-- tai suoraan toteuman id:lla.
SELECT
  t.id,
  t.luotu,
  t.alkanut,
  t.paattynyt,
  t.lisatieto,
  t.suorittajan_ytunnus AS suorittaja_ytunnus,
  t.suorittajan_nimi    AS suorittaja_nimi,
  k.jarjestelma,
  tt.maara              AS tehtava_maara,
  tt.id                 AS tehtava_id,
  tpk.yksikko           AS tehtava_yksikko,
  tpk.id                AS tehtava_toimenpidekoodi_id,
  tpk.nimi              AS tehtava_toimenpidekoodi_nimi,
  tpi.id                AS tehtava_toimenpideinstanssi_id,
  tpi.nimi              AS tehtava_toimenpideinstanssi_nimi,
  ST_Length(reitti)     AS pituus,
  t.tr_numero, t.tr_alkuosa, t.tr_alkuetaisyys, t.tr_loppuosa, t.tr_loppuetaisyys
FROM toteuma t
  JOIN kayttaja k ON t.luoja = k.id
                     AND t.poistettu IS NOT TRUE
  LEFT JOIN toteuma_tehtava tt ON t.id = tt.toteuma
                                  AND tt.poistettu IS NOT TRUE
  LEFT JOIN tehtava tpk ON tt.toimenpidekoodi = tpk.id
  LEFT JOIN toimenpide emo ON tpk.emo = emo.id
  LEFT JOIN toimenpideinstanssi tpi ON emo.id = tpi.toimenpide
                                       AND tpi.urakka = t.urakka
WHERE
  t.urakka = :urakka
  AND (:toteuma :: INTEGER IS NULL OR t.id = :toteuma)
  AND (:pvm :: DATE IS NULL OR t.alkanut >= :pvm::DATE AND t.alkanut < :pvm::DATE + interval '1 day')
  AND (:toimenpidekoodi :: INTEGER IS NULL OR tt.toimenpidekoodi = :toimenpidekoodi);

-- name: hae-varustetoteuma
SELECT
  vt.toimenpide,
  vt.toteuma,
  vt.karttapvm,
  vt.tunniste,
  vt.alkupvm,
  vt.loppupvm,
  vt.tietolaji,
  vt.arvot,
  vt.tr_numero,
  vt.tr_alkuosa,
  vt.tr_alkuetaisyys,
  vt.tr_loppuosa,
  vt.tr_loppuetaisyys,
  vt.tr_ajorata,
  vt.tr_puoli,
  vt.luotu,
  k.etunimi || ' ' || k.sukunimi AS henkilo,
  o.nimi                         AS organisaatio,
  o.ytunnus                      AS yTunnus
FROM varustetoteuma vt
  JOIN kayttaja k ON vt.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
WHERE vt.id = :id;

-- name: hae-varustetoteuma-toteumalla
SELECT
  id,
  tunniste
FROM varustetoteuma
WHERE
  toteuma = :toteumaid
  AND (:tunniste :: TEXT IS NULL OR tunniste = :tunniste)
  AND tietolaji = :tietolaji
  AND toimenpide = :toimenpide :: varustetoteuma_tyyppi
  AND (:tr_numero :: INTEGER IS NULL OR tr_numero = :tr_numero)
  AND (:tr_aosa :: INTEGER IS NULL OR tr_alkuosa = :tr_aosa)
  AND (:tr_aet :: INTEGER IS NULL OR tr_alkuetaisyys = :tr_aet)
  AND (:tr_losa :: INTEGER IS NULL OR tr_loppuosa = :tr_losa)
  AND (:tr_let :: INTEGER IS NULL OR tr_loppuetaisyys = :tr_let)
  AND (:tr_ajorata :: INTEGER IS NULL OR tr_ajorata = :tr_ajorata)
  AND (:tr_puoli :: INTEGER IS NULL OR tr_puoli = :tr_puoli);

-- name: hae-varustetoteuman-lahetystiedot
SELECT lahetetty_tierekisteriin
FROM varustetoteuma
WHERE id = :id;

-- name: merkitse-varustetoteuma-lahetetyksi<!
UPDATE varustetoteuma
SET lahetetty_tierekisteriin = TRUE,
    lahetetty = now(),
    tila = :tila :: lahetyksen_tila,
    lahetysvirhe = :lahetysvirhe
WHERE id = :id;

-- name: varustetoteuman-piste-sijainti
SELECT sijainti
FROM tierekisteriosoitteelle_piste(:tie :: INTEGER,
                                   :aosa :: INTEGER,
                                   :aet :: INTEGER) AS sijainti;

-- name: varustetoteuman-viiva-sijainti
SELECT sijainti
FROM tierekisteriosoitteelle_viiva(:tie :: INTEGER,
                                   :aosa :: INTEGER,
                                   :aet :: INTEGER,
                                   :losa :: INTEGER,
                                   :let :: INTEGER) AS sijainti;

-- name: varustetoteuman-toimenpiteelle-sijainti
SELECT sijainti
FROM tierekisteriosoitteelle_viiva(:tie :: INTEGER,
                                   :aosa :: INTEGER,
                                   :aet :: INTEGER,
                                   :losa :: INTEGER,
                                   :let :: INTEGER) AS sijainti;

-- name: paivita-toteuman-reitti!
UPDATE toteuma
SET reitti = :reitti
WHERE id = :id;

-- name: paivita-toteuman-reitti<!
UPDATE toteuma
SET reitti = :reitti
WHERE id = :id;

-- AJASTETTUJA TEHTÄVIÄ VARTEN

-- name: hae-reitittomat-mutta-reittipisteelliset-toteumat
-- Hakee toteumat, joille on olemassa reittipisteitä, mutta reittiä ei ole jostain syystä saatu tehtyä.
-- Käytetään ajastetussa tehtävässä
SELECT DISTINCT t.id
  FROM toteuma t
       JOIN toteuman_reittipisteet tr ON t.id = tr.toteuma
 WHERE t.reitti IS NULL;

-- name: hae-reitittomat-mutta-osoitteelliset-toteumat
-- Hakee toteumat, joille on tr-osoite, mutta reittiä ei ole saatu laskettua.
-- Käytetään ajastetussa tehtävässä
SELECT id,
       tr_numero        AS numero,
       tr_alkuosa       AS alkuosa,
       tr_alkuetaisyys  AS alkuetaisyys,
       tr_loppuosa      AS loppuosa,
       tr_loppuetaisyys AS loppuetaisyys
  FROM toteuma t
 WHERE reitti IS NULL
   AND t.tr_numero IS NOT NULL
   AND t.tr_alkuosa IS NOT NULL
   AND t.tr_alkuetaisyys IS NOT NULL;

-- name: merkitse-varustetoteuma-lahetetyksi!
UPDATE varustetoteuma
SET lahetetty = now(), tila = :tila :: lahetyksen_tila, lahetysvirhe = :lahetysvirhe
WHERE id = :id;

-- name: hae-epaonnistuneet-varustetoteuman-lahetykset
-- Palauttaa rivit, joiden lähetys on epäonnistunut ja
-- jotka on luotu tai joita on muokattu viimeisen viikon aikana
SELECT id
FROM varustetoteuma
WHERE tila = 'virhe'
  and ((luotu IS NOT NULL AND (EXTRACT(EPOCH FROM (current_timestamp - luotu)) < 604800)) OR
       (muokattu IS NOT NULL AND (EXTRACT(EPOCH FROM (current_timestamp - muokattu)) < 604800)));

-- name: suhteellinen-paikka-pisteiden-valissa
SELECT
  ST_LineLocatePoint(v.viiva ::geometry, ST_ClosestPoint (v.viiva ::geometry, :piste ::geometry) ::geometry) AS paikka
FROM
  (SELECT ST_MakeLine(:rp1 ::geometry, :rp2 ::geometry) AS viiva) v;

-- name: siirry-toteuma
-- Palauttaa tiedot, joita tarvitaan frontilla toteumaan siirtymiseen ja
-- tarkistaa että käyttäjällä on oikeus urakkaan, johon toteuma kuuluu
SELECT t.alkanut, t.urakka AS "urakka-id", u.hallintayksikko AS "hallintayksikko-id",
       t.tyyppi,
       tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
       tpk3.koodi AS tehtava_toimenpideinstanssi,
       hk.alkupvm AS aikavali_alku,
       hk.loppupvm AS aikavali_loppu
  FROM toteuma t
       JOIN urakka u ON t.urakka = u.id
       LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
       LEFT JOIN tehtava tpk ON tt.toimenpidekoodi = tpk.id
       LEFT JOIN toimenpide tpk3 ON tpk.emo = tpk3.id
       JOIN urakan_hoitokaudet(t.urakka) hk ON (t.alkanut BETWEEN hk.alkupvm AND hk.loppupvm)
 WHERE t.id = :toteuma-id
   AND (:tarkista-urakka? = FALSE
        OR u.urakoitsija = :urakoitsija-id);

-- name: tallenna-liite-toteumalle<!
INSERT INTO toteuma_liite (toteuma, liite) VALUES (:toteuma, :liite);

-- name: hae-toteuman-liitteet
SELECT l.id                  AS id,
       l.tyyppi              AS tyyppi,
       l.koko                AS koko,
       l.nimi                AS nimi,
       l.liite_oid           AS oid,
       l."virustarkastettu?" AS "virustarkastettu?"
  FROM liite l
           JOIN toteuma_liite tl ON l.id = tl.liite
 WHERE tl.toteuma = :toteumaid
 ORDER BY l.luotu ASC;

-- name: hae-toteumien-reitit
SELECT
  id,
  reitti as sijainti
FROM toteuma
WHERE urakka = :urakka-id AND id IN (:idt);

-- name: luodun-toteuman-id
-- single?: true
-- Koska toteuman luonti ohjataan triggerillä eri tauluun, ei toteuman insert palauta oikein
-- id kenttää. Tällä haetaan viimeksi luodun arvo.
SELECT currval('toteuma_id_seq');

-- name: toteuman-id-ulkoisella-idlla
-- single?: true
SELECT id FROM toteuma where ulkoinen_id = :ulkoinen_id;

-- name: hae-toteuman-alkanut-pvm-idlla
-- single?: true
SELECT alkanut
  FROM toteuma
 WHERE id = :id;

-- name: hae-reittitoteumat-analytiikalle
SELECT t.toteuma_tunniste_id,
       t.toteuma_sopimus_id,
       t.toteuma_alkanut,
       t.toteuma_paattynyt,
       t.toteuma_alueurakkanumero,
       t.toteuma_suorittaja_ytunnus,
       t.toteuma_suorittaja_nimi,
       t.toteuma_toteumatyyppi,
       t.toteuma_lisatieto,
       t.toteumatehtavat,
       t.toteumamateriaalit,
       CASE
           WHEN :koordinaattimuutos THEN
               json_agg(row_to_json(row (rp.aika, rp.tehtavat, st_transform(st_setsrid(rp.sijainti::geometry, 3067), 4326)::point, rp.sijainti, rp.materiaalit)))
           ELSE
               json_agg(row_to_json(row (rp.aika, rp.tehtavat, null::point, rp.sijainti, rp.materiaalit)))
           END AS reitti,
       t.toteuma_tiesijainti_numero,
       t.toteuma_tiesijainti_aosa,
       t.toteuma_tiesijainti_aet,
       t.toteuma_tiesijainti_losa,
       t.toteuma_tiesijainti_let,
       t.toteuma_muutostiedot_luotu,
       t.toteuma_muutostiedot_luoja,
       t.toteuma_muutostiedot_muokattu,
       t.toteuma_muutostiedot_muokkaaja,
       t.tyokone_tyokonetyyppi,
       t.tyokone_tunnus,
       t.urakkaid,
       t.poistettu
FROM analytiikka_toteumat t
         LEFT JOIN toteuman_reittipisteet tr ON tr.toteuma = t.toteuma_tunniste_id
         LEFT JOIN LATERAL unnest(tr.reittipisteet) AS rp ON true
WHERE ((t.toteuma_muutostiedot_muokattu IS NOT NULL AND t.toteuma_muutostiedot_muokattu BETWEEN :alkuaika::TIMESTAMP AND :loppuaika::TIMESTAMP)
    OR (t.toteuma_muutostiedot_muokattu IS NULL AND t.toteuma_muutostiedot_luotu BETWEEN :alkuaika::TIMESTAMP AND :loppuaika::TIMESTAMP))
group by toteuma_tunniste_id
ORDER BY t.toteuma_alkanut ASC
LIMIT 100000;

-- name: siirra-toteumat-analytiikalle
select siirra_toteumat_analytiikalle(:nyt::TIMESTAMP WITH TIME ZONE);

-- name: lisaa-toteumalle-jsonhash!
UPDATE toteuma SET json_hash = :hash WHERE id = :id;

-- name: hae-toteuman-hash
SELECT EXISTS(SELECT id FROM toteuma WHERE json_hash = :hash AND ulkoinen_id = :ulkoinen-id);
