ALTER TABLE jarjestelman_asetukset
ADD COLUMN arvonvahennys_validoinnit_kaytossa BOOLEAN   DEFAULT TRUE; -- Tämä asetetaan hallinnasta

INSERT INTO jarjestelman_asetukset (arvonvahennys_validoinnit_kaytossa)
VALUES (TRUE);
