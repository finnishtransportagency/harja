-- Funktio, joka lisää uuden geometriapäivityksen, jos sellaista ei löydy päivittämisen yhteydessä.
-- Päivittää myös tilatiedot päivityksen ajamisen jälkeen.
CREATE OR REPLACE FUNCTION paivita_geometriapaivityksen_viimeisin_paivitys(geometriapaivitys_ CHARACTER VARYING,
                                                                           viimeisin_paivitys_ TIMESTAMP)
    RETURNS VOID AS $$
DECLARE
    geometriapaivitys_id INTEGER;
BEGIN
    SELECT id
    INTO geometriapaivitys_id
    FROM geometriapaivitys
    WHERE nimi = geometriapaivitys_;

    IF (viimeisin_paivitys_ IS NOT NULL) THEN
    -- try update
    UPDATE geometriapaivitys
    SET viimeisin_paivitys          = viimeisin_paivitys_,
        seuraava_paivitys           = NOW() + interval '1 week',
        edellinen_paivitysyritys = NULL
    WHERE id = geometriapaivitys_id;

    IF NOT FOUND
    THEN
        INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, edellinen_paivitysyritys)
        VALUES (geometriapaivitys_, viimeisin_paivitys_, NOW() + interval '1 week', NULL);
    END IF;

    ELSE
        -- try update
        UPDATE geometriapaivitys
        SET  edellinen_paivitysyritys           = NOW()
        WHERE id = geometriapaivitys_id;

        IF NOT FOUND
        THEN
            INSERT INTO geometriapaivitys (nimi, edellinen_paivitysyritys)
            VALUES (geometriapaivitys_, NOW());
        END IF;

    END IF;
END;
$$ LANGUAGE plpgsql;