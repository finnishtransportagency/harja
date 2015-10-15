-- Suolasakkoraportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi) VALUES (
 'suolasakko', 'Suolabonus/sakkoraportti',
 ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
 ARRAY[('Hoitokausi', 'hoitokausi',true,NULL)::raporttiparametri)]
 '#''harja.palvelin.raportointi.raportit.suolasakko/suorita'
);