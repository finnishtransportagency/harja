--  -----------------------------------------
--  Tehtävä- ja määrämuutokset 
--  -----------------------------------------


-- Onko tehtävä-määrämuutos yksikköhinta laskettu, asetettu, vai onko tavoitehinta syötetty manuaalisesti 
CREATE TYPE muutos_yksikkohinta_lahde_enum AS ENUM ('laskettu','aseta','manuaali');


CREATE TABLE mhu_muutos_tehtavamaaramuutokset (

  -- Näillä joinataan data näkymään 
  urakka    INTEGER NOT NULL REFERENCES urakka(id),
  tehtava   INTEGER NOT NULL REFERENCES tehtava(id),
  hoitokauden_alkuvuosi INTEGER NOT NULL,
  -----------------------------------------

  -- "Aseta yksikköhinta" valinta annetaan jos sitä ei voida laskea 
  -- Defaulttina oletetaan että on laskettu 
  lahde muutos_yksikkohinta_lahde_enum NOT NULL DEFAULT 'laskettu',
  
  -- Kun yksikköhinta on asetettu modalista, tallennetaan käytetty hk  
  valitun_yksikkohinnan_hk_alkuvuosi INTEGER,
  
  -- Kun edellisten hk yksikköhintaa ei ole saatavilla, syötetään tav.hinnan muutos manuaalisesti 
  kasin_syotetty_tavoitehintamuutos NUMERIC(12,2),
  
  -- Muutoksen syy 
  syy TEXT,
  
  -----------------------------------------
  luotu    TIMESTAMP NOT NULL DEFAULT NOW(),
  luoja    INTEGER REFERENCES kayttaja(id),
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja(id),

  -- Yksi rivi per / (urakka,tehtava,hoitokauden_alkuvuosi)
  CONSTRAINT mmt_pk PRIMARY KEY (urakka, tehtava, hoitokauden_alkuvuosi),
  
  -- Tehdään myös tällainen constraintti 
  -- Evaluoidaan kun insertataan tai päivitetään 
  CHECK (
    -- Kun lähde on laskettu, yksikköhinta voidaan laskea suoraa
    (lahde = 'laskettu' 
    AND valitun_yksikkohinnan_hk_alkuvuosi IS NULL 
    AND kasin_syotetty_tavoitehintamuutos IS NULL)
    OR
    -- Kun lähde on manuaali, käsin syötetty tavoitehinta hinta on olemassa
    (lahde = 'manuaali' 
    AND kasin_syotetty_tavoitehintamuutos IS NOT NULL 
    AND valitun_yksikkohinnan_hk_alkuvuosi IS NULL)
    OR 
    -- Kun lähde on aseta, valittu yksikköhinta hinta on olemassa
    (lahde = 'aseta' 
    AND kasin_syotetty_tavoitehintamuutos IS NULL 
    AND valitun_yksikkohinnan_hk_alkuvuosi IS NOT NULL)
  )
);


CREATE INDEX ON mhu_muutos_tehtavamaaramuutokset (tehtava);
