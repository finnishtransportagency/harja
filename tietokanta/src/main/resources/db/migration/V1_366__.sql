-- Laaduntarkastusraportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'laaduntarkastusraportti', 'Laaduntarkastusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
       ('Tienumero', 'tienumero', false, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.laaduntarkastus/suorita',
  'hoito'::urakkatyyppi
);
