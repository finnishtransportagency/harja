-- name: hae-hoitokauden-tehtavamaarat-urakassa
SELECT *
FROM urakka_tehtavamaara ut
       JOIN urakka u on ut.urakka = u.id
       JOIN toimenpidekoodi tpk on ut.tehtava = tpk.id
WHERE ut.urakka = :urakka
  AND ut."hoitokauden-aloitusvuosi" = :hoitokausi
  AND ut.poistettu IS NOT TRUE;

-- name: paivita-tehtavamaara!
-- Päivittää urakan hoitokauden tehtävämäärät
UPDATE urakka_tehtavamaara
SET maara     = :maara,
    muokattu  = current_timestamp,
    muokkaaja = :kayttaja
WHERE urakka = :urakka
  AND "hoitokauden-aloitusvuosi" = :hoitovuosi
  AND tehtava = :tehtava;

-- name: lisaa-tehtavamaara<!
INSERT INTO urakka_tehtavamaara
  (urakka, "hoitokauden-aloitusvuosi", tehtava, maara, luotu, luoja)
VALUES (:urakka, :hoitokausi, :tehtava, :maara, :luotu, :kayttaja);
