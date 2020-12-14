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