-- name: hae-materiaalikoodit
-- Hakee kaikki järjestelmän materiaalikoodit
SELECT
  id,
  nimi,
  yksikko,
  urakkatyyppi,
  kohdistettava
FROM materiaalikoodi;

-- name: hae-materiaalikoodit-ilman-talvisuolaa
-- Hakee kaikki paitsi talvisuola tyyppiset materiaalikoodit
SELECT
  id,
  nimi,
  yksikko,
  urakkatyyppi,
  kohdistettava
FROM materiaalikoodi
WHERE materiaalityyppi != 'talvisuola' :: materiaalityyppi;

-- name: hae-urakan-materiaalit
-- Hakee jokaisen materiaalin, joka liittyy urakkaan JA JOLLA ON RIVI MATERIAALIN_KAYTTO taulussa.
-- Oleellista on, että palauttaa yhden rivin per materiaali, ja laskee yhteen paljonko materiaalia
-- on käytetty.
SELECT
  mk.id,
  mk.alkupvm,
  mk.loppupvm,
  mk.maara,
  mk.sopimus,
          m.id      AS materiaali_id,
          m.nimi    AS materiaali_nimi,
          m.yksikko AS materiaali_yksikko,
          pa.id     AS pohjavesialue_id,
          pa.nimi   AS pohjavesialue_nimi,
          pa.tunnus AS pohjavesialue_tunnus,
  (SELECT SUM(maara) AS kokonaismaara
   FROM toteuma_materiaali
   WHERE materiaalikoodi = mk.id AND toteuma IN (SELECT id
                                                 FROM toteuma
                                                 WHERE urakka = :urakka))
FROM materiaalin_kaytto mk
  LEFT JOIN materiaalikoodi m ON mk.materiaali = m.id
  LEFT JOIN pohjavesialue pa ON mk.pohjavesialue = pa.id
WHERE mk.urakka = :urakka AND
      mk.poistettu = FALSE AND
      m.materiaalityyppi != 'talvisuola' :: materiaalityyppi;

-- name: hae-urakassa-kaytetyt-materiaalit
-- Hakee urakassa käytetyt materiaalit, palauttaen yhden rivin jokaiselle materiaalille,
-- laskien samalla yhteen kuinka paljon materiaalia on käytetty. Palauttaa myös käytetyt
-- materiaalit, joille ei ole riviä materiaalin_kaytto taulussa (eli käytetty sopimuksen ulkopuolella)
-- määrä = suunniteltu määrä. kokonaismäärä = toteutunut määrä
SELECT DISTINCT
           m.nimi    AS materiaali_nimi,
           m.yksikko AS materiaali_yksikko,
           m.id      AS materiaali_id,
  (SELECT  SUM(maara) AS maara
   FROM materiaalin_kaytto
   WHERE materiaali = m.id
         AND poistettu IS NOT TRUE
         AND alkupvm :: DATE BETWEEN :alku AND :loppu
         AND loppupvm :: DATE BETWEEN :alku AND :loppu
         AND sopimus = :sopimus),

  (
    SELECT SUM(maara) AS kokonaismaara
    FROM toteuma_materiaali
    WHERE materiaalikoodi = m.id AND
          toteuma IN
          (
            SELECT id
            FROM toteuma
            WHERE
              alkanut :: DATE >= :alku AND
              alkanut :: DATE <= :loppu AND
              sopimus = :sopimus AND
              poistettu IS NOT TRUE) AND
          poistettu IS NOT TRUE
  )

FROM materiaalikoodi m
  LEFT JOIN materiaalin_kaytto mk
    ON m.id = mk.materiaali
       AND mk.poistettu IS NOT TRUE
       AND mk.alkupvm :: DATE BETWEEN :alku AND :loppu
       AND mk.loppupvm :: DATE BETWEEN :alku AND :loppu
       AND mk.sopimus = :sopimus


  LEFT JOIN toteuma_materiaali tm
    ON tm.materiaalikoodi = m.id
       AND tm.poistettu IS NOT TRUE

  LEFT JOIN toteuma t
    ON t.id = tm.toteuma AND t.poistettu IS NOT TRUE
       AND t.alkanut :: DATE BETWEEN :alku AND :loppu
       AND t.sopimus = :sopimus
