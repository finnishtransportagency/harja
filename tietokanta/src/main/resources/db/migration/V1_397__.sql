-- Varustetoteumalle tieto siitä onko lähetetty tierekisteriin
ALTER TABLE varustetoteuma ADD COLUMN lahetetty_tierekisteriin BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE varustetoteuma ADD COLUMN sijainti geometry;