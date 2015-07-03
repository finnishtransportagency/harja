-- name: luo-hanke<!
-- Luo uuden hankkeen perustiedoilla.
INSERT
INTO hanke (nimi, alkupvm, loppupvm, alueurakkanro, sampoid)
VALUES (:nimi, :alkupvm, :loppupvm, :alueurakkanro, :sampoid);
