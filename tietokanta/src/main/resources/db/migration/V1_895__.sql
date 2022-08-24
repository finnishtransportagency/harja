-- Paikkauskohteita voi nykyään tallentaa Harjaan myös käyttöliittymän kautta, jolloin tallentava henkilö saattaa vaihdella.
ALTER TABLE paikkauskohde DROP CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja;
ALTER TABLE paikkauskohde ADD CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_urakka UNIQUE ("ulkoinen-id", "urakka-id");