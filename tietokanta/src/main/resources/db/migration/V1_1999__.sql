-- Tiedosto nimetty väärällä numerolla, koska migraatioon on pitkä aika ja näin vältytään konflikteilta
-- Lisätään urakka -taulun käyttämään sopimustyyppi tietueeseen tiedot mhu ja mhu+ vaativien hoitourakoiden erottelemiseksi.
ALTER TYPE sopimustyyppi ADD VALUE 'mhu';
ALTER TYPE sopimustyyppi ADD VALUE 'mhu+';


-- Lisätään välikatselmuksen päätöksiin liittyvät taulut
CREATE TABLE paatos_lupaus
(
    id                    SERIAL PRIMARY KEY,
    urakkaid              INTEGER        NOT NULL,
    hoitokauden_alkuvuosi INTEGER        NOT NULL,
    tyyppi                VARCHAR(255)   NOT NULL, -- bonus, sanktio, taytetty
    tavoitehinta          NUMERIC(10, 2) NOT NULL,
    tarjous_tavoitehinta  NUMERIC(10, 2) NOT NULL,
    luvatut_pisteet       INTEGER        NOT NULL,
    toteutuneet_pisteet   INTEGER        NOT NULL,
    lupausbonus           NUMERIC(10, 2),
    lupaussanktio         NUMERIC(10, 2),
    erilliskustannus_id   INTEGER,
    sanktio_id            INTEGER,
    luotu                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                 INTEGER        NOT NULL,
    poistettu             BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja              INTEGER,
    FOREIGN KEY (erilliskustannus_id) REFERENCES erilliskustannus (id),
    FOREIGN KEY (sanktio_id) REFERENCES sanktio (id),
    FOREIGN KEY (luoja) REFERENCES kayttaja (id)
);

CREATE TABLE paatos_tavoitehinnan_muutos
(
    id                    SERIAL PRIMARY KEY,
    urakkaid              INTEGER        NOT NULL,
    hoitokauden_alkuvuosi INTEGER        NOT NULL,
    versio                VARCHAR(255)   NOT NULL, -- 1/2
    tavoitehinta          NUMERIC(10, 2) NOT NULL,
    kattohinta            NUMERIC(10, 2),
    luotu                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                 INTEGER        NOT NULL,
    poistettu             BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja              INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id)
);

CREATE TABLE paatos_tavoitehinta_ylitys
(
    id                       SERIAL PRIMARY KEY,
    urakkaid                 INTEGER        NOT NULL,
    hoitokauden_alkuvuosi    INTEGER        NOT NULL,
    versio                   VARCHAR(255)   NOT NULL, -- 1/2/3
    tavoitehinta             NUMERIC(10, 2) NOT NULL,
    toteutuneet_kustannukset NUMERIC(10, 2) NOT NULL,
    ylityksen_maara          NUMERIC(10, 2),
    tilaajan_prosentti       INTEGER,
    urakoitsijan_prosentti   INTEGER,
    tilaaja_maksaa           NUMERIC(10, 2),
    urakoitsija_maksaa       NUMERIC(10, 2),
    siirto                   NUMERIC(10, 2),
    kulu_id                  INTEGER,
    luotu                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                    INTEGER        NOT NULL,
    poistettu                BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                 INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (kulu_id) REFERENCES kulu (id)
);

CREATE TABLE paatos_tavoitehinta_alitus
(
    id                       SERIAL PRIMARY KEY,
    urakkaid                 INTEGER        NOT NULL,
    hoitokauden_alkuvuosi    INTEGER        NOT NULL,
    versio                   VARCHAR(255)   NOT NULL, -- 1/2
    tavoitehinta             NUMERIC(10, 2) NOT NULL,
    toteutuneet_kustannukset NUMERIC(10, 2) NOT NULL,
    alituksen_maara          NUMERIC(10, 2),
    siirron_maara            NUMERIC(10, 2),          -- Jos siirretään seuraavalle vuodelle niin tähän se summa. Viimeisenä vuotena ei voida enää siirtää
    tavoitepalkkio           NUMERIC(10, 2),
    kulu_id                  INTEGER,
    luotu                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                    INTEGER        NOT NULL,
    poistettu                BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                 INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (kulu_id) REFERENCES kulu (id)
);

CREATE TABLE paatos_kattohinta
(
    id                       SERIAL PRIMARY KEY,
    urakkaid                 INTEGER        NOT NULL,
    hoitokauden_alkuvuosi    INTEGER        NOT NULL,
    kattohinta               NUMERIC(10, 2) NOT NULL,
    toteutuneet_kustannukset NUMERIC(10, 2) NOT NULL,
    ylityksen_maara          NUMERIC(10, 2),
    urakoitsija_maksaa       NUMERIC(10, 2),
    siirrettava_maara        NUMERIC(10, 2),
    kulu_id                  INTEGER,
    luotu                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                    INTEGER        NOT NULL,
    poistettu                BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                 INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (kulu_id) REFERENCES kulu (id)
);

CREATE TABLE paatos_hoitokauden_lopun_hinta
(
    id                    SERIAL PRIMARY KEY,
    urakkaid              INTEGER        NOT NULL,
    hoitokauden_alkuvuosi INTEGER        NOT NULL,
    tavoitehinta          NUMERIC(10, 2) NOT NULL,
    kattohinta            NUMERIC(10, 2),
    luotu                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                 INTEGER        NOT NULL,
    poistettu             BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja              INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id)
);

CREATE TABLE paatos_hoitokauden_indeksikorjaus
(
    id                               SERIAL PRIMARY KEY,
    urakkaid                         INTEGER        NOT NULL,
    hoitokauden_alkuvuosi            INTEGER        NOT NULL,
    tavoitehinta                     NUMERIC(10, 2) NOT NULL, -- Hoitokauden lopun indeksikorjattu tavoitehinta
    tavoitehinnan_muutokset          NUMERIC(10, 2) NOT NULL, -- Summa tavoitehinnan muutosten kokonaisuudesta
    tavoitehinta_ennen               NUMERIC(10, 2) NOT NULL, --Hoitokauden lopun tavoitehinta ennen hoitokauden lopun indeksikorjausta. Eli tavoitehinta + tavoitehinnan muutokset
    pistelukujen_muutos              INTEGER,
    indeksikorotuksen_prosentit      INTEGER,
    hoitokauden_lopun_indeksikorjaus NUMERIC(10, 2),
    luotu                            TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                            INTEGER        NOT NULL,
    poistettu                        BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                         INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id)
);

CREATE TABLE paatos_hoidonjohtopalkkio
(
    id                    SERIAL PRIMARY KEY,
    urakkaid              INTEGER        NOT NULL,
    hoitokauden_alkuvuosi INTEGER        NOT NULL,
    tavoitehinta_ennen    NUMERIC(10, 2) NOT NULL,
    hoidonjohtopalkkio    NUMERIC(10, 2), -- Tavoitehintaan vaikuttavien hoidonjohtopalkkioiden määrä
    tavoitehinta_jalkeen  NUMERIC(10, 2) NOT NULL,
    luotu                 TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                 INTEGER        NOT NULL,
    poistettu             BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja              INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id)
);
