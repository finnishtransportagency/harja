-- Talvisuolojen siirto havaittiin toimivaksi. Voidaaan poistaa reittipisteiden_kopio ja siirtofunktio.
ALTER TABLE toteuman_reittipisteet DROP COLUMN IF EXISTS reittipisteiden_kopio;

DROP FUNCTION IF EXISTS siirra_talvisuola_kelvilta(urakka_ INTEGER, alku TIMESTAMP, loppu TIMESTAMP);
