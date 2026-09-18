-- Linkitetään vuoden 2026 MHU-urakoilta puuttuvat lupausryhmät.
INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lr.id, u.id
FROM lupausryhma lr
         CROSS JOIN urakka u
WHERE lr."urakan-alkuvuosi" = 2026
  AND lr."rivin-tunnistin-selite" = 'Yleinen'
  AND u.tyyppi = 'teiden-hoito'
  AND EXTRACT(YEAR FROM u.alkupvm) = 2026
  AND NOT EXISTS (
    SELECT 1
    FROM lupausryhma_urakka lu
    WHERE lu.lupausryhma_id = lr.id
      AND lu.urakka_id = u.id
  );
