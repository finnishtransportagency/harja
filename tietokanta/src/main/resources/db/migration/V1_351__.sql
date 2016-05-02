-- Päivitä työmaakokousraporttien valikoima
UPDATE raportti
   SET parametrit = ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri,
   ('Erilliskustannukset','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Ilmoitukset','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Kelitarkastusraportti','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Laatupoikkeamat','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Laskutusyhteenveto','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Materiaaliraportti','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Sanktioiden yhteenveto','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Soratietarkastukset', 'checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Tiestötarkastukset', 'checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Turvallisuusraportti','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Yksikköhintaiset työt kuukausittain', 'checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Yksikköhintaiset työt päivittäin','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Yksikköhintaiset työt tehtävittäin','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri,
   ('Ympäristöraportti','checkbox', true, 'urakka'::raporttikonteksti)::raporttiparametri]


WHERE nimi = 'tyomaakokous';
