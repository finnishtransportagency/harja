-- Lisää ympäristöraportti

INSERT
  INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi)
VALUES ('ymparistoraportti', 'Ympäristöraportti',
        ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
	ARRAY[('Aikaväli', 'aikavali', true, 'hallintayksikko'::raporttikonteksti)::raporttiparametri,
              ('Aikaväli', 'aikavali', true, 'koko maa'::raporttikonteksti)::raporttiparametri,
	      ('Aikaväli', 'aikavali', true, 'hankinta-alue'::raporttikonteksti)::raporttiparametri,
     	      ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hallintayksikko'::raporttikonteksti)::raporttiparametri,
	      ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'koko maa'::raporttikonteksti)::raporttiparametri,
	      ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hankinta-alue'::raporttikonteksti)::raporttiparametri,
	      ('Hoitokausi','hoitokausi', true, 'urakka'::raporttikonteksti)::raporttiparametri],
	'#''harja.palvelin.raportointi.raportit.ymparisto/suorita'
	);
