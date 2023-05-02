-- osana tehtävien tietomallimuutosta, poistetaan tehtäväryhmistä turhat eli muut kuin alatason ryhmät
DELETE FROM tehtavaryhma WHERE tyyppi != 'alataso';
-- tämän myötä poistuu myös tehtäväryhmien (turha) hierarkia, ja viittaukset emoon
ALTER TABLE tehtavaryhma DROP column emo;

CREATE OR REPLACE FUNCTION kayttajan_id_kayttajanimella(knimi TEXT)
    RETURNS int LANGUAGE SQL AS $$
SELECT id FROM kayttaja WHERE kayttajanimi = knimi;
$$;
