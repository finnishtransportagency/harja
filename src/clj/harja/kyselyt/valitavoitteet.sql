-- name: hae-urakan-valitavoitteet
-- Hakee urakan kaikki välitavoitteet
SELECT v.id, nimi, takaraja, viikkosakko, sakko,
       valmis_pvm, valmis_kommentti, valmis_merkitsija as valmis_merkitsija_id, valmis_merkitty,
       k.etunimi as valmis_merkitsija_etunimi, k.sukunimi as valmis_merkitsija_sukunimi,
       v.luotu, v.muokattu, v.luoja, v.muokkaaja
  FROM valitavoite v
       LEFT JOIN kayttaja k ON valmis_merkitsija=k.id
 WHERE v.poistettu = false AND urakka = :urakka
ORDER BY takaraja ASC

-- name: merkitse-valmiiksi!
-- Merkitsee välitavoitteen valmiiksi
UPDATE valitavoite
   SET valmis_pvm=:valmis, valmis_kommentti=:kommentti, valmis_merkitsija=:user, valmis_merkitty=NOW()
 WHERE urakka = :urakka AND id = :valitavoite AND poistettu = false


-- name: poista-valitavoite!
-- Merkitsee välitavoitteen poistetuksi
UPDATE valitavoite
   SET poistettu = true, muokattu = NOW(), muokkaaja = :user
 WHERE urakka = :urakka AND id = :valitavoite

-- name: lisaa-valitavoite<!
-- Lisää uuden välitavoitteen urakalle
INSERT
  INTO valitavoite
       (urakka,takaraja,nimi,luoja,luotu)
VALUES (?, ?, ?, ?, NOW())

-- name: paivita-valitavoite!
-- Päivittää välitavoitteen tiedot
UPDATE valitavoite
   SET nimi = :nimi, takaraja = :takaraja, muokattu = NOW(), muokkaaja = :user
 WHERE urakka = :urakka AND id = :id
 
 
