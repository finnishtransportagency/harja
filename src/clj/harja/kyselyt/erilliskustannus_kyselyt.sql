-- name: hae-erilliskustannus
SELECT id, sopimus, toimenpideinstanssi, pvm, rahasumma, indeksin_nimi, lisatieto, urakka, ulkoinen_id,
       tyyppi, kasittelytapa, laskutuskuukausi, luoja, luotu, muokkaaja, muokattu
  FROM erilliskustannus
 WHERE id = :id;

-- name: poista-erilliskustannus!
UPDATE erilliskustannus set poistettu = true, muokattu = NOW(), muokkaaja = :kayttaja-id WHERE id = :id;
