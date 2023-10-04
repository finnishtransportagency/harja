-- name: luo-velho-nimikkeisto<!
INSERT INTO velho_nimikkeisto (versio, nimikkeisto, nimiavaruus, nimi, otsikko)
VALUES (:versio, :nimikkeisto, :nimiavaruus, :nimi, :otsikko) ON CONFLICT DO NOTHING;
