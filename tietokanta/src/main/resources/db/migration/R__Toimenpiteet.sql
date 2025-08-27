-- Kustannussuunnitelmassa ja mahdollisesti monessa muussakin osiossa tarvitaan tieto Hankintoihin liittyvistä toimenpiteistä.
-- Toimenpiteitä on valtavasti. Tähän on listattu kaikki hankintoihin liittyvät toimenpiteet.
CREATE OR REPLACE FUNCTION onko_mhu_hankintatoimenpide(toimenpidekoodi TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    onko_mhu_hankintatoimenpide BOOLEAN;
BEGIN
    SELECT EXISTS(
        SELECT t.id
          FROM toimenpide t
         WHERE t.koodi = toimenpidekoodi
           AND t.koodi IN ('23104', -- talvihoito
                           '23116', -- liikenneympariston-hoito
                           '23124', -- sorateiden-hoito
                           '20107', -- paallystepaikkaukset
                           '20191', -- mhu-yllapito
                           '14301' -- mhu-korvausinvestointi
                           )
        ) INTO onko_mhu_hankintatoimenpide;

    RETURN onko_mhu_hankintatoimenpide;
END;
$$ LANGUAGE plpgsql;



