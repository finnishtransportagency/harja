-- Mahdollistetaan muutostiedon päättely urakka_parametrit taulusta
ALTER TABLE urakka_parametrit
    ADD COLUMN muutosten_hallinta BOOLEAN DEFAULT FALSE NOT NULL;

COMMENT ON COLUMN urakka_parametrit.muutosten_hallinta IS 'Määrittää, onko muutokset urakalla käytössä. Oletuksena false, eli muutosten hallinta ei käytössä. Yleisesti se on käytössä vasta -25 alkaneilla ja sen jälkeisillä urakoilla.';

-- Lisätään kaikille -25 alkaneille urakalle tieto, että muutosten hallinta on käytössä
-- Hox. Tuotannossa on urakka, joka alkaa -25 vuonna, mutta Sampon virheellisen syötön takia se alkaa 1.1. eikä 1.10.
UPDATE urakka_parametrit
SET muutosten_hallinta = true
WHERE urakkaid IN (SELECT id FROM urakka WHERE alkupvm >= '2025-01-01' AND tyyppi = 'teiden-hoito');
