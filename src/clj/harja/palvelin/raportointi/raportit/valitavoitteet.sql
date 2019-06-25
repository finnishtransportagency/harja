-- name: hae-valitavoitteet
SELECT
nimi,
takaraja,
valmis_pvm AS "valmis-pvm",
valmis_kommentti AS "valmis-kommentti"
FROM valitavoite
WHERE urakka = :urakka
AND poistettu IS NOT TRUE;
