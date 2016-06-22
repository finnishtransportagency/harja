-- name: hae-urakan-siltatarkastukset
-- Hakee urakan kaikki sillat ja sekä annettuna vuonna tehdyn uusimman siltatarkastuksen
SELECT
  siltanro,
  siltanimi,
  (SELECT tarkastusaika
   FROM siltatarkastus st
   WHERE st.silta = s.id
         AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1),
  (SELECT tarkastaja
   FROM siltatarkastus st
   WHERE st.silta = s.id
         AND EXTRACT(YEAR FROM tarkastusaika) = :vuosi
         AND st.poistettu = FALSE
   ORDER BY tarkastusaika DESC
   LIMIT 1)
FROM silta s
WHERE s.id IN (SELECT silta
               FROM sillat_alueurakoittain
               WHERE urakka = :urakka)
ORDER BY siltanro;
