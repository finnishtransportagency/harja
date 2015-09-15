-- name: hae-materiaalikoodit
-- Hakee kaikki järjestelmän materiaalikoodit
SELECT id, nimi, yksikko, urakkatyyppi, kohdistettava FROM materiaalikoodi;

-- name: hae-urakan-materiaalit
-- Hakee jokaisen materiaalin, joka liittyy urakkaan JA JOLLA ON RIVI MATERIAALIN_KAYTTO taulussa.
-- Oleellista on, että palauttaa yhden rivin per materiaali, ja laskee yhteen paljonko materiaalia
-- on käytetty.
SELECT mk.id, mk.alkupvm, mk.loppupvm, mk.maara, mk.sopimus, 
       m.id as materiaali_id, m.nimi as materiaali_nimi, m.yksikko as materiaali_yksikko,
       pa.id as pohjavesialue_id, pa.nimi as pohjavesialue_nimi, pa.tunnus as pohjavesialue_tunnus,
  (SELECT SUM(maara) as kokonaismaara from toteuma_materiaali
  WHERE materiaalikoodi = mk.id AND toteuma IN (SELECT id FROM toteuma WHERE urakka=:urakka))
  FROM materiaalin_kaytto mk
       LEFT JOIN materiaalikoodi m ON mk.materiaali = m.id
       LEFT JOIN pohjavesialue pa ON mk.pohjavesialue = pa.id
 WHERE mk.urakka = :urakka AND
       mk.poistettu = false;

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
WHERE (SELECT SUM(maara) AS maara
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
         ) IS NOT NULL;

-- name: hae-urakan-toteutuneet-materiaalit-raportille
-- Hakee urakassa käytetyt materiaalit, palauttaen yhden rivin jokaiselle materiaalille,
-- laskien samalla yhteen kuinka paljon materiaalia on käytetty. Palauttaa myös käytetyt
-- materiaalit, joille ei ole riviä materiaalin_kaytto taulussa (eli käytetty sopimuksen ulkopuolella)
SELECT mk.id, mk.alkupvm, mk.loppupvm, mk.maara, mk.sopimus,
          m.id as materiaali_id, m.nimi as materiaali_nimi, m.yksikko as materiaali_yksikko,
          pa.id as pohjavesialue_id, pa.nimi as pohjavesialue_nimi, pa.tunnus as pohjavesialue_tunnus,
  (SELECT SUM(maara) as kokonaismaara from toteuma_materiaali
  WHERE materiaalikoodi = mk.id AND toteuma IN (SELECT id FROM toteuma WHERE urakka=1))
FROM materiaalin_kaytto mk
  LEFT JOIN materiaalikoodi m ON mk.materiaali = m.id
  LEFT JOIN pohjavesialue pa ON mk.pohjavesialue = pa.id
WHERE mk.urakka = 1 AND
      mk.poistettu = false;



SELECT DISTINCT
           m.nimi    AS materiaali_nimi,
           m.yksikko AS materiaali_yksikko,
           m.id      AS materiaali_id,
  (
    SELECT SUM(maara) AS kokonaismaara
    FROM toteuma_materiaali
    WHERE materiaalikoodi = m.id AND
          toteuma IN
          (
            SELECT id
            FROM toteuma
            WHERE
              alkanut :: DATE >= '2000-09-30 00:00:00+02' AND
              alkanut :: DATE <= '2010-09-30 00:00:00+02' AND
              poistettu IS NOT TRUE) AND
          poistettu IS NOT TRUE
  )

FROM materiaalikoodi m
  LEFT JOIN materiaalin_kaytto mk
    ON m.id = mk.materiaali
       AND mk.poistettu IS NOT TRUE
       AND mk.alkupvm :: DATE BETWEEN '2000-09-30 00:00:00+02' AND '2010-09-30 00:00:00+02'
       AND mk.loppupvm :: DATE BETWEEN '2000-09-30 00:00:00+02' AND '2010-09-30 00:00:00+02'


  LEFT JOIN toteuma_materiaali tm
    ON tm.materiaalikoodi = m.id
       AND tm.poistettu IS NOT TRUE

  LEFT JOIN toteuma t
    ON t.id = tm.toteuma AND t.poistettu IS NOT TRUE
       AND t.alkanut :: DATE BETWEEN '2000-09-30 00:00:00+02' AND '2010-09-30 00:00:00+02'
WHERE (SELECT SUM(maara) AS maara
       FROM materiaalin_kaytto
       WHERE materiaali = m.id
             AND poistettu IS NOT TRUE
             AND alkupvm :: DATE BETWEEN '2000-09-30 00:00:00+02' AND '2010-09-30 00:00:00+02'
             AND loppupvm :: DATE BETWEEN '2000-09-30 00:00:00+02' AND '2010-09-30 00:00:00+02') IS NOT NULL
      OR (
           SELECT SUM(maara) AS kokonaismaara
           FROM toteuma_materiaali
           WHERE materiaalikoodi = m.id AND
                 toteuma IN
                 (
                   SELECT id
                   FROM toteuma
                   WHERE
                     alkanut :: DATE >= '2000-09-30 00:00:00+02' AND
                     alkanut :: DATE <= '2010-09-30 00:00:00+02' AND
                     poistettu IS NOT TRUE) AND
                 poistettu IS NOT TRUE
         ) IS NOT NULL;


