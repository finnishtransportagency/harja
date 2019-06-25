-- name: hae-urakan-valitavoitteet
-- Hakee urakan kaikki välitavoitteet (urakkakohtaiset ja valtakunnalliset)
SELECT
  v.id,
  v.nimi,
  v.takaraja,
  v.viikkosakko,
  v.sakko,
  v.urakka as "urakka-id",
  v.valtakunnallinen_valitavoite as "valtakunnallinen-id",
  v.aloituspvm,
  v.valmis_pvm as "valmispvm",
  v.valmis_kommentti "valmis-kommentti",
  v.valmis_merkitsija as "valmis-merkitsija",
  v.valmis_merkitty as "valmis-merkitty",
  v.luotu,
  v.muokattu,
  v.luoja,
  v.muokkaaja,
  v.yllapitokohde as "yllapitokohde-id",
  vv.nimi as "valtakunnallinen-nimi",
  vv.takaraja as "valtakunnallinen-takaraja",
  vv.takaraja_toistopaiva as "valtakunnallinen-takarajan-toistopaiva",
  vv.takaraja_toistokuukausi as "valtakunnallinen-takarajan-toistokuukausi",
  merkitsija.etunimi as "valmis-merkitsija-etunimi",
  merkitsija.sukunimi as "valmis-merkitsija-sukunimi"
  FROM valitavoite v
  LEFT JOIN valitavoite vv ON v.valtakunnallinen_valitavoite = vv.id
  LEFT JOIN kayttaja merkitsija ON v.valmis_merkitsija = merkitsija.id
WHERE v.poistettu = FALSE
      AND v.urakka = :urakka
ORDER BY v.takaraja ASC;

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
   SET valmis_pvm=:valmis,
     valmis_kommentti=:kommentti,
     valmis_merkitsija=:user,
     valmis_merkitty=NOW(),
     muokattu = NOW()
 WHERE urakka = :urakka AND id = :valitavoite AND poistettu = false;

-- name: poista-urakan-valitavoite!
-- Merkitsee urakan välitavoitteen poistetuksi
UPDATE valitavoite
   SET poistettu = true,
     muokattu = NOW(),
     muokkaaja = :user
 WHERE urakka = :urakka AND id = :valitavoite;

-- name: lisaa-urakan-valitavoite<!
-- Lisää uuden välitavoitteen urakalle
INSERT
  INTO valitavoite
  (urakka,
   aloituspvm,
   takaraja,
   valtakunnallinen_valitavoite,
   nimi,
   yllapitokohde,
   luoja,
   luotu)
  VALUES (:urakka,
          :aloituspvm,
          :takaraja,
          :valtakunnallinen_valitavoite,
          :nimi,
          :yllapitokohde,
          :luoja,
          NOW());

-- name: paivita-urakan-valitavoite!
-- Päivittää urakan välitavoitteen tiedot
UPDATE valitavoite
   SET nimi = :nimi,
     takaraja = :takaraja,
     aloituspvm = :aloituspvm,
     yllapitokohde = :yllapitokohde,
     muokattu = NOW(),
     muokkaaja = :muokkaaja
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
(urakkatyyppi, nimi, tyyppi, takaraja_toistopaiva, takaraja_toistokuukausi, luoja, luotu)
VALUES (:urakkatyyppi::urakkatyyppi,
        :nimi,
        :tyyppi::valitavoite_tyyppi,
        :takaraja_toistopaiva,
        :takaraja_toistokuukausi,
        :luoja,
        NOW());

-- name: hae-kaynnissa-olevat-urakat
-- Hakee kaikki urakat
SELECT
  id,
  nimi,
  alkupvm,
  loppupvm
FROM urakka
WHERE loppupvm >= current_date;

-- name: paivita-valtakunnallinen-valitavoite!
-- Päivittää valtakunnallisen välitavoitteen tiedot
UPDATE valitavoite
SET nimi = :nimi,
  takaraja = :takaraja,
  takaraja_toistopaiva = :takaraja_toistopaiva,
  takaraja_toistokuukausi = :takaraja_toistokuukausi,
  muokattu = NOW(),
  muokkaaja = :user
WHERE id = :id
      AND urakka IS NULL;

-- name: poista-valtakunnallinen-valitavoite!
-- Merkitsee valtakunnallisen välitavoitteen poistetuksi
UPDATE valitavoite
SET poistettu = true,
  muokattu = NOW(),
  muokkaaja = :user
WHERE id = :id
      AND urakka IS NULL;

-- name: hae-valitavoitteeseen-linkitetyt-valitavoitteet
SELECT
  v.id,
  v.nimi,
  v.valmis_pvm as "valmispvm",
  u.id as urakka_id,
  u.nimi as urakka_nimi,
  u.alkupvm as urakka_alkupvm,
  u.loppupvm as urakka_loppupvm
FROM valitavoite v
  JOIN urakka u ON v.urakka = u.id
WHERE v.poistettu = FALSE
      AND v.valtakunnallinen_valitavoite = :id
ORDER BY v.takaraja ASC;

-- name: hae-valitavoitteeseen-linkitetyt-valitavoitteet-urakassa
SELECT
  v.id,
  v.nimi,
  v.valmis_pvm as "valmispvm",
  u.id as urakka_id,
  u.nimi as urakka_nimi,
  u.alkupvm as urakka_alkupvm,
  u.loppupvm as urakka_loppupvm
FROM valitavoite v
  JOIN urakka u ON v.urakka = u.id
WHERE v.poistettu = FALSE
      AND v.valtakunnallinen_valitavoite = :id
      AND urakka = :urakkaid
ORDER BY v.takaraja ASC;

-- name: paivita-kertaluontoiseen-valitavoitteeseen-linkitetty-muokkaamaton-valitavoite!
UPDATE valitavoite
SET nimi = :nimi,
  takaraja = :takaraja
  -- Ei päivitetä muokkaustietoja, koska niitä käytetään tutkimaan onko käyttäjä muokannut tätä urakassa
WHERE valtakunnallinen_valitavoite = :id
AND muokattu IS NULL;

-- name: poista-toistuvaan-valitavoitteeseen-linkitetty-muokkaamaton-valitavoite!
DELETE FROM valitavoite
WHERE valtakunnallinen_valitavoite = :id
      AND valmis_pvm IS NULL
      AND muokattu IS NULL;
