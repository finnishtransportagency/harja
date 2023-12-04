-- name: luo-velho-nimikkeisto<!
INSERT INTO velho_nimikkeisto (versio, kohdeluokka, tyyppi_avain, nimiavaruus, nimi, otsikko)
VALUES (:versio, :kohdeluokka, :tyyppi-avain, :nimiavaruus, :nimi, :otsikko) ON CONFLICT DO NOTHING;

-- name: hae-nimikkeen-tiedot
SELECT * FROM velho_nimikkeisto
WHERE :tyyppi-nimi = CONCAT(tyyppi_avain, '/', nimi);

--name: hae-nimike-otsikolla
--single?: true
SELECT nimi from velho_nimikkeisto
WHERE tyyppi_avain = 'varustetoimenpide' AND otsikko = :otsikko;

--name: hae-muut-varustetoimenpide-nimikkeet
SELECT nimi, nimiavaruus FROM velho_nimikkeisto
WHERE tyyppi_avain = 'varustetoimenpide' AND otsikko NOT IN ('Korjaus', 'Tarkastettu', 'Puhdistaminen');

-- name: hae-nimikkeistot
SELECT * FROM velho_nimikkeisto;
