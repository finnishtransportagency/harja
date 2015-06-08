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
SELECT DISTINCT
  m.nimi as materiaali_nimi, mk.maara, m.yksikko as materiaali_yksikko, m.id as materiaali_id,
  (SELECT SUM(maara) as kokonaismaara from toteuma_materiaali
  WHERE materiaalikoodi = m.id AND toteuma IN (SELECT id FROM toteuma WHERE
    urakka=:urakka AND
    alkanut::DATE <= :alku AND
    paattynyt::DATE >= :loppu))
FROM materiaalikoodi m
  LEFT JOIN materiaalin_kaytto mk ON m.id = mk.materiaali
  INNER JOIN toteuma_materiaali tm ON tm.materiaalikoodi = m.id
  LEFT JOIN toteuma t ON t.id = tm.toteuma
WHERE mk.poistettu IS NOT true AND
      t.poistettu IS NOT true AND
      tm.poistettu IS NOT true AND
      t.urakka = :urakka AND
      t.alkanut::DATE <= :alku AND
      t.paattynyt::DATE >= :loppu;

-- name: hae-urakan-toteumat-materiaalille
-- Hakee kannasta kaikki urakassa olevat materiaalin toteumat. Ei vaadi, että toteuma/materiaali
-- löytyy materiaalin_kaytto taulusta.
SELECT t.id, m.id as materiaali_id, m.nimi as materiaali_nimi, m.yksikko as materiaali_yksikko,
      tm.maara as toteuma_maara, t.alkanut as toteuma_alkanut, t.paattynyt as toteuma_paattynyt,
      pa.id as pohjavesialue_id, pa.nimi as pohjavesialue_nimi, pa.tunnus as pohjavesialue_tunnus,
      tm.id as tmid, t.lisatieto as toteuma_lisatieto, t.suorittajan_nimi as toteuma_suorittaja
FROM toteuma t
  LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
  LEFT JOIN materiaalikoodi m ON tm.materiaalikoodi = m.id
  LEFT JOIN materiaalin_kaytto mk ON m.id = mk.materiaali
  LEFT JOIN pohjavesialue pa ON mk.pohjavesialue = pa.id
WHERE t.urakka = :urakka AND
      m.id = :materiaali AND
      mk.poistettu IS NOT true AND
      t.poistettu IS NOT true AND
      tm.poistettu IS NOT true;

-- name: hae-toteuman-materiaalitiedot
SELECT m.nimi as toteumamateriaali_materiaali_nimi, m.yksikko as toteumamateriaali_materiaali_yksikko, tm.maara as toteumamateriaali_maara,
  t.alkanut as toteuma_alkanut, t.paattynyt as toteuma_paattynyt, m.id as toteumamateriaali_materiaali_id, t.id as toteuma_id, tm.id as toteumamateriaali_tmid,
  t.suorittajan_nimi as toteuma_suorittaja, t.suorittajan_ytunnus as toteuma_ytunnus, t.lisatieto as toteuma_lisatieto
FROM toteuma_materiaali tm
  LEFT JOIN toteuma t ON t.id = tm.toteuma
  LEFT JOIN materiaalikoodi m ON tm.materiaalikoodi = m.id
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