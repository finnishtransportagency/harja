ALTER TABLE sanktio
    ADD COLUMN normaalimaara NUMERIC,
    ADD COLUMN omailmoitettu BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN sanktio.normaalimaara IS 'Sanktion profiilista ratkaistu normaalimäärä ennen mahdollista omailmoituspuolitusta.';
COMMENT ON COLUMN sanktio.omailmoitettu IS 'Tapahtumaan tallennettu käyttäjän omailmoitusvalinta.';
