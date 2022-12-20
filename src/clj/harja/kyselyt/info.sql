-- name: hae-koulutusvideot
-- Hakee kaikki järjestelmän koulutusvideot
SELECT
    id,
    otsikko,
    linkki,
    pvm
FROM koulutusvideot ORDER BY pvm DESC;

-- name: lisaa-video!
-- Lisää videolinkin järjestelmään
INSERT INTO 
koulutusvideot (otsikko, linkki, pvm) 
VALUES (:otsikko, :linkki, :pvm);

-- name: paivita-video!
-- Päivittää videon tiedot 
UPDATE koulutusvideot
SET 
    otsikko = :otsikko, 
    linkki = :linkki, 
    pvm = :pvm
WHERE id = :id;

-- name: poista-video!
-- Poistaa linkin videolistalta
DELETE FROM koulutusvideot WHERE id = :id;