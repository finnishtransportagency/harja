-- Turvallisuuspoikkeamaraportti

INSERT
  INTO raportti (nimi,kuvaus,konteksti,parametrit,koodi,urakkatyyppi)
VALUES ('turvallisuuspoikkeama','Turvallisuuspoikkeamaraportti',
        ARRAY['urakka'::raporttikonteksti,'hallintayksikko'::raporttikonteksti,'hankinta-alue'::raporttikonteksti,'koko maa'::raporttikonteksti],
	ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri],
	'#''harja.palvelin.raportointi.raportit.turvallisuuspoikkeamat/suorita',
	'hoito'::urakkatyyppi);

