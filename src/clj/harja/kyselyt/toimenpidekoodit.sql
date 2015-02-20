-- name: hae-kaikki-toimenpidekoodit
-- Listaa kaikki toimenpidekoodit.
SELECT id,koodi,nimi,emo,taso FROM toimenpidekoodi WHERE poistettu=false;


-- name: lisaa-toimenpidekoodi<!
-- Lisää uuden 4. tason toimenpidekoodin (tehtäväkoodi).
INSERT INTO toimenpidekoodi (nimi,emo,taso,luoja,luotu,muokattu) VALUES (:nimi, :emo, 4, :kayttajaid, NOW(), NOW());

-- name: poista-toimenpidekoodi!
-- Poistaa (merkitsee poistetuksi) annetun toimenpidekoodin.
UPDATE toimenpidekoodi SET poistettu=true, muokkaaja=:kayttajaid, muokattu=NOW() WHERE id=:id

-- name: muokkaa-toimenpidekoodi!
-- Muokkaa annetun toimenpidekoodin nimen.
UPDATE toimenpidekoodi SET muokkaaja=:kayttajaid, muokattu=NOW(), nimi=:nimi WHERE id=:id

-- name: viimeisin-muokkauspvm
-- Antaa MAX(muokattu) päivämäärän toimenpidekoodeista
SELECT MAX(muokattu) as muokattu FROM toimenpidekoodi
