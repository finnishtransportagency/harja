-- Tavoitehinnalle pysyvä muutos aiheuttaa niin isot muutokset, että tehdään sille oma taulu
CREATE TABLE paatos_tavoitehinnan_pysyva_muutos
(
    id                                 SERIAL PRIMARY KEY,
    urakkaid                           INTEGER   NOT NULL,
    hoitokauden_alkuvuosi              INTEGER   NOT NULL,
    kirjallisesti_sovitut_muutokset    NUMERIC(12, 2),
    pysyvat_muutokset                  NUMERIC(12, 2),
    johto_ja_hallintakorvaus_muutokset NUMERIC(12, 2),
    muutostyo_muutokset                NUMERIC(12, 2),
    toteumiin_perustuvat_muutokset     NUMERIC(12, 2),
    tehtava_ja_maaratoteumamuutokset   NUMERIC(12, 2),
    rahavarausten_muutokset            NUMERIC(12, 2),
    arvonvahennysten_muutokset         NUMERIC(12, 2),
    tavoitehinnan_muutokset_yhteensa   NUMERIC(12, 2),
    luotu                              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    luoja                              INTEGER   NOT NULL,
    poistettu                          BOOLEAN   NOT NULL DEFAULT FALSE,
    poistaja                           INTEGER,
    FOREIGN KEY (luoja) REFERENCES kayttaja (id),
    FOREIGN KEY (urakkaid) REFERENCES urakka (id)
);
