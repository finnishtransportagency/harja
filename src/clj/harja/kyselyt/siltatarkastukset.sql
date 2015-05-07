-- name: hae-urakan-sillat
-- Hakee hoidon alueurakalle sillat sekä niiden viimeiset tarkastuspvm:t.
SELECT s.id, s.siltanimi, st.uusin_aika, st.tarkastaja
  FROM silta s
  JOIN (SELECT silta as uusin_silta, tarkastaja, MAX(tarkastusaika) as uusin_aika FROM siltatarkastus GROUP BY silta,tarkastaja) AS st ON st.uusin_silta = s.id 
 WHERE s.id IN (SELECT silta FROM sillat_alueurakoittain WHERE urakka = :urakka)
