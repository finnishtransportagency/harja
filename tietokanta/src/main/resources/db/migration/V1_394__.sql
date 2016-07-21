-- Indeksitarkistusraportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'indeksitarkistus', 'Indeksitarkistusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.indeksitarkistus/suorita',
  'hoito'::urakkatyyppi
);
