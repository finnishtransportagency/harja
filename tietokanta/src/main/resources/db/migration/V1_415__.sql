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
 suolankayton_sakkoraja NUMERIC, -- suolankäytön sakkoraja ennen kohtuullistamista
 kohtuullisuustarkistettu_sakkoraja NUMERIC, -- yo raja kohtuullisuustarkistuksen jälkeen
 sakkoraja NUMERIC,              -- suolankäytön sakkoraja
 maara NUMERIC,                  -- suolasakon määrä per ylitystonni
 suolasakko NUMERIC              -- sakon loppusumma
);

-- Muodostetaan sopimuksen_kaytetty_materiaali uudestaan (suolassa oli bugi)
-- Tästä on myös sproc tulevaisuuden varalle, mutta repeatablet ajetaan numeroitujen
-- jälkeen, joten sitä ei voi vielä käyttää.
DELETE FROM sopimuksen_kaytetty_materiaali;

INSERT INTO sopimuksen_kaytetty_materiaali (sopimus, alkupvm, materiaalikoodi, maara)
    SELECT t.sopimus, t.alkanut::date as alkupvm, tm.materiaalikoodi, SUM(tm.maara)
      FROM toteuma_materiaali tm join toteuma t ON tm.toteuma=t.id
  GROUP BY t.sopimus, t.alkanut::date, tm.materiaalikoodi;
