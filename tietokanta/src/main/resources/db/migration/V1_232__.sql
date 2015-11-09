-- Nullaa tyhjät yksiköt sisältävät toimenpidekoodit oikein
UPDATE toimenpidekoodi SET yksikko = NULL WHERE yksikko = 'NULL';