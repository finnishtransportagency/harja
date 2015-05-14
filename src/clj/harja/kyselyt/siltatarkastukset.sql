-- name: hae-urakan-sillat
-- Hakee hoidon alueurakalle sillat sekä niiden viimeiset tarkastuspvm:t.
SELECT s.id, s.siltanimi, s.siltanro, s1.tarkastusaika, s1.tarkastaja
  FROM silta s
       LEFT JOIN siltatarkastus s1 ON s1.silta = s.id
       LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id AND s2.tarkastusaika > s1.tarkastusaika)
  WHERE s.id IN (SELECT silta FROM sillat_alueurakoittain WHERE urakka = :urakka)
    AND s2.id IS NULL;


-- name: hae-sillan-tarkastukset
-- Hakee sillan sillantarkastukset
SELECT id, silta, urakka,
       tarkastusaika, tarkastaja,
       luotu, luoja, muokattu, muokkaaja, poistettu
  FROM siltatarkastus
 WHERE silta = :silta ORDER BY tarkastusaika DESC

-- name: hae-siltatarkastusten-kohteet
-- Hakee annettujen siltatarkastusten kohteet ID:iden perusteella
SELECT siltatarkastus, kohde, tulos, lisatieto
  FROM siltatarkastuskohde
 WHERE siltatarkastus IN (:siltatarkastus_idt)


-- name: paivita-siltatarkastuksen-kohteet!
-- Päivittää olemassaolevan siltatarkastuksen kohteet
UPDATE siltatarkastuskohde
   SET tulos = :tulos, lisatieto = :lisatieto
 WHERE siltatarkastus = :siltatarkastus AND kohde = :kohde