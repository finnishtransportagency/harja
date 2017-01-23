-- Lisää ilmoitus-taulule T-LOIKista tullut ilmoituksen tunniste

ALTER TABLE ilmoitus ADD COLUMN tloik_tunniste VARCHAR(256);