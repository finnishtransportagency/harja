INSERT INTO urakkatyypin_indeksi(urakkatyyppi, indeksinimi, koodi, raakaaine)
VALUES
    ('paallystys'::urakkatyyppi, 'MAKU-Päällysteet osaindeksi (2015=100)', 'MAKU-PAALL-2015', NULL);

ALTER TABLE yllapitokohde ADD COLUMN yotyo BOOLEAN DEFAULT FALSE;
ALTER TABLE yllapitokohteen_kustannukset ADD COLUMN maku_paallysteet NUMERIC;
COMMENT ON COLUMN yllapitokohteen_kustannukset.maku_paallysteet IS E'1.1.2023 eteenpäin ylläpitokohteiden kustannuksiin vaikuttaa MAKU-päällysteet indeksi.';
