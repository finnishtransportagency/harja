--
ALTER TABLE lasku
ADD COLUMN laskun_numero INTEGER,
ADD COLUMN lisatieto TEXT,
ADD COLUMN koontilaskun_kuukausi TEXT,
ADD COLUMN suorittaja integer references aliurakoitsija(id);

ALTER TABLE lasku_kohdistus
DROP COLUMN suorittaja;

-- Muutetaan laskun viite globaalisti uniikista per projekti
ALTER TABLE lasku DROP CONSTRAINT lasku_viite_key;
CREATE UNIQUE INDEX lasku_urakka_viite_uniikki_idx ON lasku (urakka, viite);
