ALTER TABLE ilmoitustoimenpide ADD COLUMN virhe_lkm INTEGER DEFAULT 0;
COMMENT ON COLUMN ilmoitustoimenpide.virhe_lkm IS 'Laskuri sille, kuinka monesti ilmoitustoimenpiteen lähetys T-Loikiin on epäonnistunut.';

ALTER TABLE ilmoitustoimenpide ADD COLUMN ed_lahetysvirhe TIMESTAMP DEFAULT NULL;
COMMENT ON COLUMN ilmoitustoimenpide.ed_lahetysvirhe IS 'Ilmoitustoimenpiteen edellisen lähetysvirheen aikaleima.';
