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
   otsikko  text, -- korkean tason otsikko, joka kuvaa tehtäväryhmää (esim. Talvihoito)
   nimi text not null,       -- tehtäväryhmän nimi (esim. "Viheralueiden hoito")
   emo INTEGER REFERENCES toimenpidekoodi (id),  -- ylemmän tason tehtäväryhmä (NULL jos taso on tehtäväryhmissä ylimpänä)
   tyyppi TEHTAVARYHMATYYPPI not null,
   jarjestys integer, -- ryhmän järjestys käyttöliittymässä
   nakyva boolean, -- true, jos taso näkyy käyttäjälle käyttöliittymissä
   poistettu boolean DEFAULT false, -- true jos taso on poistettu käytöstä kaikissa urakoissa
   luotu timestamp,
   luoja integer REFERENCES kayttaja (id),
   muokattu timestamp,
   muokkaaja integer REFERENCES kayttaja (id)
);

-- Täytyy voida selvittää mistä puhuttaessa tarkoitetaan, uniikit nimet tehtäväryhmille
CREATE UNIQUE INDEX uniikki_tehtavaryhma_nimi ON tehtavaryhma (nimi);

-- Lisää toimenpidekooditauluun viittaus tehtäväryhmään ja käyttöliittymä
ALTER TABLE toimenpidekoodi
ADD COLUMN api_tunnus numeric,
ADD COLUMN tehtavaryhma numeric,
ADD COLUMN ensisijainen boolean;

UPDATE toimenpidekoodi
  SET api_tunnus = id WHERE taso = 4;



ALTER TABLE toimenpidekoodi
ADD COLUMN ensisijainen boolean;
