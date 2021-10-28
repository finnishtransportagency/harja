-- Nimi: Varustetoteuma2-taulun lisääminen
-- Kuvaus: Tierekisterin poistuessa Harja alkoi käyttää VelhoAPIa varustetoteumien lähteenä
-- Tämä taulu koostaa Velhon Varusterajapinnasta haettujen toteumien ja varusteiden tiedot.
--
-- Kaikkia tietoja ei haeta talteen Harjaan, vaan osa Varusteiden tiedoista saadaan jatkossakin
-- kyselemällä tarvittaessa Velhon rajapinnasta.

CREATE TABLE varustetoteuma2
(
    id               serial PRIMARY KEY    not null,
    velho_tunniste   varchar(128)          not null,
    urakka_id        integer               not null,
    arvot            varchar(4096),
    karttapvm        date,
    tr_numero        integer               not null,
    tr_alkuosa       integer               not null,
    tr_alkuetaisyys  integer               not null,
    tr_loppuosa      integer,
    tr_loppuetaisyys integer,
    sijainti         geometry              not null,
    tietolaji        varchar(128)          not null, -- tl506,tl501 jne.
    toimenpide       varustetoteuma_tyyppi not null,
    kuntoluokka      integer               not null,
    alkupvm          date                  not null,
    loppupvm         date,
    muokkaaja        text                  not null,
    muokattu         timestamp             not null
);

-- Lisätään varustehaun integraation tyyppi integraatio tauluun
INSERT INTO integraatio (jarjestelma, nimi)
VALUES ('velho', 'varustetoteumien-haku');