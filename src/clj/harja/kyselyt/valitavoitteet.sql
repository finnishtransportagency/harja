-- name: hae-urakan-valitavoitteet
-- Hakee urakan kaikki välitavoitteet
SELECT v.id, nimi, takaraja, viikkosakko, sakko,
       valmis_pvm, valmis_kommentti, valmis_merkitsija as valmis_merkitsija_id, valmis_merkitty,
       k.etunimi as valmis_merkitsija_etunimi, k.sukunimi as valmis_merkitsija_sukunimi,
       v.luotu, v.muokattu, v.luoja, v.muokkaaja
  FROM valitavoite v
       LEFT JOIN kayttaja k ON valmis_merkitsija=k.id
 WHERE v.poistettu = false AND urakka = :urakka
ORDER BY takaraja ASC;

-- name: hae-valtakunnalliset-valitavoitteet
-- Hakee kaikki valtakunnalliset välitavoitteet
SELECT
  id,
  nimi,
  takaraja,
  tyyppi,
  urakkatyyppi,
  takaraja_toistopaiva AS "takaraja-toistopaiva",
  takaraja_toistokuukausi AS "takaraja-toistokuukausi"
FROM valitavoite v
WHERE v.poistettu = false AND urakka IS NULL
ORDER BY takaraja ASC;

-- name: merkitse-valmiiksi!
-- Merkitsee välitavoitteen valmiiksi
UPDATE valitavoite
   SET valmis_pvm=:valmis, valmis_kommentti=:kommentti, valmis_merkitsija=:user, valmis_merkitty=NOW()
 WHERE urakka = :urakka AND id = :valitavoite AND poistettu = false;

-- name: poista-urakan-valitavoite!
-- Merkitsee välitavoitteen poistetuksi
UPDATE valitavoite
   SET poistettu = true, muokattu = NOW(), muokkaaja = :user
 WHERE urakka = :urakka AND id = :valitavoite;

-- name: lisaa-urakan-valitavoite<!
-- Lisää uuden välitavoitteen urakalle
INSERT
  INTO valitavoite
       (urakka,
        takaraja,
        valtakunnallinen_valitavoite,
        nimi,
        luoja,
        luotu)
VALUES (:urakka,
        :takaraja,
        :valtakunnallinen_valitavoite,
        :nimi,
        :luoja,
        NOW());

-- name: paivita-urakan-valitavoite!
-- Päivittää välitavoitteen tiedot
UPDATE valitavoite
   SET nimi = :nimi, takaraja = :takaraja, muokattu = NOW(), muokkaaja = :user
 WHERE urakka = :urakka AND id = :id;

-- name: lisaa-valtakunnallinen-kertaluontoinen-valitavoite<!
-- Lisää uuden valtakunnallisen välitavoitteen
INSERT
INTO valitavoite
(takaraja, urakkatyyppi, tyyppi, nimi, luoja, luotu)
VALUES (:takaraja,
        :urakkatyyppi::urakkatyyppi,
        :tyyppi::valitavoite_tyyppi,
        :nimi,
        :luoja,
        NOW());

-- name: lisaa-valtakunnallinen-toistuva-valitavoite<!
-- Lisää uuden valtakunnallisen välitavoitteen
INSERT
INTO valitavoite
(takaraja, urakkatyyppi, nimi, tyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, luoja, luotu)
VALUES (:takaraja,
        :urakkatyyppi::urakkatyyppi,
        :nimi,
        :tyyppi::valitavoite_tyyppi,
        :takaraja_toistopaiva,
        :takaraja_toistokuukausi,
        :luoja,
        NOW());

-- name: hae-urakat<!
-- Hakee kaikki urakat
SELECT
  id,
  nimi,
  alkupvm,
  loppupvm
FROM urakka;