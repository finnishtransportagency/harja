ALTER TABLE hairioilmoitus
    ADD COLUMN alkuaika TIMESTAMP CHECK (loppuaika IS NULL OR alkuaika < loppuaika),
    ADD COLUMN loppuaika TIMESTAMP CHECK (alkuaika IS NULL OR alkuaika < loppuaika);

ALTER TABLE hairioilmoitus
    ADD CONSTRAINT hairioilmoitus_aika_check
        CHECK ((loppuaika IS NULL AND alkuaika IS NULL) OR
               loppuaika > alkuaika)
