CREATE TYPE velho_lahetyksen_tila_tyyppi AS ENUM (
    'ei-lahetetty', -- default
    'lahetyspalvelu', -- lähetetty lähetyspalveluun mutta ei vielä muuta tietoa saatavilla (ehkä 5-15min kestoltaan)
    'epaonnistunut',
    'osittain-onnistunut', -- (vähintään 1 rivi päällyste- tai alustarivi epäonnistunut ja vähintään 1 rivi onnistunut)
    'valmis');

CREATE TYPE velho_rivi_lahetyksen_tila_tyyppi AS ENUM (
    'ei-lahetetty',
    'epaonnistunut', -- (tässä tapauksessa tallennettaa virhe omaan sarakkeeseen)
    'onnistunut'); -- (-> virhe sarake asetettava arvoon NIL)

ALTER TABLE yllapitokohde ADD COLUMN velho_lahetyksen_aika TIMESTAMP;
ALTER TABLE yllapitokohde ADD COLUMN velho_lahetyksen_tila velho_lahetyksen_tila_tyyppi NOT NULL DEFAULT 'ei-lahetetty';
ALTER TABLE yllapitokohde ADD COLUMN velho_lahetyksen_vastaus VARCHAR;

ALTER TABLE pot2_paallystekerros ADD COLUMN velho_lahetyksen_aika TIMESTAMP;
ALTER TABLE pot2_paallystekerros ADD COLUMN velho_rivi_lahetyksen_tila velho_rivi_lahetyksen_tila_tyyppi NOT NULL DEFAULT 'ei-lahetetty';
ALTER TABLE pot2_paallystekerros ADD COLUMN velho_lahetyksen_vastaus VARCHAR;

ALTER TABLE pot2_alusta ADD COLUMN velho_lahetyksen_aika TIMESTAMP;
ALTER TABLE pot2_alusta ADD COLUMN velho_rivi_lahetyksen_tila velho_rivi_lahetyksen_tila_tyyppi NOT NULL DEFAULT 'ei-lahetetty';
ALTER TABLE pot2_alusta ADD COLUMN velho_lahetyksen_vastaus VARCHAR;


