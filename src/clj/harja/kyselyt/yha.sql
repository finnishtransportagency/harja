-- name: poista-urakan-yha-tiedot
-- Poistaa urakan yha-tiedot
DELETE FROM yhatiedot WHERE urakka = :urakka;

-- name: lisaa-urakalle-yha-tiedot
-- Lisää urakalle YHA-tiedot
INSERT INTO yhatiedot (urakka, yhatunnus, yhaid, yhanimi, elyt, vuodet, kohdeluettelo_paivitetty, luotu, linkittaja, muokattu)
    VALUES (:urakka, :yhatunnus, :yhaid, :yhanimi, :elyt, :vuodet, null, NOW(), :kayttaja, NOW());

-- name: paivita-yhatietojen-kohdeluettelon-paivitysaika
-- Päivittää urakan YHA-tietoihin kohdeluettelon uudeksi päivitysajaksi nykyhetken
UPDATE yhatiedot SET kohdeluettelo_paivitetty = NOW()
WHERE urakka = :urakka;