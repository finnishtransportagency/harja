ALTER TABLE tehtava
    DROP COLUMN koodi,
    DROP COLUMN taso,
    DROP COLUMN tuotenumero;

-- Poistetaan tässä funktiossa aiemmin ollut riippuvuus tehtava.taso kolumniin
CREATE OR REPLACE FUNCTION tarkista_t_tr_ti_yhteensopivuus(tehtava_ INTEGER, tehtavaryhma_ INTEGER, toimenpideinstanssi_ INTEGER)
    RETURNS boolean AS
$$
DECLARE
    kaikki_ok BOOLEAN;
BEGIN
    SELECT exists(SELECT 1
                  FROM toimenpide tk3
                           JOIN tehtava tk4 ON tk4.emo = tk3.id
                           JOIN toimenpideinstanssi ti ON tk3.id = ti.toimenpide
                           JOIN tehtavaryhma tr ON tk4.tehtavaryhma = tr.id
                  WHERE (tk4.id = tehtava_ OR tehtava_ IS NULL)
                    AND (tr.id = tehtavaryhma_ OR tehtavaryhma_ IS NULL)
                    AND (ti.id = toimenpideinstanssi_ OR toimenpideinstanssi_ IS NULL))
    INTO kaikki_ok;
    RETURN kaikki_ok;
END;
$$ LANGUAGE plpgsql;
