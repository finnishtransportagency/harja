-- name: hae-urakan-tarkastukset
-- Hakee urakan tarkastukset aikavälin perusteella
SELECT id, sopimus, aika,
       tr_numero, tr_alkuosa, tr_loppuosa, tr_loppuetaisyys,
       sijainti,
       tarkastaja, mittaaja,
       tyyppi -- tähän myös havainnon kuvaus
  FROM tarkastus
 WHERE urakka = :urakka
   AND (aika >= :alku AND aika <= :loppu)
       
