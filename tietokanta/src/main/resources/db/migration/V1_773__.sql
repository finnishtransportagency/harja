----------------------------------------------------------------------------------------------------------
-- Toimenpidekoodi-taulun tehtävät (taso = 4) ryhmiteltynä käytettävyyden ja raportoinnin avustamiseksi --
----------------------------------------------------------------------------------------------------------

-- Käytetään nimiä tasonumeroinnin sijaan, jotta voidaan tarvittaessa lisätä väleihin uusia tasoa (yläalataso, alavälitaso jne.)
CREATE TYPE tehtavaryhmatyyppi AS ENUM (
  'ylataso',
  'valitaso',
  'alataso');

CREATE TABLE tehtavaryhma (
   id serial primary key,    -- sisäinen id
   nimi varchar (128),       -- tehtäväryhmän nimi (esim. "Laaja toimenpide")
   emo INTEGER REFERENCES toimenpidekoodi (id),  -- ylemmän tason tehtäväryhmä (NULL jos taso on tehtäväryhmissä ylimpänä)
   tyyppi TEHTAVARYHMATYYPPI not null,
   poistettu boolean DEFAULT false,
   luotu timestamp,
   luoja integer REFERENCES kayttaja (id),
   muokattu timestamp,
   muokkaaja integer REFERENCES kayttaja (id)
);


