INSERT INTO pot2_mk_alusta_toimenpide (nimi, lyhenne, koodi)
VALUES ('Kantavan kerroksen AB', 'ABK', 21),
       ('Sidekerroksen AB', 'ABS', 22);

UPDATE pot2_mk_alusta_toimenpide SET koodi = 3 WHERE koodi = 667;
UPDATE pot2_mk_alusta_toimenpide SET koodi = 4 WHERE koodi = 666;