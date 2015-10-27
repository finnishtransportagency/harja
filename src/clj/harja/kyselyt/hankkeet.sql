-- name: luo-hanke<!
-- Luo uuden hankkeen.
INSERT INTO hanke (nimi, alkupvm, loppupvm, alueurakkanro, sampo_tyypit, sampoid)
VALUES (:nimi, :alkupvm, :loppupvm, :alueurakkanro, :sampo_tyypit, :sampoid);

-- name: paivita-hanke-samposta!
-- Paivittaa hankkeen Samposta saaduilla tiedoilla
UPDATE hanke
SET nimi = :nimi, alkupvm = :alkupvm, loppupvm = :loppupvm, alueurakkanro = :alueurakkanro, sampo_tyypit = :sampo_tyypit
WHERE sampoid = :sampoid;

-- name: onko-tuotu-samposta
-- Tarkistaa onko hanke jo tuotu Samposta
SELECT exists(
    SELECT hanke.id
    FROM hanke
    WHERE sampoid = :sampoid);

-- name:hae-sampo-tyypit
-- Hakee Sampo tyypit Sampo id:llä
SELECT sampo_tyypit
FROM hanke
WHERE sampoid = :sampoid;