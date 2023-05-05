-- osana tehtävien tietomallimuutosta, poistetaan tehtäväryhmistä turhat eli muut kuin alatason ryhmät
-- jotta tehtäväryhmiä voi poistaa, on kaikki foreign key -yhteydet ensin poistettava
UPDATE tehtavaryhma set emo = NULL;
DELETE FROM tehtavaryhma WHERE tyyppi != 'alataso';
-- kun jäljellä on enää alatason tyyppejä, voidaan poistaa koko sarake turhana
ALTER TABLE tehtavaryhma DROP column tyyppi;
-- tämän myötä poistuu myös tehtäväryhmien (turha) hierarkia, ja viittaukset emoon
ALTER TABLE tehtavaryhma DROP column emo;


CREATE OR REPLACE FUNCTION kayttajan_id_kayttajanimella(knimi TEXT)
    RETURNS int LANGUAGE SQL AS $$
SELECT id FROM kayttaja WHERE kayttajanimi = knimi;
$$;

CREATE OR REPLACE FUNCTION tarkista_t_tr_ti_yhteensopivuus(tehtava_ INTEGER, tehtavaryhma_ INTEGER, toimenpideinstanssi_ INTEGER)
    RETURNS boolean AS
$$
DECLARE
    kaikki_ok BOOLEAN;
BEGIN
    SELECT exists(SELECT 1
                    FROM toimenpidekoodi tk3
                             JOIN toimenpidekoodi tk4 ON tk4.emo = tk3.id
                             JOIN toimenpideinstanssi ti ON tk3.id = ti.toimenpide
                             JOIN tehtavaryhma tr ON tk4.tehtavaryhma = tr.id
                   WHERE (tk4.id = tehtava_ OR tehtava_ IS NULL)
                     AND tk4.taso = 4
                     AND (tr.id = tehtavaryhma_ OR tehtavaryhma_ IS NULL)
                     AND (ti.id = toimenpideinstanssi_ OR toimenpideinstanssi_ IS NULL))
      INTO kaikki_ok;
    RETURN kaikki_ok;
END;
$$ LANGUAGE plpgsql;
