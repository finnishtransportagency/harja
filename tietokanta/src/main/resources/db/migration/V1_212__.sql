-- Kuvaus: älä salli arvoa null toimenpidekoodien kokonaishintainen-sarakkeeseen
UPDATE toimenpidekoodi SET kokonaishintainen = false WHERE kokonaishintainen IS NULL;
ALTER TABLE toimenpidekoodi ALTER COLUMN kokonaishintainen SET NOT NULL;