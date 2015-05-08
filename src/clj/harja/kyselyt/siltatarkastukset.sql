-- name: hae-urakan-sillat
-- Hakee hoidon alueurakalle sillat sekä niiden viimeiset tarkastuspvm:t.
SELECT s.id, s.siltanimi, s.siltanro, st.uusin_aika, st.tarkastaja
  FROM silta s
       LEFT JOIN (SELECT silta as uusin_silta, tarkastaja, MAX(tarkastusaika) as uusin_aika
                  FROM siltatarkastus GROUP BY silta,tarkastaja) AS st ON st.uusin_silta = s.id
 WHERE s.id IN (SELECT silta FROM sillat_alueurakoittain WHERE urakka = :urakka)


-- name: hae-sillan-tarkastukset
-- Hakee sillan sillantarkastukset
SELECT st.id, st.silta, st.urakka,
       st.tarkastusaika, st.tarkastaja,
       st.luotu, st.luoja, st.muokattu, st.muokkaaja, st.poistettu
  FROM siltatarkastus st
 WHERE silta = :silta

-- name: hae-siltatarkastuksen-kohteet
-- Hakee yhden siltatarkastuksen kohteet
SELECT kohde, tulos, lisatieto
  FROM siltatarkastuskohde
 WHERE siltatarkastus = :siltatarkastus

