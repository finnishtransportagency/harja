-- name: hae-urakan-tarkastukset
-- Hakee urakan tarkastukset aikavälin perusteella
SELECT id, sopimus, aika,
       tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
       sijainti,
       tarkastaja, mittaaja,
       tyyppi -- tähän myös havainnon kuvaus
  FROM tarkastus
 WHERE urakka = :urakka
   AND (aika >= :alku AND aika <= :loppu)
       
-- name: luo-tarkastus<!
-- Luo uuden tarkastuksen
INSERT
  INTO tarkastus
       (urakka, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
        sijainti, tarkastaja, mittaaja, tyyppi, havainto, luoja)
VALUES (:urakka, :aika, :tr_numero, :tr_alkuosa, :tr_alkuetaisyys, :tr_loppuosa, :tr_loppuetaisyys,
        :sijainti::point, :tarkastaja, :mittaaja, :tyyppi::tarkastustyyppi, :havainto, :luoja)
	
