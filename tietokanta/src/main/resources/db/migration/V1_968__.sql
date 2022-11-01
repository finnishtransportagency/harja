-- Erotetaan muu-tyyppinen erilliskustannus muu bonus-tyyppisestä rivistä,
-- jotta bonukset voidaan näyttää sanktiot ja bonukset-välilehden taulukossa
UPDATE
    erilliskustannus
SET tyyppi = 'muu-bonus'
FROM erilliskustannus ek
         JOIN urakka u ON ek.urakka = u.id
WHERE ek.tyyppi = 'muu'
  AND u.loppupvm > current_date
  AND (u.tyyppi = 'hoito' AND
       ek.lisatieto ILIKE '%bonus%');