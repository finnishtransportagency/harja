INSERT INTO raportti (nimi, kuvaus, urakkatyyppi, konteksti, parametrit, koodi) VALUES (
  'ilmoitusraportti','Ilmoitusraportti', 'hoito'::urakkatyyppi,
  ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
  ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri],
  '#''harja.palvelin.raportointi.raportit.ilmoitus/suorita'
);
