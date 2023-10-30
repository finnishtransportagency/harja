-- name: luo-velho-nimikkeisto<!
INSERT INTO velho_nimikkeisto (versio, kohdeluokka, tyyppi_avain, nimiavaruus, nimi, otsikko)
VALUES (:versio, :kohdeluokka, :tyyppi-avain, :nimiavaruus, :nimi, :otsikko) ON CONFLICT DO NOTHING;

-- name: hae-nimikkeen-tiedot
SELECT * FROM velho_nimikkeisto
WHERE :tyyppi-nimi = CONCAT(tyyppi_avain, '/', nimi);

-- name: hae-nimikkeistot
SELECT * FROM velho_nimikkeisto;
