-- Käytetään POT2:ssa sittenkin samaa paallystysilmoitus-taulua kuin POT1:ssä,
-- koska tietosisältö näyttää muodostuvan identtiseksi
-- Poistetaan tässä kohti siis taulu pot2, ja korvataan kaikki foreign keyt jotka siihen
-- viittasivat, uusille viittauksilla paallystysilmoitus-tauluun


-- Kulutuskerros
DROP INDEX pot2_paallystekerros_kohdeosa_idx;
ALTER TABLE pot2_paallystekerros DROP COLUMN pot2_id;
ALTER TABLE pot2_paallystekerros ADD COLUMN pot2_id INTEGER NOT NULL REFERENCES paallystysilmoitus (id);
CREATE INDEX pot2_paallystekerros_pot2_idx ON pot2_paallystekerros (pot2_id);

-- Alusta
DROP INDEX pot2_alusta_idx;
ALTER TABLE pot2_alusta DROP COLUMN pot2_id;
ALTER TABLE pot2_alusta ADD COLUMN pot2_id INTEGER NOT NULL REFERENCES paallystysilmoitus (id);
CREATE INDEX pot2_alusta_pot2_idx ON pot2_alusta (pot2_id);

DROP TABLE pot2;


-- POT2:n alikohteiden TR-tiedot pidetään yllä YLLÄPITOKOHDEOSA-taulussa, ei haluta niitä kahteen paikkaan
ALTER TABLE pot2_paallystekerros DROP COLUMN tr_numero;
ALTER TABLE pot2_paallystekerros DROP COLUMN tr_ajorata;
ALTER TABLE pot2_paallystekerros DROP COLUMN tr_kaista;
ALTER TABLE pot2_paallystekerros DROP COLUMN tr_alkuetaisyys;
ALTER TABLE pot2_paallystekerros DROP COLUMN tr_alkuosa;
ALTER TABLE pot2_paallystekerros DROP COLUMN tr_loppuosa;
ALTER TABLE pot2_paallystekerros DROP COLUMN tr_loppuetaisyys;
