-- name: hae-urakan-valitavoitteet
-- Hakee urakan kaikki välitavoitteet
SELECT id, nimi, takaraja, viikkosakko, sakko,
       valmis_pvm, valmis_kommentti, valmis_merkitsija, valmis_merkitty,
       luotu, muokattu, luoja, muokkaaja
  FROM valitavoite
 WHERE poistettu = false AND urakka = :urakka
 
