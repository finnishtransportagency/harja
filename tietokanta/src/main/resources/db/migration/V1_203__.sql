-- Raporttien tietokantataulu

CREATE TYPE raporttikonteksti AS ENUM ('urakka','hallintayksikko','hankinta-alue','koko maa');

CREATE TYPE raporttiparametri AS (
  nimi varchar(64),           -- parametrin nimi käyttöliittymässä
  tyyppi varchar(64),         -- parametrin tyyppi (esim yleiset, kuten "pvm", "teksti" tai tietty "hoitokausi")
  pakollinen boolean,         -- onko tämän arvo pakollinen suorittamiselle
  konteksti raporttikonteksti -- missä kontekstissa suoritettaessa tämä pyydetään (NULL = kaikissa)
);

CREATE TABLE raportti (
  nimi varchar(128),              -- raportin keyword nimi, esim 'laskutusyhteenveto'
  kuvaus varchar(255),            -- raportin ihmisen luettava nimi, esim, 'Laskutusyhteenveto'
  konteksti raporttikonteksti[],  -- kontekstit, jossa raportin voi suorittaa
  parametrit raporttiparametri[], -- parametrit
  koodi text                      -- raportin clojure koodi, täytyy evaluoitua funktioksi
);

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi) VALUES (
 'laskutusyhteenveto', 'Laskutusyhteenveto',
 ARRAY['urakka'::raporttikonteksti], 
 ARRAY[('Hoitokausi', 'hoitokausi',true,NULL)::raporttiparametri,
       ('Kuukausi','hoitokauden-kuukausi',true,NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.laskutusyhteenveto/suorita'
);

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi) VALUES (
 'materiaaliraportti','Materiaaliraportti',
 ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.materiaali/suorita'
);

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi) VALUES (
 'yks-hint-tyot', 'Yksikköhintaisten töiden raportti',
 ARRAY['urakka'::raporttikonteksti],
 ARRAY[('Hoitokausi', 'hoitokausi',true,NULL)::raporttiparametri,
       ('Kuukausi','hoitokauden-kuukausi',true,NULL)::raporttiparametri,
       ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot/suorita'
);
