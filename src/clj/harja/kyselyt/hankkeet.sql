-- name: luo-hanke<!
-- Luo uuden hankkeen.
INSERT INTO hanke (nimi, alkupvm, loppupvm, alueurakkanro, sampoid)
VALUES (:nimi, :alkupvm, :loppupvm, :alueurakkanro, :sampoid);

-- name: paivita-hanke-samposta!
-- Paivittaa hankkeen Samposta saaduilla tiedoilla
UPDATE hanke
SET nimi = :nimi, alkupvm = :alkupvm, loppupvm = :loppupvm, alueurakkanro = :alueurakkanro
WHERE sampoid = :sampoid;

-- name: onko-tuotu-samposta
-- Tarkistaa onko hanke jo tuotu Samposta
SELECT exists(
    SELECT hanke.id
    FROM hanke
    WHERE sampoid = :sampoid);