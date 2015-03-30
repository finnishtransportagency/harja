-- name: hae-materiaalikoodit
-- Hakee kaikki järjestlemän materiaalikoodit
SELECT id, nimi, yksikko FROM materiaalikoodi

-- name: hae-urakan-materiaalit
-- Hakee urakan kaikki materiaalit
SELECT mk.id, mk.alkupvm, mk.loppupvm, mk.maara,
       m.id as materiaali_id, m.nimi as materiaali_nimi, m.yksikko as materiaali_yksikko,
       pa.id as pohjavesialue_id, pa.nimi as pohjavesialue_nimi, pa.tunnus as pohjavesialue_tunnus
  FROM materiaalin_kaytto mk
       LEFT JOIN materiaalikoodi m ON mk.materiaali = m.id
       LEFT JOIN pohjavesialue pa ON mk.pohjavesialue = pa.id
 WHERE mk.urakka = :urakka AND
       poistettu = false
 
