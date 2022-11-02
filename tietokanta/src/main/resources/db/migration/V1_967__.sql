-- Tallennetaan komponenttien statukset muutamaksi kuukaudeksi
CREATE TYPE komponentti_status_tyyppi AS ENUM ('ok','nok','hidas','ei-kaytossa');
CREATE TABLE komponenttien_status (
id serial primary key,
palvelin text,
komponentti varchar(50),
status komponentti_status_tyyppi,
lisatiedot text,
luotu timestamp
);

CREATE INDEX komponenttien_status_index
    ON komponenttien_status (palvelin, komponentti, status);
CREATE INDEX komponenttien_status_luotu_index
    ON komponenttien_status (luotu);
