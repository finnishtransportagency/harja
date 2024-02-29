CREATE TABLE lupausryhma_urakka 
(
    id SERIAL PRIMARY KEY,
    lupausryhma_id INTEGER REFERENCES lupausryhma(id) NOT NULL,
    urakka_id INTEGER REFERENCES urakka(id) NOT NULL,
    luotu TIMESTAMP NOT NULL DEFAULT NOW(),
    muokattu TIMESTAMP,
    UNIQUE (urakka_id, lupausryhma_id)
);

COMMENT ON TABLE lupausryhma_urakka IS 'Taulun avulla linkitetään lupausryhma tiettyyn urakkaan.'