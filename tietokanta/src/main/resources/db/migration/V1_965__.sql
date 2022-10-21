ALTER TABLE erilliskustannus ADD COLUMN kasittelytapa laatupoikkeaman_kasittelytapa, ADD COLUMN laskutuskuukausi DATE;
CREATE TABLE erilliskustannus_liite (
       bonus INTEGER REFERENCES erilliskustannus(id),
       liite INTEGER REFERENCES liite(id));
       
COMMENT ON TABLE erilliskustannus_liite IS 'Bonuksien ja sanktioiden näkymässä bonuksiin liitetyt liitteet';
