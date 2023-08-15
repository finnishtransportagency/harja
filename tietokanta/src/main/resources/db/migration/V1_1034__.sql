-- Turha sarake työmaapäiväkirjassa
ALTER TABLE tyomaapaivakirja_kommentti DROP COLUMN tunnit;
-- Lisää toiminto poistaa kommentteja
ALTER TABLE tyomaapaivakirja_kommentti ADD COLUMN IF NOT EXISTS poistettu boolean DEFAULT false;
-- Lisää muokkaajan tiedot
ALTER TABLE tyomaapaivakirja_kommentti ADD COLUMN IF NOT EXISTS muokattu timestamp;
ALTER TABLE tyomaapaivakirja_kommentti ADD COLUMN IF NOT EXISTS muokkaaja integer references kayttaja (id);
