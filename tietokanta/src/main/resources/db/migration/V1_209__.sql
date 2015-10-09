-- Lisää materiaaliraporttii hoitokausiparametri

UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli', 'aikavali', true, 'hallintayksikko'::raporttikonteksti)::raporttiparametri,
                          ('Aikaväli', 'aikavali', true, 'koko maa'::raporttikonteksti)::raporttiparametri,
	                  ('Aikaväli', 'aikavali', true, 'hankinta-alue'::raporttikonteksti)::raporttiparametri,
			  ('Hoitokausi','hoitokausi', true, 'urakka'::raporttikonteksti)::raporttiparametri]
 WHERE nimi='materiaaliraportti';
 
