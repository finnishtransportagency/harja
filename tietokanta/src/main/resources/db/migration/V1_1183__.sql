-- Muutetaan nimi kuvaamaan asiaa, ei järjestelmää mihin asia liittyy.
-- Kanavasiltaan jää
ALTER TABLE silta
    RENAME COLUMN trex_oid TO silta_oid;
