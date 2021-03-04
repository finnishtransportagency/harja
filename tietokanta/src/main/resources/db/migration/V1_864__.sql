ALTER TABLE pot2_mk_kulutuskerros_toimenpide RENAME TO pot2_mk_paallystekerros_toimenpide;

CREATE TABLE pot2_mk_sidotun_kantavan_kerroksen_sideaine
(
    koodi   INTEGER PRIMARY KEY,
    nimi    TEXT,
    lyhenne TEXT
);
-- tämä uutta Velhossa. Sovittava heidän kanssa, mitä lähetetään rajapintaan
INSERT INTO pot2_mk_sidotun_kantavan_kerroksen_sideaine (nimi, koodi)
VALUES
('(UUSIO) CEM I', 1),
('(UUSIO) CEM II/A-S', 2),
('(UUSIO) CEM II/B-S', 3),
('(UUSIO) CEM II/A-D', 4),
('(UUSIO) CEM II/A-V', 5),
('(UUSIO) CEM II/B-V', 6),
('(UUSIO) CEM II/A-L', 7),
('(UUSIO) CEM II/A-LL', 8),
('(UUSIO) CEM II/A-M', 9),
('(UUSIO) CEM III/A', 10),
('(UUSIO) CEM III/B', 11),
('Masuuni- tms kuona', 12);

ALTER TABLE yllapitokohde ADD COLUMN yha_tr_osoite tr_osoite;