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
                           JOIN tehtavaryhma tr3 ON tk4.tehtavaryhma = tr3.id
                           JOIN tehtavaryhma tr2 ON tr2.id = tr3.emo
                           JOIN tehtavaryhma tr1 ON tr1.id = tr2.emo
                  WHERE (tk4.id = tehtava_ OR tehtava_ IS NULL)
                    AND tr1.emo IS NULL
                    AND tk4.taso = 4
                    AND (tr1.id = tehtavaryhma_ OR tr2.id = tehtavaryhma_ OR tr3.id = tehtavaryhma_ OR
                         tehtavaryhma_ IS NULL)
                    AND (ti.id = toimenpideinstanssi_ OR toimenpideinstanssi_ IS NULL))
    INTO kaikki_ok;
    RETURN kaikki_ok;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE toimenpidekoodi
    ALTER COLUMN tehtavaryhma TYPE INTEGER,
    ADD CONSTRAINT toimenpidekoodi_tehtavaryhma_fkey FOREIGN KEY (tehtavaryhma) REFERENCES tehtavaryhma (id);

ALTER TABLE lasku_kohdistus
    ADD CONSTRAINT lasku_kohdistus_tehtava_tehtavaryhma_toimenpideinstanssi_oikein CHECK (tarkista_t_tr_ti_yhteensopivuus(
            tehtava, tehtavaryhma, toimenpideinstanssi));