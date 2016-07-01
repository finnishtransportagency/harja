<<<<<<< HEAD
-- Välitavoitteelle valtakunnallisuus ja linkitys toiseen (valtakunnalliseen) välitavoitteeseen
CREATE TYPE valitavoite_tyyppi AS ENUM ('kertaluontoinen','toistuva');
ALTER TABLE valitavoite ADD COLUMN tyyppi valitavoite_tyyppi;
ALTER TABLE valitavoite ADD COLUMN takaraja_toistopaiva INT CHECK (takaraja_toistopaiva > 0 AND takaraja_toistopaiva <= 31);
ALTER TABLE valitavoite ADD COLUMN takaraja_toistokuukausi INT CHECK (takaraja_toistokuukausi > 0 AND takaraja_toistokuukausi <= 12);
ALTER TABLE valitavoite ADD COLUMN urakkatyyppi urakkatyyppi; -- Urakkatyyppi jota valtakunnallinen tavoite koskee
ALTER TABLE valitavoite ADD COLUMN valtakunnallinen_valitavoite integer REFERENCES valitavoite (id); -- Viittaus välitavoitteeseen ilman urakka-id:tä
=======
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
>>>>>>> develop
