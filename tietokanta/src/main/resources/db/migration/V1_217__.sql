-- Lisää talvisuolan käyttöraja

-- Talvisuolan käyttöraja
ALTER TABLE suolasakko ADD COLUMN talvisuolaraja NUMERIC;

-- Onko talvisuolan rajoitukset ja sakot lainkaan käytössä
ALTER TABLE suolasakko ADD COLUMN kaytossa BOOLEAN NOT NULL DEFAULT true;

-- Pohjavesialueiden talvisuolarajat
CREATE TABLE pohjavesialue_talvisuola (
  pohjavesialue INTEGER REFERENCES pohjavesialue (id),
  urakka INTEGER REFERENCES urakka (id),
  hoitokauden_alkuvuosi smallint, 
  talvisuolaraja NUMERIC
);