WHERE m.materiaalityyppi != 'talvisuola' :: materiaalityyppi
      AND ((SELECT SUM(maara) AS maara
            FROM materiaalin_kaytto
            WHERE materiaali = m.id
                  AND poistettu IS NOT TRUE
                  AND alkupvm :: DATE BETWEEN :alku AND :loppu
                  AND loppupvm :: DATE BETWEEN :alku AND :loppu
                  AND sopimus = :sopimus) IS NOT NULL
           OR (
                SELECT SUM(maara) AS kokonaismaara
                FROM toteuma_materiaali
                WHERE materiaalikoodi = m.id AND
                      toteuma IN
                      (
                        SELECT id
                        FROM toteuma
                        WHERE
                          alkanut :: DATE >= :alku AND
                          alkanut :: DATE <= :loppu AND
                          sopimus = :sopimus AND
                          poistettu IS NOT TRUE) AND
                      poistettu IS NOT TRUE
              ) IS NOT NULL);

-- name: hae-urakan-suunnitellut-materiaalit-raportille
SELECT DISTINCT
  urakka.nimi             AS urakka_nimi,
  materiaalikoodi.nimi    AS materiaali_nimi,
  materiaalikoodi.yksikko AS materiaali_yksikko
FROM materiaalin_kaytto
  JOIN urakka ON materiaalin_kaytto.urakka = urakka.id
  JOIN materiaalikoodi ON materiaalikoodi.id = materiaalin_kaytto.materiaali
WHERE urakka = :urakka
      AND materiaalin_kaytto.alkupvm :: DATE >= :alkuvpm
      AND materiaalin_kaytto.alkupvm :: DATE <= :alkupvm
      AND poistettu IS NOT TRUE;

-- name: hae-urakan-toteutuneet-materiaalit-raportille
-- Palauttaa urakan materiaalit ja määrät omilla riveillä.
-- Samat materiaalit summataan yhteen.
SELECT
  SUM(maara)              AS kokonaismaara,
  urakka.nimi             AS urakka_nimi,
  materiaalikoodi.nimi    AS materiaali_nimi,
  materiaalikoodi.yksikko AS materiaali_yksikko
FROM toteuma_materiaali
  LEFT JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
  LEFT JOIN urakka ON urakka.id = (SELECT urakka
                                   FROM toteuma
                                   WHERE id = toteuma_materiaali.toteuma)
  JOIN toteuma ON toteuma.id = toteuma
                  AND urakka.id = :urakka
                  AND alkanut :: DATE >= :alku
                  AND alkanut :: DATE <= :loppu
                  AND toteuma.poistettu IS NOT TRUE
                  AND toteuma_materiaali.poistettu IS NOT TRUE
GROUP BY materiaali_nimi, urakka_nimi, materiaalikoodi.yksikko;

-- name: hae-hallintayksikon-toteutuneet-materiaalit-raportille
-- Palauttaa hallintayksikköön kuuluvien urakoiden materiaalit ja määrät jokaisen omana rivinä.
-- Saman urakan samat materiaalit summataan yhteen.
SELECT
  SUM(maara)              AS kokonaismaara,
  urakka.nimi             AS urakka_nimi,
  materiaalikoodi.nimi    AS materiaali_nimi,
  materiaalikoodi.yksikko AS materiaali_yksikko
FROM toteuma_materiaali
  LEFT JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
  INNER JOIN toteuma ON toteuma.id = toteuma
                        AND alkanut :: DATE >= :alku
                        AND alkanut :: DATE <= :loppu
                        AND toteuma.poistettu IS NOT TRUE
                        AND toteuma_materiaali.poistettu IS NOT TRUE
  LEFT JOIN urakka ON urakka.id = toteuma.urakka
WHERE urakka.hallintayksikko = :hallintayksikko
GROUP BY materiaali_nimi, urakka_nimi, materiaalikoodi.yksikko, toteuma_materiaali.id;

-- name: hae-koko-maan-toteutuneet-materiaalit-raportille
-- Palauttaa kaikkien urakoiden materiaalit ja määrät jokaisen omana rivinä.
-- Saman urakan samat materiaalit summataan yhteen.
SELECT
  SUM(maara)              AS kokonaismaara,
  urakka.nimi             AS urakka_nimi,
  materiaalikoodi.nimi    AS materiaali_nimi,
  materiaalikoodi.yksikko AS materiaali_yksikko
