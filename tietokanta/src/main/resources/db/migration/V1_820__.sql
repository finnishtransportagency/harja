ALTER TABLE paikkauskohde
    ADD COLUMN tarkistettu TIMESTAMP,
    ADD COLUMN "tarkistaja-id" INTEGER REFERENCES kayttaja (id),
    ADD COLUMN "ilmoitettu-virhe" TEXT;