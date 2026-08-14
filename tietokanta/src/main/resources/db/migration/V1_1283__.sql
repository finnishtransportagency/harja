-- Luodaan mahdollisuus tallentaa pysyvämuutos ilman tehtävämääriä
ALTER TABLE mhu_muutos_kustannusvaikutus
    ADD COLUMN tehtavamaaramuutos_kirjattu BOOLEAN;

ALTER TABLE mhu_muutos_kustannusvaikutus
    ADD COLUMN syy TEXT;

-- Historiarivit
ALTER TABLE mhu_muutos_kustannusvaikutus_historia
    ADD COLUMN tehtavamaaramuutos_kirjattu BOOLEAN;

ALTER TABLE mhu_muutos_kustannusvaikutus_historia
    ADD COLUMN syy TEXT;

-- Päivitä olemassaolevat rivit oikein
UPDATE mhu_muutos_kustannusvaikutus 
   SET tehtavamaaramuutos_kirjattu = TRUE
 WHERE summa IS NOT NULL;

-- Vaadi että tälle kirjataan aina syy
-- Heittää repliin constraint virheen, jos yritetään tallentaa vaikutus ilman tehtävämuutoksia, ilman syytä
ALTER TABLE mhu_muutos_kustannusvaikutus
    ADD CONSTRAINT mhu_muutos_kustannusvaikutus_syy_vaadittu
        CHECK (
            tehtavamaaramuutos_kirjattu IS NOT FALSE
                OR NULLIF(BTRIM(syy), '') IS NOT NULL
            );
