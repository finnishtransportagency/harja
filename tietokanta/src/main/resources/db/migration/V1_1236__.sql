-- Korjaa virhe elinvoimakeskustiedon päivittämisessä
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Lounais-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Varsinais-Suomi')
  AND loppupvm > '2026-01-01';

-- Etelä-Pohjanmaan tiemerkintä-, maanteiden korjaus- ja valaistusurakat
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Etelä-Pohjanmaan elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Etelä-Pohjanmaa')
  AND nimi IN ('Maantiepäällysteiden korjausurakka DHJ EPO ELY 2025 (optio 2026-2027) osa A, P',
               'EPO TMPS 2021-2024(optio 2025-2026; ei oteta), P')
  AND loppupvm > '2026-01-01';

-- Uudenmaan elinvoimakeskuksen alueelle jäävät UUD-elyn e18-tien, tiemerkintä-, maanteiden korjaus- ja valaistusurakat
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Uudenmaan elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa')
  AND nimi IN ('UUD Tiemerkintöjen palvelusopimus UUD Länsi TMPS 2025-2026, P',
               'UUD Tiemerkintöjen palvelusopimus UUD TMPS Itä 2021-2024, P',
               'UUD Tievalaistuksen palvelusopimus 2025-2028, Itä-Uusimaa, P',
               'UUD Tievalaistus ja sulkupuomit palvelusopimus 2021-2026, Länsi-Uusimaa, P',
               'UUD Maantienpäällysteiden korjausurakoiden DHJ KAS ELY 2025 Ramppi, P',
               'E18 (Vt7) Koskenkylä-Kotka, kunnossapito, P')
  AND loppupvm > '2026-01-01';

-- UUD-elystä pois siirtyvät tiemerkintä- ja valaistusurakat
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Kaakkois-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa')
  AND nimi IN ('UUD Tievalaistuksen palvelusopimus, Häme 2024-2027, P')
  AND loppupvm > '2026-01-01';
UPDATE urakka
SET elinvoimakeskus_id = (select id from organisaatio where nimi = 'Sisä-Suomen elinvoimakeskus')
WHERE hallintayksikko = (select id from organisaatio where nimi = 'Uusimaa')
  AND nimi IN ('UUD Tiemerkintäurakka UUD Kanta-Häme TMU 2025, P')
  AND loppupvm > '2026-01-01';

