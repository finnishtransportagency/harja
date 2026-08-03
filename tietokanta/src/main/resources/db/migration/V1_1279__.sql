-- Luodaan mahdollisuus tallentaa pysyvämuutos ilman tehtävämääriä
ALTER TABLE mhu_muutos_kustannusvaikutus
    ADD COLUMN tehtavamuutoksia BOOLEAN DEFAULT TRUE;

ALTER TABLE mhu_muutos_kustannusvaikutus
    ADD COLUMN syy TEXT;

-- Historiarivit
ALTER TABLE mhu_muutos_kustannusvaikutus_historia
    ADD COLUMN tehtavamuutoksia BOOLEAN DEFAULT TRUE;

ALTER TABLE mhu_muutos_kustannusvaikutus_historia
    ADD COLUMN syy TEXT;

-- Vaadi että tälle kirjataan aina syy
ALTER TABLE mhu_muutos_kustannusvaikutus
    ADD CONSTRAINT mhu_muutos_kustannusvaikutus_syy_vaadittu
        CHECK (
            tehtavamuutoksia IS NOT FALSE
                OR NULLIF(BTRIM(syy), '') IS NOT NULL
            );
