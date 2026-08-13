-- name: hae-valitavoitteet
SELECT
v.nimi,
v.takaraja,
v.valmis_pvm AS "valmispvm",
v.aloituspvm,
v.valmis_kommentti AS "valmis-kommentti",
v.valtakunnallinen_valitavoite as "valtakunnallinen-id",
v.valmis_merkitsija as "valmis-merkitsija",
vv.nimi as "valtakunnallinen-nimi",
vv.takaraja as "valtakunnallinen-takaraja",
vv.takaraja_toistopaiva as "valtakunnallinen-takarajan-toistopaiva",
vv.takaraja_toistokuukausi as "valtakunnallinen-takarajan-toistokuukausi",
merkitsija.etunimi as "valmis-merkitsija-etunimi",
merkitsija.sukunimi as "valmis-merkitsija-sukunimi"
FROM valitavoite v
         LEFT JOIN valitavoite vv ON v.valtakunnallinen_valitavoite = vv.id
         LEFT JOIN kayttaja merkitsija ON v.valmis_merkitsija = merkitsija.id
WHERE v.urakka = :urakka
  AND v.takaraja BETWEEN :alkupvm::DATE AND :loppupvm::DATE
  AND v.poistettu IS NOT TRUE
ORDER BY v.takaraja ASC;
