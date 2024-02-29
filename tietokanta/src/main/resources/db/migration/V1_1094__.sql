-- Siirtymä vanhasta urakan alkuvuosi perustaisesta lupausryhmien määräytymisestä lupausryhma_urakka taulun käyttöön

INSERT INTO lupausryhma_urakka (lupausryhma_id, urakka_id)
SELECT lupausryhma.id AS "lupausryhma_id", urakka.id  AS "urakka_id"
FROM urakka
    JOIN lupausryhma ON lupausryhma."urakan-alkuvuosi" = EXTRACT(YEAR FROM urakka.alkupvm);