FROM toteuma_materiaali
  LEFT JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
  INNER JOIN toteuma ON toteuma.id = toteuma
                        AND alkanut :: DATE >= :alku
                        AND alkanut :: DATE <= :loppu
                        AND toteuma.poistettu IS NOT TRUE
                        AND toteuma_materiaali.poistettu IS NOT TRUE
  LEFT JOIN urakka ON urakka.id = toteuma.urakka
GROUP BY materiaali_nimi, urakka_nimi, materiaalikoodi.yksikko;

-- name: hae-urakan-toteumat-materiaalille
-- Hakee kannasta kaikki urakassa olevat materiaalin toteumat. Ei vaadi, että toteuma/materiaali
-- löytyy materiaalin_kaytto taulusta.
SELECT
  t.id,
  m.id               AS materiaali_id,
  m.nimi             AS materiaali_nimi,
  m.yksikko          AS materiaali_yksikko,
  tm.maara           AS toteuma_maara,
  t.alkanut          AS toteuma_alkanut,
  t.paattynyt        AS toteuma_paattynyt,
  pa.id              AS pohjavesialue_id,
  pa.nimi            AS pohjavesialue_nimi,
  pa.tunnus          AS pohjavesialue_tunnus,
  tm.id              AS tmid,
  t.lisatieto        AS toteuma_lisatieto,
  t.suorittajan_nimi AS toteuma_suorittaja,
  t.sopimus
FROM toteuma t
  INNER JOIN toteuma_materiaali tm
    ON tm.toteuma = t.id
       AND tm.poistettu IS NOT TRUE
       AND t.poistettu IS NOT TRUE
       AND t.sopimus = :sopimus
       AND tm.materiaalikoodi = :materiaali
       AND t.alkanut :: DATE >= :alku
       AND t.alkanut :: DATE <= :loppu

  INNER JOIN materiaalikoodi m
    ON tm.materiaalikoodi = m.id

  LEFT JOIN materiaalin_kaytto mk
    ON m.id = mk.materiaali
       AND mk.sopimus = :sopimus
       AND mk.poistettu IS NOT TRUE

  LEFT JOIN pohjavesialue pa
    ON mk.pohjavesialue = pa.id;

-- name: hae-toteuman-materiaalitiedot
SELECT
  m.nimi                AS toteumamateriaali_materiaali_nimi,
  m.yksikko             AS toteumamateriaali_materiaali_yksikko,
  tm.maara              AS toteumamateriaali_maara,
  t.alkanut             AS toteuma_alkanut,
  t.paattynyt           AS toteuma_paattynyt,
  m.id                  AS toteumamateriaali_materiaali_id,
  t.id                  AS toteuma_id,
  tm.id                 AS toteumamateriaali_tmid,
  t.suorittajan_nimi    AS toteuma_suorittaja,
  t.suorittajan_ytunnus AS toteuma_ytunnus,
  t.lisatieto           AS toteuma_lisatieto,
  k.jarjestelma         AS toteuma_jarjestelmanlisaama,
  k.kayttajanimi        AS toteuma_kayttajanimi,
  o.nimi                AS toteuma_organisaatio,
  t.luoja               AS toteuma_luoja
FROM toteuma_materiaali tm
  LEFT JOIN toteuma t ON t.id = tm.toteuma
  LEFT JOIN materiaalikoodi m ON tm.materiaalikoodi = m.id
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
WHERE t.id = :toteuma_id AND
      t.urakka = :urakka_id AND
      t.poistettu IS NOT TRUE AND
      tm.poistettu IS NOT TRUE;

-- name: luo-materiaalinkaytto<!
-- Luo uuden materiaalin käytön
INSERT
INTO materiaalin_kaytto
(alkupvm, loppupvm, maara, materiaali, urakka, sopimus, pohjavesialue, luotu, luoja, poistettu)
VALUES (:alku, :loppu, :maara, :materiaali, :urakka, :sopimus, :pohjavesialue, NOW(), :kayttaja, FALSE);

-- name: paivita-materiaalinkaytto-maara!
-- Päivittää yhden materiaalin määrän id:n perusteella
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, maara = :maara
WHERE id = :id;

