CREATE TABLE paatos_tavoitehinnan_pysyvamuutos
(
    id                       SERIAL PRIMARY KEY,
    urakkaid                 INTEGER        NOT NULL,
    hoitokauden_alkuvuosi    INTEGER        NOT NULL,
    rahavaraus_muutokset NUMERIC(12, 2),
    kirjalliset_muutokset    NUMERIC(12, 2),
    tehtava_muutokset        NUMERIC(12, 2),
    tavoitehinta_ennen       NUMERIC(12, 2) NOT NULL,
    tavoitehinta_jalkeen     NUMERIC(12, 2) NOT NULL,
    luotu                    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                    INTEGER        NOT NULL,
    poistettu                BOOLEAN        NOT NULL DEFAULT FALSE,
    poistaja                 INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);
