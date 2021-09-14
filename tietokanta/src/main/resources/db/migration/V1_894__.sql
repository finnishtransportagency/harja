-- Paikkauskohteita voi nykyään tallentaa Harjaan myös käyttöliittymän kautta, jolloin tallentava henkilö saattaa vaihdella.
ALTER TABLE paikkauskohde DROP CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja_urakka;
ALTER TABLE paikkauskohde ADD CONSTRAINT paikkauskohteen_uniikki_ulkoinen_id_luoja UNIQUE ("ulkoinen-id", "urakka-id");