-- name: hae-urakan-toteumat-materiaalille
-- Hakee kannasta kaikki urakassa olevat materiaalin toteumat. Ei vaadi, että toteuma/materiaali
-- löytyy materiaalin_kaytto taulusta.
SELECT t.id, m.id as materiaali_id, m.nimi as materiaali_nimi, m.yksikko as materiaali_yksikko,
      tm.maara as toteuma_maara, t.alkanut as toteuma_alkanut, t.paattynyt as toteuma_paattynyt,
      pa.id as pohjavesialue_id, pa.nimi as pohjavesialue_nimi, pa.tunnus as pohjavesialue_tunnus,
      tm.id as tmid, t.lisatieto as toteuma_lisatieto, t.suorittajan_nimi as toteuma_suorittaja, t.sopimus
FROM toteuma t
  INNER JOIN toteuma_materiaali tm
    ON tm.toteuma = t.id
    AND tm.poistettu IS NOT TRUE
    AND t.poistettu IS NOT TRUE
    AND t.sopimus = :sopimus
    AND tm.materiaalikoodi = :materiaali
    AND t.alkanut::DATE >= :alku
    AND t.alkanut::DATE <= :loppu

  INNER JOIN materiaalikoodi m
    ON tm.materiaalikoodi = m.id

  LEFT JOIN materiaalin_kaytto mk
    ON m.id = mk.materiaali
    AND mk.sopimus = :sopimus
    AND mk.poistettu IS NOT TRUE

  LEFT JOIN pohjavesialue pa
    ON mk.pohjavesialue = pa.id;

-- name: hae-toteuman-materiaalitiedot
SELECT m.nimi as toteumamateriaali_materiaali_nimi, m.yksikko as toteumamateriaali_materiaali_yksikko, tm.maara as toteumamateriaali_maara,
  t.alkanut as toteuma_alkanut, t.paattynyt as toteuma_paattynyt, m.id as toteumamateriaali_materiaali_id, t.id as toteuma_id, tm.id as toteumamateriaali_tmid,
  t.suorittajan_nimi as toteuma_suorittaja, t.suorittajan_ytunnus as toteuma_ytunnus, t.lisatieto as toteuma_lisatieto,
  k.jarjestelma as toteuma_jarjestelmanlisaama, k.kayttajanimi as toteuma_kayttajanimi, o.nimi as toteuma_organisaatio, t.luoja as toteuma_luoja
FROM toteuma_materiaali tm
  LEFT JOIN toteuma t ON t.id = tm.toteuma
  LEFT JOIN materiaalikoodi m ON tm.materiaalikoodi = m.id
  LEFT JOIN kayttaja k ON k.id = t.luoja
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
WHERE t.id = :toteuma_id AND
    t.urakka = :urakka_id AND
    t.poistettu IS NOT true AND
    tm.poistettu IS NOT true;

-- name: luo-materiaalinkaytto<!
-- Luo uuden materiaalin käytön
INSERT
INTO materiaalin_kaytto
(alkupvm, loppupvm, maara,  materiaali,  urakka,  sopimus, pohjavesialue,   luotu, luoja,     poistettu)
VALUES (:alku,   :loppu,   :maara, :materiaali, :urakka, :sopimus, :pohjavesialue, NOW(), :kayttaja, false);

-- name: paivita-materiaalinkaytto-maara!
-- Päivittää yhden materiaalin määrän id:n perusteella
UPDATE materiaalin_kaytto
   SET muokattu = NOW(), muokkaaja = :kayttaja, maara = :maara
 WHERE id = :id;

-- name: poista-materiaalinkaytto!
-- Poistaa urakan sopimuksen materiaalin päivämäärien, materiaalin ja pohjavesialueen mukaan
UPDATE materiaalin_kaytto
   SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = true
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkupvm = :alkupvm AND loppupvm = :loppupvm
   AND materiaali = :materiaali
   AND pohjavesialue IS NULL;

-- name: poista-materiaalinkaytto-id!
-- Poistaa materiaalin käytön id:llä.
UPDATE materiaalin_kaytto
   SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = true
 WHERE id = :id;
 
-- name: poista-pohjavesialueen-materiaalinkaytto!
-- Poistaa materiaalin käytön pohjavesialueella
UPDATE materiaalin_kaytto
   SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = true
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkupvm = :alkupvm AND loppupvm = :loppupvm
   AND materiaali = :materiaali
   AND pohjavesialue = :pohjavesialue;

-- name: luo-toteuma-materiaali<!
-- Luo uuden materiaalin toteumalle
INSERT
INTO toteuma_materiaali
(toteuma, materiaalikoodi, maara, luotu, luoja, poistettu)
VALUES (:toteuma, :materiaalikoodi, :maara, NOW(), :kayttaja, FALSE );

-- name: paivita-toteuma-materiaali!
-- Päivittää toteuma_materiaalin
UPDATE toteuma_materiaali
SET materiaalikoodi=:materiaalikoodi, maara=:maara, muokattu = NOW(), muokkaaja = :kayttaja
WHERE toteuma=:toteuma AND id=:id;

-- name: poista-toteuma-materiaali!
UPDATE toteuma_materiaali
SET muokattu=NOW(), muokkaaja=:kayttaja, poistettu=TRUE
WHERE id IN (:id) AND poistettu IS NOT true;

-- name: hae-materiaalikoodin-id-nimella
SELECT id FROM materiaalikoodi WHERE nimi = :nimi;