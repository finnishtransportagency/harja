-- name: hae-urakan-valitavoitteet
-- Hakee urakan kaikki välitavoitteet
SELECT id, nimi, takaraja, viikkosakko, sakko,
       valmis_pvm, valmis_kommentti, valmis_merkitsija, valmis_merkitty,
       luotu, muokattu, luoja, muokkaaja
  FROM valitavoite
 WHERE poistettu = false AND urakka = :urakka
ORDER BY takaraja ASC

-- name: merkitse-valmiiksi!
-- Merkitsee välitavoitteen valmiiksi
UPDATE valitavoite
   SET valmis_pvm=:valmis, valmis_kommentti=:kommentti, valmis_merkitsija=:user, valmis_merkitty=NOW()
 WHERE urakka = :urakka AND id = :valitavoite AND poistettu = false
 
 
