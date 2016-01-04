-- Yksikköhintaisten töiden kuukausiraportti

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi) VALUES (
 'yks-hint-kuukausiraportti', 'Yksikköhintaisten työt kuukausittain',
 ARRAY['urakka'::raporttikonteksti],
 ARRAY[('Hoitokausi', 'hoitokausi',true,NULL)::raporttiparametri,
       ('Kuukausi','hoitokauden-kuukausi',true,NULL)::raporttiparametri,
       ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.yksikkohintaiset-tyot-kuukausittain/suorita'
);

UPDATE raportti SET kuvaus = 'Yksikköhintaiset työt päivittäin' WHERE nimi = 'yks-hint-tyot';