-- Tiestö- & kelitarkastustarkastusraportti

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'tiestotarkastusraportti', 'Tiestötarkastusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
       ('Tienumero', 'tienumero', false, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.tiestotarkastus/suorita',
  'hoito'::urakkatyyppi
);

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'kelitarkastusraportti', 'Kelitarkastusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
       ('Tienumero', 'tienumero', false, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.kelitarkastus/suorita',
  'hoito'::urakkatyyppi
);