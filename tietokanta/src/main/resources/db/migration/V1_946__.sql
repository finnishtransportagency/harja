CREATE TABLE analytiikka_toteumat (
    toteuma_tunniste_id            serial primary key,
    toteuma_sopimus_id             integer,
    toteuma_alkanut                timestamp,
    toteuma_paattynyt              timestamp,
    toteuma_alueurakkanumero       varchar(16),
    toteuma_suorittaja_ytunnus     varchar(256),
    toteuma_suorittaja_nimi        varchar(256),
    toteuma_toteumatyyppi          toteumatyyppi,
    toteuma_lisatieto              text,
    toteumatehtavat                json, -- Nopeuttaa hakua, mutta sisältää paljon dataa
    toteumamateriaalit             json, -- Nopeuttaa hakua, mutta sisältää paljon dataa
    toteuma_tiesijainti_numero     integer,
    toteuma_tiesijainti_aosa       integer,
    toteuma_tiesijainti_aet        integer,
    toteuma_tiesijainti_losa       integer,
    toteuma_tiesijainti_let        integer,
    toteuma_muutostiedot_luotu     timestamp,
    toteuma_muutostiedot_luoja     integer,
    toteuma_muutostiedot_muokattu  timestamp,
    toteuma_muutostiedot_muokkaaja integer,
    tyokone_tyokonetyyppi          text,
    tyokone_tunnus                 text,
    urakkaid                       integer,
    poistettu                      boolean default false,
    -- Foreign key urakka -tauluun
    CONSTRAINT analytiikka_toteumat_fk_urakka
        FOREIGN KEY (urakkaid)
            REFERENCES urakka (id),
    -- Foreign key sopimus -tauluun
    CONSTRAINT analytiikka_toteumat_fk_sopimus
        FOREIGN KEY (toteuma_sopimus_id)
            REFERENCES sopimus (id)
);

create index analytiikka_toteumat_toteuma_alkanut_toteuma_paattynyt_index
    on analytiikka_toteumat (toteuma_alkanut, toteuma_paattynyt);

