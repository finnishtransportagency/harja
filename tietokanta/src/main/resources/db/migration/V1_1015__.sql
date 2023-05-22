INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'kirjaa-tyomaapaivakirja');
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'paivita-tyomaapaivakirja');

CREATE TABLE tyomaapaivakirja (
    id          serial primary key, -- sisäinen ID, välitetään urakoitsijajärjestelmälle
    urakka_id   integer not null references urakka (id),
    ulkoinen_id text not null,-- urakoitsijajärjestelmät lähettävät omasta järjestelmästään id:n meille
    paivamaara  date not null,
    luotu       timestamp,
    muokattu    timestamp,
    luoja       integer references kayttaja (id),
    muokkaaja   integer references kayttaja (id)
);

COMMENT ON TABLE tyomaapaivakirja IS
    E'Urakoitsijajärjestelmät lähettävät jokaiselle päivälle yhden työmaapäiväkirjan, johon liittyy heidän järjestelmän id ja työmaapäiväkirjan päivä';

CREATE UNIQUE INDEX unique_index_tyomaapaivakirja
    ON tyomaapaivakirja (urakka_id, paivamaara);

CREATE TABLE tyomaapaivakirja_saa (
    id                              serial primary key,
    urakka_id                       integer not null references urakka (id),
    tyomaapaivakirja_id             integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
    versio                          integer not null,
    havaintoaika                    timestamp not null,
    aseman_tunniste                 text not null,
    aseman_tietojen_paivityshetki   timestamp not null,
    ilman_lampotila                 numeric not null,
    tien_lampotila                  numeric,
    keskituuli                      integer,
    sateen_olomuoto                 numeric,
    sadesumma                       integer
);

CREATE INDEX index_tyomaapaivakirja_saa
    ON tyomaapaivakirja_saa (urakka_id, tyomaapaivakirja_id, versio);

CREATE TABLE tyomaapaivakirja_poikkeussaa (
   id                   serial primary key,
   urakka_id            integer not null references urakka (id),
   versio               integer not null,
   tyomaapaivakirja_id integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
   havaintoaika         timestamp not null,
   paikka               text not null,
   kuvaus               text not null
);

CREATE INDEX index_tyomaapaivakirja_poikkeussaa
    ON tyomaapaivakirja_poikkeussaa (urakka_id, tyomaapaivakirja_id, versio);

CREATE TABLE tyomaapaivakirja_kalusto (
    id                      serial primary key,
    urakka_id               integer not null references urakka (id),
    tyomaapaivakirja_id    integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
    versio                  integer not null,
    aloitus                 timestamp not null,
    lopetus                 timestamp not null,
    tyokoneiden_lkm         integer not null,
    lisakaluston_lkm        integer not null
);

CREATE INDEX index_tyomaapaivakirja_kalusto
    ON tyomaapaivakirja_kalusto (urakka_id, tyomaapaivakirja_id, versio);

CREATE TABLE tyomaapaivakirja_paivystaja (
    id                       serial primary key,
    urakka_id               integer not null references urakka (id),
    tyomaapaivakirja_id    integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
    versio                  integer not null,
    aloitus                 timestamp not null,
    lopetus                 timestamp,
    nimi                    text not null
);

CREATE INDEX index_tyomaapaivakirja_paivystaja
    ON tyomaapaivakirja_paivystaja (urakka_id, tyomaapaivakirja_id, versio);

CREATE TABLE tyomaapaivakirja_tyonjohtaja (
    id                      serial primary key,
    urakka_id               integer not null references urakka (id),
    tyomaapaivakirja_id    integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
    versio                  integer not null,
    aloitus                 timestamp not null,
    lopetus                 timestamp,
    nimi                    text not null
);

CREATE INDEX index_tyomaapaivakirja_tyonjohtaja
    ON tyomaapaivakirja_tyonjohtaja (urakka_id, tyomaapaivakirja_id, versio);

CREATE TYPE tyomaapaivakirja_toimenpide_tyyppi AS ENUM ('yleinen', 'muu');

-- Yleiskuvaus tehtävistä toimenpiteistä, työnimikeittäin. Hanskataan työnimike tehtävä id:llä, joka saadaan
-- tällä  hetkellä toimenpidekoodi taulusta
CREATE TABLE tyomaapaivakirja_tieston_toimenpide (
    id                      serial primary key,
    urakka_id               integer not null references urakka (id),
    tyomaapaivakirja_id    integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
    versio                  integer not null,
    tyyppi                  tyomaapaivakirja_toimenpide_tyyppi,
    aloitus                 timestamp not null,
    lopetus                 timestamp,
    tehtavat                integer[],
    toimenpiteet            text[]
);

CREATE INDEX index_tyomaapaivakirja_tieston_toimenpide
    ON tyomaapaivakirja_tieston_toimenpide (urakka_id, tyomaapaivakirja_id, versio);

CREATE TABLE tyomaapaivakirja_kommentti (
    id                      serial primary key,
    urakka_id               integer not null references urakka (id),
    tyomaapaivakirja_id    integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
    versio                  integer not null,
    kommentti               text not null,
    tunnit                  numeric,
    luotu                   timestamp not null,
    luoja                   integer not null references kayttaja (id)
);

CREATE INDEX index_tyomaapaivakirja_kommentti
    ON tyomaapaivakirja_kommentti (urakka_id, tyomaapaivakirja_id, versio);

CREATE TYPE tyomaapaivakirja_tapahtumatyyppi AS ENUM ('onnettomuus', 'liikenteenohjausmuutos','viranomaisen_avustus', 'palaute', 'tilaajan-yhteydenotto','muut_kirjaukset');

CREATE TABLE tyomaapaivakirja_tapahtuma (
    id                      serial primary key,
    urakka_id               integer not null references urakka (id),
    tyomaapaivakirja_id    integer not null references tyomaapaivakirja (id) ON DELETE CASCADE,
    versio                  integer not null,
    tyyppi                  tyomaapaivakirja_tapahtumatyyppi not null,
    kuvaus                  text not null
);

CREATE INDEX index_tyomaapaivakirja_tapahtuma
    ON tyomaapaivakirja_tapahtuma (urakka_id, tyomaapaivakirja_id, versio);
