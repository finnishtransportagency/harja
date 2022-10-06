-- Funktio, joka lisää uuden geometriapäivityksen, jos sellaista ei löydy päivittämisen yhteydessä.
-- Lisätään mukaan seuraava kayta_paikallista_tiedostoa-sarakkeen päivitys sekä seuraava tiedostonlatausajankohta.
-- Sarake päivitetään aina falseksi päivityksen jälkeen, jotta manuaalinen päivitys ei jää vahingossa päälle.
-- Tiedostoa ei ladata aina kun

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

    -- try update
    UPDATE geometriapaivitys
    SET viimeisin_paivitys          = viimeisin_paivitys_,
        seuraava_paivitys           = NOW() + interval '1 week',
        kayta_paikallista_tiedostoa = false
    WHERE id = geometriapaivitys_id;

    IF NOT FOUND
    THEN
        INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys)
        VALUES (geometriapaivitys_, viimeisin_paivitys_, NOW() + interval '1 week');
    END IF;
END;
$$ LANGUAGE plpgsql;