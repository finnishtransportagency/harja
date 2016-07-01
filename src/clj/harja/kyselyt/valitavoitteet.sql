-- name: hae-urakan-valitavoitteet
-- Hakee urakan kaikki välitavoitteet
SELECT
  v.id,
  v.nimi,
  v.takaraja,
  v.viikkosakko,
  v.sakko,
  v.urakka as "urakka-id",
  v.valtakunnallinen_valitavoite as "valtakunnallinen-id",
  vv.nimi as "valtakunnallinen-nimi",
  vv.takaraja as "valtakunnallinen-takaraja",
  v.valmis_pvm,
  v.valmis_kommentti,
  v.valmis_merkitsija AS valmis_merkitsija_id,
  v.valmis_merkitty,
  k.etunimi         AS valmis_merkitsija_etunimi,
  k.sukunimi        AS valmis_merkitsija_sukunimi,
  v.luotu,
  v.muokattu,
  v.luoja,
  v.muokkaaja
FROM valitavoite v
  LEFT JOIN valitavoite vv ON v.valtakunnallinen_valitavoite = vv.id
  LEFT JOIN kayttaja k ON v.valmis_merkitsija = k.id
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

-- name: hae-kaynnissa-olevat-urakat
-- Hakee kaikki urakat
SELECT
  id,
  nimi,
  alkupvm,
  loppupvm
FROM urakka
WHERE loppupvm >= NOW();