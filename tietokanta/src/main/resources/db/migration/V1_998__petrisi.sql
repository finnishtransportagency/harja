-- Lisätään varustehaun integraation tyyppi integraatio tauluun
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('velho', 'varustetoteumien-haku');

-- Nimi: Varustetoteuma2-taulun lisääminen
-- Kuvaus: Tierekisterin poistuessa Harja alkoi käyttää VelhoAPIa varustetoteumien lähteenä
-- Tämä taulu koostaa Velhon Varusterajapinnasta haettujen toteumien ja varusteiden tiedot.
--
-- Kaikkia tietoja ei haeta talteen Harjaan, vaan osa Varusteiden tiedoista saadaan jatkossakin
-- kyselemällä tarvittaessa Velhon rajapinnasta.

CREATE TABLE varustetoteuma2
(
    id               serial PRIMARY KEY    not null,
    velho_oid        varchar(128)          not null,
    urakka_id        integer               not null,
    tr_numero        integer               not null,
    tr_alkuosa       integer               not null,
    tr_alkuetaisyys  integer               not null,
    tr_loppuosa      integer,
    tr_loppuetaisyys integer,
    sijainti         geometry              not null,
    tietolaji        varchar(128)          not null, -- tl506,tl501 jne.
    lisatieto        varchar(128),
    toimenpide       varustetoteuma_tyyppi not null,
    kuntoluokka      integer               not null,
    alkupvm          date                  not null,
    loppupvm         date,
    muokkaaja        text                  not null,
    muokattu         timestamp             not null
);

CREATE UNIQUE INDEX varustetoteuma2_unique_velho_oid_muokattu ON varustetoteuma2 (velho_oid, muokattu);

CREATE TABLE varustetoteuma2_kohdevirhe
(
    id          serial PRIMARY KEY not null,
    velho_oid   varchar(128)       not null,
    muokattu    timestamp          not null,
    virhekuvaus text -- Vastaava teksti, kuin minkä Harja kirjoitti lokiin virheestä
);

CREATE TABLE varustetoteuma2_viimeksi_haettu
(
    viimeksi_haettu timestamp not null
);

INSERT INTO varustetoteuma2_viimeksi_haettu (viimeksi_haettu)
VALUES (date('2021-09-01T00:00:00Z'));
