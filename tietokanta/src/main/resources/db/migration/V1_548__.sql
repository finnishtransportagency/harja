-- Vikailmoitus
ALTER TABLE vv_vikailmoitus DROP COLUMN "toteuma-id";
ALTER TABLE vv_vikailmoitus ADD COLUMN "toimenpide-id" INTEGER REFERENCES reimari_toimenpide(id);

-- reimari_toimenpide taulun "tyyppi" -> "toimenpidetyyppi". Vastaa paremmin sitä millä nimellä me sitä kutsumme
ALTER TABLE reimari_toimenpide RENAME COLUMN "reimari-tyyppi" TO  "reimari-toimenpidetyyppi";

-- Sopparin nimessä tai sampoid:ssä ei saa olle merkkejä, joita käytetään SQL-parsinnassa
ALTER TABLE sopimus ADD CONSTRAINT sallittu_sampoid CHECK (nimi NOT LIKE '%=%');
ALTER TABLE sopimus ADD CONSTRAINT sallittu_nimi CHECK (nimi NOT LIKE '%=%');

-- Toimenpidekoodissa sama homma, kielletään SQL-parsinnassa käytetyt merkit
ALTER TABLE toimenpidekoodi ADD CONSTRAINT sallittu_nimi CHECK (nimi NOT LIKE '%^%');

-- Tiukenna toteuman tr-kenttiä
ALTER TABLE toteuma ADD CONSTRAINT toteuman_tr_numero_validi CHECK (tr_numero >= 0);
ALTER TABLE toteuma ADD CONSTRAINT toteuman_tr_alkuosa_validi CHECK (tr_alkuosa >= 0);
ALTER TABLE toteuma ADD CONSTRAINT toteuman_tr_alkuetaisyys_validi CHECK (tr_alkuetaisyys >= 0);
ALTER TABLE toteuma ADD CONSTRAINT toteuman_tr_loppuosa_validi CHECK (tr_loppuosa >= 0);
ALTER TABLE toteuma ADD CONSTRAINT toteuman_tr_loppuetaisyys_validi CHECK (tr_loppuetaisyys >= 0);