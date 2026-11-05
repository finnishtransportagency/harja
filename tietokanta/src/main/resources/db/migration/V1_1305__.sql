-- Lisätään tehtäville tieto, onko tehtävä materiaalina raportoitava
ALTER TABLE tehtava 
    ADD COLUMN "materiaalina_raportoitava?" BOOLEAN NOT NULL DEFAULT FALSE;

-- Löytyy dokumentaatiosta: "Materiaaleina raportoitavat tehtävät"
UPDATE tehtava
   SET "materiaalina_raportoitava?" = TRUE
 WHERE nimi IN (
           'Liukkaudentorjunta suolaamalla (materiaali)',
           'Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)',
           'Liukkaudentorjunta hiekoituksella (materiaali)',
           'Ennalta arvaamattomien kuljetusten avustaminen (materiaali)',
           'Sorastus',
           'Liikenteen varmistaminen kelirikkokohteessa (materiaali)',
           'Reunantäyttö',
           'Kesäsuola (CaCl2, materiaali)'
);
