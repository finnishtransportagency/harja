-- SiltatarkastusraporTTI

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'siltatarkastus', 'Siltatarkastusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Vuosi', 'hoitokauden-vuosi', true, NULL)::raporttiparametri,
       ('Silta', 'silta', true, 'urakka'::raporttikonteksti)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.siltatarkastus/suorita',
  'hoito'::urakkatyyppi
);