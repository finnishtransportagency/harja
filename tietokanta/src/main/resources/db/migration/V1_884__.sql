-- haluamme vain yhden desimaalin talteen päällysterivin massamenekille
ALTER TABLE pot2_paallystekerros RENAME COLUMN massamenekki TO _massamenekki;
ALTER TABLE pot2_paallystekerros ADD massamenekki NUMERIC(10, 1);
UPDATE pot2_paallystekerros SET massamenekki = _massamenekki;
ALTER TABLE pot2_paallystekerros DROP COLUMN _massamenekki;