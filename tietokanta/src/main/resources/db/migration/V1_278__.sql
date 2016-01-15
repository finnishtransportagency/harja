-- Laatupoikkeama- & soratietarkastusraportti

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'laatupoikkeamaraportti', 'Laatupoikkeamaraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.laatupoikkeama/suorita',
  'hoito'::urakkatyyppi
);

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'soratietarkastusraportti', 'Soratietarkastusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
       ('Tienumero', 'tienumero', false, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.soratietarkastus/suorita',
  'hoito'::urakkatyyppi
);