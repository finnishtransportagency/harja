-- Huomioidaan yksilöivissä indekseissä myös null-arvot

ALTER TABLE kiinteahintainen_tyo
    DROP CONSTRAINT uniikki_kiinteahintainen_tyo,
    ADD CONSTRAINT uniikki_kiinteahintainen_tyo UNIQUE NULLS NOT DISTINCT (toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, vuosi, kuukausi, versio);

ALTER TABLE kustannusarvioitu_tyo
    DROP CONSTRAINT uniikki_kustannusarvioitu_tyo,
    ADD CONSTRAINT uniikki_kustannusarvioitu_tyo UNIQUE NULLS NOT DISTINCT (toimenpideinstanssi, tehtavaryhma, tehtava, rahavaraus_id, sopimus, vuosi, kuukausi, osio, versio);

