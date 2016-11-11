-- Lisää urakka-alueiden erittely ilmoitusraporttiin
UPDATE raportti
SET parametrit =
ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri,
('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'koko maa'::raporttikonteksti)::raporttiparametri,
('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hankinta-alue'::raporttikonteksti)::raporttiparametri,
('Näytä urakka-alueet eriteltynä', 'urakoittain', true, 'hallintayksikko'::raporttikonteksti)::raporttiparametri]
WHERE nimi = 'muutos-ja-lisatyot';