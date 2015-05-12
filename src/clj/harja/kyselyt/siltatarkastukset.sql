-- name: hae-urakan-sillat
-- Hakee hoidon alueurakalle sillat sekä niiden viimeiset tarkastuspvm:t.
SELECT s.id, s.siltanimi, s.siltanro, st.uusin_aika, st.tarkastaja
  FROM silta s
       LEFT JOIN (SELECT silta as uusin_silta, tarkastaja, MAX(tarkastusaika) as uusin_aika
                  FROM siltatarkastus GROUP BY silta,tarkastaja) AS st ON st.uusin_silta = s.id
 WHERE s.id IN (SELECT silta FROM sillat_alueurakoittain WHERE urakka = :urakka)


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

