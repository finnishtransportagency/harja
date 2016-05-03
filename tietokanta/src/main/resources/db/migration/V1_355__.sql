<<<<<<< HEAD
-- Ylläpitokohteelle keskimääräinen vuorokausiliikenne ja nykyinen päällyste (YHA-integraatio)

ALTER TABLE yllapitokohde ADD COLUMN keskimaarainen_vuorokausiliikenne INTEGER;
ALTER TABLE yllapitokohde ADD COLUMN nykyinen_paallyste INTEGER;

ALTER TABLE yllapitokohdeosa DROP COLUMN kvl;
ALTER TABLE yllapitokohdeosa DROP COLUMN nykyinen_paallyste;
=======
-- Lisää toimenpiteiden ajoittuminen -raportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi)
  VALUES ('toimenpideajat','Toimenpiteiden ajoittuminen',
          ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti,
               'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
          ARRAY[('Aikaväli','aikavali',true,NULL)::raporttiparametri,
               ('Näytä urakka-alueet eriteltynä','urakoittain',true,'koko maa')::raporttiparametri,
               ('Näytä urakka-alueet eriteltynä','urakoittain',true,'hallintayksikko')::raporttiparametri,
               ('Näytä urakka-alueet eriteltynä','urakoittain',true,'hankinta-alue')::raporttiparametri,
               ('Hoitoluokat', 'hoitoluokat', true, NULL)::raporttiparametri],
          '#''harja.palvelin.raportointi.raportit.toimenpideajat/suorita',
          'hoito');
>>>>>>> develop
