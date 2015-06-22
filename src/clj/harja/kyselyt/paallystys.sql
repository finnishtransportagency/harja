-- name: hae-urakan-paallystyskohteet
-- Hakee urakan kaikki paallystyskohteet
SELECT * FROM paallystyskohde WHERE
urakka = :urakka
AND sopimus = :sopimus
