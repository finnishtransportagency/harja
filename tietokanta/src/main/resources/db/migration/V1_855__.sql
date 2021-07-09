-- Materiaalikirjaston tauluille oma etulitte pot2_km
ALTER TABLE pot2_alusta_toimenpide RENAME TO pot2_mk_alusta_toimenpide;
ALTER TABLE pot2_kulutuskerros_toimenpide RENAME TO pot2_mk_kulutuskerros_toimenpide;
ALTER TABLE pot2_lisaainetyyppi RENAME TO pot2_mk_lisaainetyyppi;
ALTER TABLE pot2_massa RENAME TO pot2_mk_urakan_massa;
ALTER TABLE pot2_massa_lisaaine RENAME TO pot2_mk_massan_lisaaine;
ALTER TABLE pot2_massa_runkoaine RENAME TO pot2_mk_massan_runkoaine;
ALTER TABLE pot2_massa_sideaine RENAME TO pot2_mk_massan_sideaine;
ALTER TABLE pot2_massatyyppi RENAME TO pot2_mk_massatyyppi;
ALTER TABLE pot2_murske RENAME TO pot2_mk_urakan_murske;
ALTER TABLE pot2_mursketyyppi RENAME TO pot2_mk_mursketyyppi;
ALTER TABLE pot2_runkoainetyyppi RENAME TO pot2_mk_runkoainetyyppi;
ALTER TABLE pot2_sideainetyyppi RENAME TO pot2_mk_sideainetyyppi;

-- pot2_päällystekerroksen järjestysnumero. 1 = kulutuskerros, 2 = 1. alempi päällystekerros...
ALTER TABLE pot2_paallystekerros ADD COLUMN jarjestysnro INTEGER NOT NULL DEFAULT 1;
ALTER TABLE pot2_mk_urakan_murske DROP COLUMN nimi;
ALTER TABLE pot2_mk_urakan_murske ADD COLUMN rakeisuus_tarkenne TEXT;
ALTER TABLE pot2_mk_urakan_murske ADD COLUMN tyyppi_tarkenne TEXT;
-- tietyillä murskeilla on kiviainesesiintymän sijaan lähde, esiintymä ei siis olekaan pakollinen
ALTER TABLE pot2_mk_urakan_murske ALTER esiintyma DROP NOT NULL;
ALTER TABLE pot2_mk_urakan_murske ADD COLUMN lahde TEXT;
UPDATE pot2_mk_mursketyyppi set koodi = 6 where nimi = 'Muu';
INSERT INTO pot2_mk_mursketyyppi (nimi, lyhenne, koodi)
VALUES
('(UUSIO) Betonimurske I', 'BeM I',  4),
('(UUSIO) Betonimurske II', 'BeM II', 5);

UPDATE pot2_mk_mursketyyppi
   SET nimi = '(UUSIO) RA, Asfalttirouhe',
       lyhenne = 'RA'
 WHERE nimi = '(Uusio) RA, Asfalttirouhe';
UPDATE pot2_mk_mursketyyppi
   SET lyhenne = 'SrM'
 WHERE nimi = 'Soramurske';

ALTER TABLE pot2_alusta ADD COLUMN poistettu BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE pot2_alusta DROP CONSTRAINT pot2_alusta_tr_ajorata_check;
ALTER TABLE pot2_alusta ADD CONSTRAINT pot2_alusta_tr_ajorata_check CHECK (tr_ajorata >= 0);
