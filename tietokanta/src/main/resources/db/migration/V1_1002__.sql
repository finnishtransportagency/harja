-- Pudotetaan turhat kolumnit pois analytiikka_toteumat -taulusta
ALTER TABLE analytiikka_toteumat
    DROP COLUMN toteuma_materiaali_luotu,
    DROP COLUMN toteuma_materiaali_muokattu;
