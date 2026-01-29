CREATE TYPE ajastetuntehtavan_tyyppi AS ENUM ('siirra_toteumat_analytiikalle');

CREATE TABLE ajastetut_tehtavat
(
    tyyppi              ajastetuntehtavan_tyyppi not null,
    suoritusyritys_aika timestamp,
    onnistunut          boolean,
    virhe               text DEFAULT NULL
);

-- Oletetaan, että edellinen suoritus on onnistunut, jotta taulu ei ole tuotannossa tyhjä.
-- Seuraava ajastettu tehtävä ajetaan tässä syötetyn "suoritusyritys_aika" kohdasta eteenpäin siihen asti, missä se ajo tapahtuu.
-- Eli jos suoritusyritys-aika on vaikka 1.1.2026 klo 12:00 ja uusi ajastettu tehtävä ajetaan 2.1.2026 klo 10:00, niin ajettu tehtävä käsittelee aikavälin 1.1.2026 klo 12:00 - 2.1.2026 klo 10:00.
INSERT INTO ajastetut_tehtavat (tyyppi, suoritusyritys_aika, onnistunut, virhe)
VALUES ('siirra_toteumat_analytiikalle'::ajastetuntehtavan_tyyppi, NOW() - INTERVAL '1 day', TRUE, NULL);
