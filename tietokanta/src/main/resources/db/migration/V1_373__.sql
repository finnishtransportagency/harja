-- Lisää Toimenpidekilometrit-raportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi)
  VALUES ('toimenpidekilometrit','Toimenpidekilometrit',
          ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti,
               'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
          ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri,
               ('Hoitoluokat', 'hoitoluokat', true, NULL)::raporttiparametri],
          '#''harja.palvelin.raportointi.raportit.toimenpidekilometrit/suorita',
          'hoito');
