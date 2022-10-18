-- Lisätään urakka_tehtavamaara taululle tieto, että onko siihen lisätty määrä muokkaantunut sopimukselta/tarjouksesta
ALTER TABLE urakka_tehtavamaara
  ADD COLUMN "muuttunut-tarjouksesta?" BOOLEAN DEFAULT FALSE NOT NULL;
-- Ja koska kaikki ei alueelliset oli suunniteltavat määrät on lähtökohtaisesti näytettävä, ikään kuin ne olisivat muuttuneet
-- tarjoukselta, niin asetetaan ne kaikki muuttuneiksi
UPDATE urakka_tehtavamaara ut SET "muuttunut-tarjouksesta?" = true
 WHERE ut.tehtava IN (SELECT t.id FROM toimenpidekoodi t WHERE t.aluetieto = false);
