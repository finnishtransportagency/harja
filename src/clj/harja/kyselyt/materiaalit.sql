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
-- Hakee kaikki materiaalit, ja palauttaa materiaalin suunnittelutiedot, jos materiaalia on urakkaan suunniteltu.
SELECT
  mk.id,
  mk.alkupvm,
  mk.loppupvm,
  mk.maara,
  mk.sopimus,
  m.id      AS materiaali_id,
  m.nimi    AS materiaali_nimi,
  m.yksikko AS materiaali_yksikko
FROM materiaalin_kaytto mk
  LEFT JOIN materiaalikoodi m ON mk.materiaali = m.id
WHERE mk.urakka = :urakka AND
      mk.poistettu = FALSE AND
      m.materiaalityyppi != 'talvisuola' :: materiaalityyppi;

-- name: hae-urakassa-kaytetyt-materiaalit
-- Hakee urakassa käytetyt materiaalit, palauttaen yhden rivin jokaiselle materiaalille,
-- laskien samalla yhteen kuinka paljon materiaalia on käytetty. Palauttaa myös käytetyt
-- materiaalit, joille ei ole riviä materiaalin_kaytto taulussa (eli käytetty sopimuksen ulkopuolella)
-- määrä = suunniteltu määrä. kokonaismäärä = toteutunut määrä
SELECT mat.* FROM
  (SELECT m.nimi    AS materiaali_nimi,
          m.yksikko AS materiaali_yksikko,
          m.id      AS materiaali_id,
      (SELECT  SUM(maara)
         FROM materiaalin_kaytto
        WHERE materiaali = m.id
              AND poistettu IS NOT TRUE
              AND alkupvm :: DATE BETWEEN :alku AND :loppu
              AND loppupvm :: DATE BETWEEN :alku AND :loppu
              AND sopimus = :sopimus) AS maara,
      (SELECT SUM(maara)
         FROM sopimuksen_kaytetty_materiaali
        WHERE materiaalikoodi = m.id AND
              (alkupvm BETWEEN :alku AND :loppu) AND
 	      sopimus = :sopimus) AS kokonaismaara
   FROM materiaalikoodi m
  WHERE m.materiaalityyppi != 'talvisuola' :: materiaalityyppi) as mat
WHERE mat.maara != 0 OR mat.kokonaismaara != 0;

-- name: paivita-sopimuksen-materiaalin-kaytto
SELECT paivita_sopimuksen_materiaalin_kaytto(:sopimus::integer, :alkupvm::date);

-- name: paivita-koko-sopimuksen-materiaalin-kaytto
SELECT paivita_koko_sopimuksen_materiaalin_kaytto(:sopimus::integer);

-- name: paivita-sopimuksen-materiaalin-kaytto-toteumapvm
-- Päivittää sopimuksen materiaalin käytön annetun toteuman alkupäivämäärän
-- päivälle.
SELECT paivita_sopimuksen_materiaalin_kaytto(
       :sopimus::integer,
       (SELECT alkanut FROM toteuma WHERE id = :toteuma)::date);

-- name: hae-urakan-suunnitellut-materiaalit-raportille
SELECT DISTINCT
  urakka.nimi             AS "urakka-nimi",
  materiaalikoodi.nimi    AS "materiaali-nimi",
  materiaalikoodi.yksikko AS "materiaali-yksikko"
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
  urakka.nimi             AS "urakka-nimi",
  materiaalikoodi.nimi    AS "materiaali-nimi",
  materiaalikoodi.yksikko AS "materiaali-yksikko"
FROM toteuma_materiaali
  LEFT JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
  JOIN urakka ON urakka.id = (SELECT urakka
                                   FROM toteuma
                                   WHERE id = toteuma_materiaali.toteuma)
  JOIN toteuma ON toteuma.id = toteuma
                  AND urakka.id = :urakka
                  AND alkanut :: DATE >= :alku
                  AND alkanut :: DATE <= :loppu
                  AND toteuma.poistettu IS NOT TRUE
                  AND toteuma_materiaali.poistettu IS NOT TRUE
GROUP BY "materiaali-nimi", "urakka-nimi", materiaalikoodi.yksikko;

-- name: hae-hallintayksikon-toteutuneet-materiaalit-raportille
-- Palauttaa hallintayksikköön kuuluvien urakoiden materiaalit ja määrät jokaisen omana rivinä.
-- Saman urakan samat materiaalit summataan yhteen.
SELECT
  SUM(maara)              AS kokonaismaara,
  urakka.nimi             AS "urakka-nimi",
  materiaalikoodi.nimi    AS "materiaali-nimi",
  materiaalikoodi.yksikko AS "materiaali-yksikko"
FROM toteuma_materiaali
  LEFT JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
  INNER JOIN toteuma ON toteuma.id = toteuma
                        AND alkanut :: DATE >= :alku
                        AND alkanut :: DATE <= :loppu
                        AND toteuma.poistettu IS NOT TRUE
                        AND toteuma_materiaali.poistettu IS NOT TRUE
  JOIN urakka ON (urakka.id = toteuma.urakka AND urakka.urakkanro IS NOT NULL)
WHERE urakka.hallintayksikko = :hallintayksikko AND (:urakkatyyppi::urakkatyyppi IS NULL OR urakka.tyyppi = :urakkatyyppi :: urakkatyyppi)
GROUP BY "materiaali-nimi", "urakka-nimi", materiaalikoodi.yksikko, toteuma_materiaali.id;

