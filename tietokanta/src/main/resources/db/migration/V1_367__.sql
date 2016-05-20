-- Laaduntarkastusraportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'laaduntarkastusraportti', 'Laaduntarkastusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
       ('Tienumero', 'tienumero', false, NULL)::raporttiparametri,
       ('Vain laadun alitukset', 'checkbox', false, NULL)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.laaduntarkastus/suorita',
  'hoito'::urakkatyyppi
);

-- Lisää laaduntarkastus ja toimenpiteiden ajoittuminen työmaakokousraporttiin
UPDATE raportti
   SET parametrit = array_cat(parametrit,
     ARRAY[
      ('Laaduntarkastusraportti','checkbox',true,'urakka'::raporttikonteksti)::raporttiparametri,
      ('Toimenpiteiden ajoittuminen','checkbox',true,'urakka'::raporttikonteksti)::raporttiparametri
     ])
WHERE nimi = 'tyomaakokous';
