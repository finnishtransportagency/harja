<<<<<<< HEAD
-- SiltatarkastusraporTTI

INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'siltatarkastus', 'Siltatarkastusraportti',
 ARRAY['urakka'::raporttikonteksti,
       'hallintayksikko'::raporttikonteksti,
       'hankinta-alue'::raporttikonteksti,
       'koko maa'::raporttikonteksti],
 ARRAY[('Vuosi', 'urakan-vuosi', true, NULL)::raporttiparametri,
       ('Silta', 'silta', true, 'urakka'::raporttikonteksti)::raporttiparametri],
 '#''harja.palvelin.raportointi.raportit.siltatarkastus/suorita',
  'hoito'::urakkatyyppi
);
=======
-- Lisää asiatarkastuksen tiedot POT-lomakkeelle
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_pvm DATE;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_tarkastaja VARCHAR(1024);
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_tekninen_osa BOOLEAN;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_taloudellinen_osa BOOLEAN;
ALTER TABLE paallystysilmoitus ADD COLUMN asiatarkastus_lisatiedot VARCHAR(4096);
>>>>>>> develop
