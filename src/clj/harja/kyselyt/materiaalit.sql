-- name: hae-materiaalikoodit
-- Hakee kaikki järjestelmän materiaalikoodit
SELECT id, nimi, yksikko, urakkatyyppi, kohdistettava FROM materiaalikoodi

-- name: hae-urakan-materiaalit
-- Hakee urakan kaikki materiaalit
SELECT mk.id, mk.alkupvm, mk.loppupvm, mk.maara, mk.sopimus, 
       m.id as materiaali_id, m.nimi as materiaali_nimi, m.yksikko as materiaali_yksikko,
       pa.id as pohjavesialue_id, pa.nimi as pohjavesialue_nimi, pa.tunnus as pohjavesialue_tunnus
  FROM materiaalin_kaytto mk
       LEFT JOIN materiaalikoodi m ON mk.materiaali = m.id
       LEFT JOIN pohjavesialue pa ON mk.pohjavesialue = pa.id
 WHERE mk.urakka = :urakka AND
       poistettu = false

-- name: paivita-materiaalin-maara!
-- Päivittää yhden materiaalin määrän id:n perusteella
UPDATE materiaalin_kaytto
   SET muokattu = NOW(), muokkaaja = :kayttaja, maara = :maara
 WHERE id = :id

-- name: poista-materiaali!
-- Poistaa urakan sopimuksen materiaalin päivämäärien, materiaalin ja pohjavesialueen mukaan
UPDATE materiaalin_kaytto
   SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = true
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkupvm = :alkupvm AND loppupvm = :loppupvm
   AND materiaali = :materiaali
   AND pohjavesialue IS NULL

-- name: poista-pohjavesialueen-materiaali!
-- Poistaa materiaalin käytön pohjavesialueella
UPDATE materiaalin_kaytto
   SET muokattu = NOW(), muokkaaja = :kayttaja, poistettu = true
 WHERE urakka = :urakka AND sopimus = :sopimus
   AND alkupvm = :alkupvm AND loppupvm = :loppupvm
   AND materiaali = :materiaali
   AND pohjavesialue = :pohjavesialue
   
 
-- name: luo-materiaali<!
-- Luo uuden materiaalin käytön
INSERT
  INTO materiaalin_kaytto
       (alkupvm, loppupvm, maara,  materiaali,  urakka,  sopimus,  pohjavesialue,  luotu, luoja,     poistettu)
VALUES (:alku,   :loppu,   :maara, :materiaali, :urakka, :sopimus, :pohjavesialue, NOW(), :kayttaja, false)
