-- Muutos- ja lisätöiden raportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
        'muutos-ja-lisatyot', 'Muutos- ja lisätyöt',
        ARRAY['urakka'::raporttikonteksti,
        'hallintayksikko'::raporttikonteksti,
        'hankinta-alue'::raporttikonteksti,
        'koko maa'::raporttikonteksti],
        ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
        ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri],
        '#''harja.palvelin.raportointi.raportit.muutos-ja-lisatyot/suorita',
        'hoito'::urakkatyyppi
);
-- Lisää laaduntarkastus ja toimenpiteiden ajoittuminen työmaakokousraporttiin
UPDATE raportti
SET parametrit = array_cat(parametrit,
                           ARRAY[('Muutos- ja lisätyöt','checkbox',true,'urakka'::raporttikonteksti)::raporttiparametri])
WHERE nimi = 'tyomaakokous';
