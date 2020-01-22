--
ALTER TABLE lasku
ADD COLUMN laskun_numero INTEGER,
ADD COLUMN lisatieto TEXT,
ADD COLUMN koontilaskun_kuukausi TIMESTAMP,
ADD COLUMN suorittaja integer references aliurakoitsija(id);

ALTER TABLE lasku_kohdistus
DROP COLUMN suorittaja;