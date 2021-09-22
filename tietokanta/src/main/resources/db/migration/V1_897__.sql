-- Nimi: Varustetoteuma2-taulun lisääminen
-- Kuvaus: Tierekisterin poistuessa Harja alkoi käyttää VelhoAPIa varustetoteumien lähteenä
-- Tämä taulu koostaa Velhon Varusterajapinnasta haettujen toteumien ja varusteiden tiedot.
--
-- Kaikkia tietoja ei haeta talteen Harjaan, vaan osa Varusteiden tiedoista saadaan jatkossakin
-- kyselemällä tarvittaessa Velhon rajapinnasta.

CREATE TABLE varustetoteuma2 (
                              id                       serial PRIMARY KEY,
                              velho_oid                varchar(128),
                              urakkakoodi              integer,
                              arvot                    varchar(4096),
                              karttapvm                date,
                              tr_numero                integer,
                              tr_alkuosa               integer,
                              tr_loppuosa              integer,
                              tr_alkuetaisyys          integer,
                              tr_loppuetaisyys         integer,
                              sijainti                 geometry,
                              tietolaji                varchar(128), -- tl506,tl501 jne.
                              toimenpide               varustetoteuma_tyyppi,
                              kuntoluokka              integer,
                              alkupvm                  date not null,
                              loppupvm                 date,
                              muokkaaja                text,
                              muokattu                 timestamp
);