-- name: poista-materiaalinkaytto!
-- Poistaa urakan sopimuksen materiaalin päivämäärien, materiaalin ja pohjavesialueen mukaan
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE urakka = :urakka AND sopimus = :sopimus
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm
      AND materiaali = :materiaali
      AND pohjavesialue IS NULL;

-- name: poista-materiaalinkaytto-id!
-- Poistaa materiaalin käytön id:llä.
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id = :id;

-- name: poista-pohjavesialueen-materiaalinkaytto!
-- Poistaa materiaalin käytön pohjavesialueella
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE urakka = :urakka AND sopimus = :sopimus
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm
      AND materiaali = :materiaali
      AND pohjavesialue = :pohjavesialue;

-- name: poista-urakan-materiaalinkaytto!
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE urakka = :urakka AND sopimus = :sopimus
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm

-- name: luo-toteuma-materiaali<!
-- Luo uuden materiaalin toteumalle
INSERT
INTO toteuma_materiaali
(toteuma, materiaalikoodi, maara, luotu, luoja, poistettu)
VALUES (:toteuma, :materiaalikoodi, :maara, NOW(), :kayttaja, FALSE);

-- name: paivita-toteuma-materiaali!
-- Päivittää toteuma_materiaalin
UPDATE toteuma_materiaali
SET materiaalikoodi = :materiaalikoodi, maara = :maara, muokattu = NOW(), muokkaaja = :kayttaja
WHERE toteuma = :toteuma AND id = :id;

-- name: poista-toteuma-materiaali!
UPDATE toteuma_materiaali
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id IN (:id) AND poistettu IS NOT TRUE;

-- name: hae-materiaalikoodin-id-nimella
SELECT id
FROM materiaalikoodi
WHERE nimi = :nimi;


-- name: hae-suolatoteumat
-- Hakee annetun aikavälin suolatoteumat jaoteltuna päivän tarkkuudella
SELECT
  tmid,
  tid,
  materiaali_id,
  materiaali_nimi,
  alkanut,
  maara,
  lisatieto,
  koneellinen
FROM (WITH paivat AS (
    SELECT date_trunc('day', dd) AS pvm
    FROM generate_series(:alkupvm :: DATE, :loppupvm :: DATE, '1 day' :: INTERVAL) dd )
      SELECT
        tm.id   AS tmid,
        t.id    AS tid,
        mk.id   AS materiaali_id,
        mk.nimi AS materiaali_nimi,
        t.alkanut,
        tm.maara,
        t.lisatieto,
        FALSE   AS koneellinen
      FROM toteuma_materiaali tm
        JOIN toteuma t ON tm.toteuma = t.id
        JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
        JOIN kayttaja k ON tm.luoja = k.id
      WHERE t.urakka = :urakka
            AND k.jarjestelma IS NOT TRUE
            AND (t.alkanut BETWEEN :alkupvm AND :loppupvm)
            AND mk.materiaalityyppi = 'talvisuola' :: materiaalityyppi
      UNION
      SELECT
        NULL                                             AS tmid,
        NULL                                             AS tid,
        mk.id                                            AS materiaali_id,
        mk.nimi                                          AS materiaali_nimi,
        p.pvm,
        (SELECT SUM(tm.maara)
         FROM toteuma_materiaali tm
           JOIN toteuma t ON tm.toteuma = t.id
           JOIN kayttaja k ON tm.luoja = k.id
         WHERE k.jarjestelma = TRUE
               AND t.urakka = :urakka
               AND tm.materiaalikoodi = mk.id
               AND date_trunc('day', t.alkanut) = p.pvm) AS maara,
        ''                                               AS lisatieto,
        TRUE                                             AS koneellinen
      FROM materiaalikoodi mk
        CROSS JOIN paivat p
      WHERE mk.materiaalityyppi = 'talvisuola' :: materiaalityyppi) toteumat
WHERE maara IS NOT NULL;

-- name: hae-suolamateriaalit
SELECT *
FROM materiaalikoodi
WHERE materiaalityyppi = 'talvisuola' :: materiaalityyppi;

-- name: hae-kaikki-materiaalit
SELECT
  id,
  nimi,
  yksikko
FROM materiaalikoodi;

	 
	 
