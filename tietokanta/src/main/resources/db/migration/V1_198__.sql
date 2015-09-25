-- Kuvaus: Lisää toimenpidekoodille lippu kokonaishintaisuudesta
ALTER TABLE toimenpidekoodi ADD COLUMN kokonaishintainen BOOLEAN DEFAULT FALSE;

UPDATE toimenpidekoodi
SET kokonaishintainen = FALSE
WHERE kokonaishintainen IS NULL;