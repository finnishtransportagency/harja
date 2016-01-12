-- Yksikköhintaisten töiden raporteille uudet kontekstit

UPDATE raportti SET konteksti = ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti]
WHERE nimi = 'yks-hint-tehtavien-summat';

UPDATE raportti SET parametrit =
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
 ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri,
 ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hallintayksikko'::raporttikonteksti)::raporttiparametri,
 ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'koko maa'::raporttikonteksti)::raporttiparametri,
 ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hankinta-alue'::raporttikonteksti)::raporttiparametri]
WHERE nimi = 'yks-hint-tehtavien-summat';

UPDATE raportti SET konteksti = ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti]
WHERE nimi = 'yks-hint-kuukausiraportti';

UPDATE raportti SET parametrit =
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
 ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri,
 ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hallintayksikko'::raporttikonteksti)::raporttiparametri,
 ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'koko maa'::raporttikonteksti)::raporttiparametri,
 ('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hankinta-alue'::raporttikonteksti)::raporttiparametri]
WHERE nimi = 'yks-hint-kuukausiraportti';