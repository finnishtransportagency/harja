-- name: hae-valitavoitteet
SELECT nimi, takaraja, valmis_pvm, valmis_kommentti
FROM valitavoite
WHERE urakka = :urakka
AND poistettu IS NOT TRUE;