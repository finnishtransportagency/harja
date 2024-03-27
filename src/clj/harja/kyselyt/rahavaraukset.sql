-- name: hae-urakoiden-rahavaraukset

SELECT * FROM urakka u
LEFT JOIN rahavaraus_urakka rvu ON rvu.urakka_id = u.id
left join rahavaraus rv on rv.id = rvu.rahavaraus_id
where u.tyyppi = 'teiden-hoito'




