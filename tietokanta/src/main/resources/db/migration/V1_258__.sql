-- Ilmoituksen luonti ja muutosajat
ALTER TABLE ilmoitus ADD COLUMN luotu TIMESTAMP DEFAULT NOW();
ALTER TABLE ilmoitus ADD COLUMN muokattu TIMESTAMP;
