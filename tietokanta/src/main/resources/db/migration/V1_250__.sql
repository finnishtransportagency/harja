INSERT INTO raportti (nimi, kuvaus, urakkatyyppi, konteksti, parametrit, koodi) VALUES (
  'ilmoitusraportti','Ilmoitusraportti', 'hoito'::urakkatyyppi,
  ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
  ARRAY[('Aikaväli', 'aikavali', true, 'hallintayksikko'::raporttikonteksti)::raporttiparametri,
  ('Aikaväli', 'aikavali', true, 'koko maa'::raporttikonteksti)::raporttiparametri,
  ('Aikaväli', 'aikavali', true, 'hankinta-alue'::raporttikonteksti)::raporttiparametri,
  ('Hoitokausi','hoitokausi', true, 'urakka'::raporttikonteksti)::raporttiparametri],
  '#''harja.palvelin.raportointi.raportit.ilmoitus/suorita'
);