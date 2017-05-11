-- Sopparin nimessä tai sampoid:ssä ei saa olle merkkejä, joita käytetään SQL-parsinnassa
ALTER TABLE sopimus ADD CONSTRAINT sallittu_sampoid CHECK (nimi NOT LIKE '%=%');
ALTER TABLE sopimus ADD CONSTRAINT sallittu_nimi CHECK (nimi NOT LIKE '%=%');

-- Toimenpidekoodissa sama homma, kielletään SQL-parsinnassa käytetyt merkit
ALTER TABLE toimenpidekoodi ADD CONSTRAINT sallittu_nimi CHECK (nimi NOT LIKE '%^%');
