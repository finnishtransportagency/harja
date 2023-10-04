-- name: luo-velho-nimikkeisto<!
INSERT INTO velho_nimikkeisto (versio, nimikkeisto, nimiavaruus, nimi, otsikko)
VALUES (:versio, :nimikkeisto, :nimiavaruus, :nimi, :otsikko) ON CONFLICT DO NOTHING;

-- name: hae-nimikkeen-otsikko
-- single?: true
SELECT otsikko FROM velho_nimikkeisto
WHERE :nimiavaruus_nimike = CONCAT(nimiavaruus, '/', nimi);

