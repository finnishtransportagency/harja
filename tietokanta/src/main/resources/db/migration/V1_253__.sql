-- Turvallisuuspoikkeamaraportti ja muiden raporttien parametrit

INSERT
  INTO raportti (nimi,kuvaus,konteksti,parametrit,koodi,urakkatyyppi)
VALUES ('turvallisuus','Turvallisuusraportti',
        ARRAY['urakka'::raporttikonteksti,'hallintayksikko'::raporttikonteksti,'hankinta-alue'::raporttikonteksti,'koko maa'::raporttikonteksti],
	ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri,
	      ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hallintayksikko')::raporttiparametri,
	      ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'koko maa')::raporttiparametri,
	      ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hankinta-alue')::raporttiparametri
	      ],
	'#''harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat/suorita',
	'hoito'::urakkatyyppi);

UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri,
                          ('Laskutusyhteenveto','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
			  ('Yksikköhintaisten töiden raportti', 'checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
			  ('Ympäristöraportti','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
			  ('Ilmoitusraportti','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
			  ('Turvallisuusraportti','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri]
 WHERE nimi = 'tyomaakokous';

UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri]
 WHERE nimi = 'suolasakko';

UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri,
                          ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, NULL)::raporttiparametri]
 WHERE nimi = 'ymparistoraportti';

UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri]
 WHERE nimi = 'materiaaliraportti';

UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
                          ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri]
 WHERE nimi = 'yks-hint-tyot';

UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri]
 WHERE nimi = 'laskutusyhteenveto';