-- name: hae-koko-maan-toteutuneet-materiaalit-raportille
-- Palauttaa kaikkien urakoiden materiaalit ja määrät jokaisen omana rivinä.
-- Saman urakan samat materiaalit summataan yhteen.
SELECT
  SUM(maara)              AS kokonaismaara,
  urakka.nimi             AS "urakka-nimi",
  o.nimi                  AS "hallintayksikko-nimi",
  materiaalikoodi.nimi    AS "materiaali-nimi",
  materiaalikoodi.yksikko AS "materiaali-yksikko"
FROM toteuma_materiaali
  LEFT JOIN materiaalikoodi ON materiaalikoodi.id = toteuma_materiaali.materiaalikoodi
  INNER JOIN toteuma ON toteuma.id = toteuma
                        AND alkanut :: DATE >= :alku
                        AND alkanut :: DATE <= :loppu
                        AND toteuma.poistettu IS NOT TRUE
                        AND toteuma_materiaali.poistettu IS NOT TRUE
  JOIN urakka ON (urakka.id = toteuma.urakka AND urakka.urakkanro IS NOT NULL)
  JOIN organisaatio o ON urakka.hallintayksikko = o.id
  WHERE (:urakkatyyppi::urakkatyyppi IS NULL OR urakka.tyyppi = :urakkatyyppi :: urakkatyyppi)
GROUP BY "materiaali-nimi", "urakka-nimi", o.nimi, materiaalikoodi.yksikko;

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
  tm.id              AS tmid,
  t.lisatieto        AS toteuma_lisatieto,
  t.suorittajan_nimi AS toteuma_suorittaja,
  k.jarjestelma         AS toteuma_jarjestelmanlisaama,
  t.sopimus
FROM toteuma_materiaali tm
  INNER JOIN toteuma t
    ON tm.toteuma = t.id
       AND tm.poistettu IS NOT TRUE
       AND t.poistettu IS NOT TRUE
       AND t.sopimus = :sopimus
       AND tm.materiaalikoodi = :materiaali
       AND t.alkanut :: DATE >= :alku
       AND t.alkanut :: DATE <= :loppu

  INNER JOIN materiaalikoodi m
    ON tm.materiaalikoodi = m.id

  LEFT JOIN kayttaja k ON k.id = t.luoja

ORDER BY t.alkanut DESC;

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
(alkupvm, loppupvm, maara, materiaali, urakka, sopimus, luotu, luoja, poistettu)
VALUES (:alku, :loppu, :maara, :materiaali, :urakka, :sopimus, NOW(), :kayttaja, FALSE);

-- name: paivita-materiaalinkaytto-maara!
-- Päivittää yhden materiaalin määrän id:n perusteella
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, maara = :maara
WHERE id = :id;

-- name: poista-materiaalinkaytto!
-- Poistaa urakan sopimuksen materiaalin päivämäärien ja materiaalin mukaan
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE urakka = :urakka AND sopimus = :sopimus
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm
      AND materiaali = :materiaali;

-- name: poista-materiaalinkaytto-id!
-- Poistaa materiaalin käytön id:llä.
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE id = :id;

-- name: poista-urakan-materiaalinkaytto!
UPDATE materiaalin_kaytto
SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = TRUE
WHERE urakka = :urakka AND sopimus = :sopimus
      AND alkupvm = :alkupvm AND loppupvm = :loppupvm;

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
FROM (SELECT
        tm.id   AS tmid,
        t.id    AS tid,
        mk.id   AS materiaali_id,
        mk.nimi AS materiaali_nimi,
        t.alkanut,
        tm.maara,
        t.lisatieto,
        FALSE   AS koneellinen
      FROM toteuma_materiaali tm
        JOIN toteuma t ON (tm.toteuma = t.id AND t.poistettu IS NOT TRUE)
        JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
        LEFT JOIN kayttaja k ON tm.luoja = k.id
      WHERE t.urakka = :urakka
            AND t.sopimus = :sopimus
            AND tm.poistettu IS NOT TRUE
            AND k.jarjestelma IS NOT TRUE
            AND (t.alkanut BETWEEN :alkupvm AND :loppupvm)
            AND mk.materiaalityyppi = 'talvisuola' :: materiaalityyppi
      UNION
      SELECT
        NULL                         AS tmid,
        NULL                         AS tid,
        mk.id                        AS materiaali_id,
        mk.nimi                      AS materiaali_nimi,
        date_trunc('day', t.alkanut) AS alkanut,
        SUM(tm.maara)                AS maara,
        string_agg(t.lisatieto, ', ') AS lisatieto,
        TRUE                         AS koneellinen
      FROM toteuma_materiaali tm
        JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
        JOIN toteuma t ON tm.toteuma = t.id
        JOIN kayttaja k ON tm.luoja = k.id
      WHERE k.jarjestelma = TRUE
            AND t.urakka = :urakka
            AND t.poistettu IS NOT TRUE
            AND tm.poistettu IS NOT TRUE
            AND t.sopimus = :sopimus
            AND (t.alkanut BETWEEN :alkupvm AND :loppupvm)
            AND mk.materiaalityyppi = 'talvisuola' :: materiaalityyppi
      GROUP BY mk.id, mk.nimi, date_trunc('day', t.alkanut)) toteumat
WHERE maara IS NOT NULL
ORDER BY alkanut DESC
LIMIT 501;

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
