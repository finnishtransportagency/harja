-- Yksikköhintaisten töiden summat

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'yks-hint-tehtavien-summat', 'Yksikköhintaisten työt summittain',
 ARRAY['urakka'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
 ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-summittain/suorita',
 'hoito'::urakkatyyppi
);