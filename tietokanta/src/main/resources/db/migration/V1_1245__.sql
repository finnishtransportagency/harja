-- Tällä hetkellä on tiedossa vain yksi tyyppi, joka tauluun tallennetaan
CREATE TYPE ajastettu_tehtava AS ENUM ('siirra_toteumat_analytiikalle');

-- Tallenna ajastettujen tehtävien logit tänne talteen.
CREATE TABLE ajastetut_tehtavat
(
    tyyppi            ajastettu_tehtava not null,
    alkuaika_valilta  timestamp DEFAULT NULL, -- Tehtävän käsittelemä aikaväli alkaa tästä ajasta
    loppuaika_valilta timestamp DEFAULT NULL, -- Tehtävän käsittelemä aikaväli päättyy tähän aikaan
    onnistunut        boolean,
    virhe             text      DEFAULT NULL,
    luotu             timestamp
);

-- Oletetaan, että edellinen suoritus on onnistunut, jotta taulu ei ole tuotannossa tyhjä.
-- Seuraava ajastettu tehtävä ajetaan tässä syötetyn "suoritusyritys_aika" kohdasta eteenpäin siihen asti, missä se ajo tapahtuu.
-- Eli jos suoritusyritys-aika on vaikka 1.1.2026 klo 12:00 ja uusi ajastettu tehtävä ajetaan 2.1.2026 klo 10:00, niin ajettu tehtävä käsittelee aikavälin 1.1.2026 klo 12:00 - 2.1.2026 klo 10:00.
INSERT INTO ajastetut_tehtavat (tyyppi, alkuaika_valilta, loppuaika_valilta, onnistunut, virhe, luotu)
VALUES ('siirra_toteumat_analytiikalle'::ajastettu_tehtava, NOW() - INTERVAL '2 day', NOW() - INTERVAL '1 day', TRUE, NULL, NOW());
