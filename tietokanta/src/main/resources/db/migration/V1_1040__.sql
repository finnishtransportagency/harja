-- Jotta työmaapäiväkirjaan saadaan näkyville muutoshistoriatiedot, tarvimme lisätty sarakkeen
ALTER TABLE tyomaapaivakirja_kalusto ADD COLUMN IF NOT EXISTS lisatty timestamp;
ALTER TABLE tyomaapaivakirja_paivystaja ADD COLUMN IF NOT EXISTS lisatty timestamp;
ALTER TABLE tyomaapaivakirja_poikkeussaa ADD COLUMN IF NOT EXISTS lisatty timestamp;
ALTER TABLE tyomaapaivakirja_tapahtuma ADD COLUMN IF NOT EXISTS lisatty timestamp;
ALTER TABLE tyomaapaivakirja_saaasema ADD COLUMN IF NOT EXISTS lisatty timestamp;
ALTER TABLE tyomaapaivakirja_tieston_toimenpide ADD COLUMN IF NOT EXISTS lisatty timestamp;
ALTER TABLE tyomaapaivakirja_tyonjohtaja ADD COLUMN IF NOT EXISTS lisatty timestamp;
ALTER TABLE tyomaapaivakirja_toimeksianto ADD COLUMN IF NOT EXISTS lisatty timestamp;
