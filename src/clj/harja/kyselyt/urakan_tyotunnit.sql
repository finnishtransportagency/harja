-- name: hae-urakat-joilla-puuttuu-kolmanneksen-tunnit
-- Tarkistaa löytyykö toteumaa ulkoisella id:llä
SELECT u.sampoid
FROM urakka u
WHERE NOT exists(SELECT ut.id
                 FROM urakan_tyotunnit ut
                 WHERE ut.urakka = u.id AND
                       ut.vuosi = :vuosi AND
                       ut.vuosikolmannes = :vuosikolmannes);