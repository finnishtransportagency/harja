-- haluamme vain yhden desimaalin talteen päällysterivin massamenekille
ALTER TABLE paikkaus RENAME COLUMN massamenekki TO _massamenekki;
ALTER TABLE paikkaus ADD massamenekki NUMERIC;
UPDATE paikkaus SET massamenekki = _massamenekki;
ALTER TABLE paikkaus DROP COLUMN _massamenekki;