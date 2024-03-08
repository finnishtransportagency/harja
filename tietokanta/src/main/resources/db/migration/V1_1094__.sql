CREATE TABLE rahavaraus
(
    id   SERIAL PRIMARY KEY,
    nimi TEXT NOT NULL
);

CREATE TABLE rahavaraus_tehtava
(
    rahavaraus INT REFERENCES rahavaraus (id),
    tehtava    INT REFERENCES tehtava (id),
    PRIMARY KEY (rahavaraus, tehtava)
);

CREATE TABLE rahavaraus_urakka
(
    rahavaraus INT REFERENCES rahavaraus (id),
    urakka     INT REFERENCES urakka (id),
    urakkakohtainen_nimi TEXT,
    PRIMARY KEY (rahavaraus, urakka)
);

ALTER TABLE kustannusarvioitu_tyo ADD COLUMN rahavaraus INT REFERENCES rahavaraus (id);

-- TODO: Populoi taulut

