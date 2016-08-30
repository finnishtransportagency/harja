-- Lisää myös vanha vertailukausi
ALTER TABLE lampotilat ADD pitka_keskilampotila_vanha NUMERIC (4,2);

-- Tehdään hoitokauden suolasakolle uusi tyyppi

CREATE TYPE hk_suolasakko AS (
 urakka INTEGER,                 -- urakka
 keskilampotila NUMERIC,         -- hoitokauden talven keskilämpötila
 pitkakeskilampotila NUMERIC,    -- vertailukauden pitkä keskilämpötila
 lampotilapoikkeama NUMERIC,     -- keskilämmön ja vertailujakson erotus
 suolankaytto NUMERIC,           -- hk toteutunut suolan käyttö
 sallittu_suolankaytto NUMERIC,  -- hoitokauden suolankäyttöraja
 kohtuullisuustarkistettu_sakkoraja NUMERIC, -- yo raja kohtuullisuustarkistuksen jälkeen
 sakkoraja NUMERIC,              -- suolankäytön sakkoraja
 maara NUMERIC,                  -- suolasakon määrä per ylitystonni
 suolasakko NUMERIC              -- sakon loppusumma
);
