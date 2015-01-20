-- name: hae-kaikki-toimenpidekoodit
-- Listaa kaikki toimenpidekoodit.
SELECT id,koodi,nimi,emo,taso FROM toimenpidekoodi;


-- name: lisaa-toimenpidekoodi<!
-- Lisää uuden 4. tason toimenpidekoodin (tehtäväkoodi).
INSERT INTO toimenpidekoodi (nimi,emo,taso,luoja,luotu) VALUES (:nimi, :emo, 4, :kayttajaid, NOW());

-- name: poista-toimenpidekoodi!
-- Poistaa (merkitsee poistetuksi) annetun toimenpidekoodin.
UPDATE toimenpidekoodi SET poistettu=true, muokkaaja=:kayttajaid, muokattu=NOW() WHERE id=:id

-- name: muokkaa-toimenpidekoodi!
-- Muokkaa annetun toimenpidekoodin nimen.
UPDATE toimenpidekoodi SET muokkaaja=:kayttajaid, muokattu=NOW(), nimi=:nimi WHERE id=:id
