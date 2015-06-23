-- name: hae-urakan-paallystyskohteet
-- Hakee urakan kaikki paallystyskohteet
SELECT * FROM paallystyskohde WHERE
urakka = :urakka
AND sopimus = :sopimus

-- name: hae-urakan-paallystystoteumat
-- Hakee urakan kaikki paallystystoteumat
SELECT tila FROM paallystysilmoitus
JOIN paallystyskohde pk ON pk.id = paallystysilmoitus.paallystyskohde
AND pk.urakka = :urakka
AND pk.sopimus = :sopimus;