-- Korjaa virhe elinvoimakeskustiedon päivittämisessä
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Lounais-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Varsinais-Suomi')
  AND loppupvm > '2026-01-01';
