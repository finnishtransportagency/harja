-- Luodaan toimenpiteille uusi taulu, minne jätetään entisen toimenpidekoodi-taulun tason 4 rivit
CREATE TABLE toimenpide (
    id                     SERIAL PRIMARY KEY,
    nimi                   VARCHAR(255) CHECK (nimi NOT LIKE '%^%'),
    koodi                  VARCHAR(16) UNIQUE,
    emo                    INTEGER REFERENCES toimenpide (id),  -- ylemmän tason toimenpidekoodi (tai NULL jos tämän taso on 1.),
    taso                   SMALLINT CHECK (taso > 0 AND taso < 4),
    luotu                  TIMESTAMP,
    muokattu               TIMESTAMP,
    luoja                  INTEGER REFERENCES kayttaja(id),
    muokkaaja              INTEGER REFERENCES kayttaja(id),
    poistettu              BOOLEAN DEFAULT FALSE,
    tuotenumero            INTEGER,
    piilota                BOOLEAN
);
CREATE INDEX toimenpide_taso ON toimenpide (taso);
CREATE INDEX toimenpide_emo ON toimenpide (emo);
CREATE INDEX toimenpide_koodi ON toimenpide (koodi);

-- Siirretänä tasojen 1, 2 ja 3 toimenpidekoodit toimenpide-tauluun
INSERT INTO toimenpide (id, nimi, koodi, emo, taso, luotu, muokattu, luoja, muokkaaja, poistettu, tuotenumero, piilota)
    (SELECT id, nimi, koodi, emo, taso, luotu, muokattu, luoja, muokkaaja, poistettu, tuotenumero, piilota
       FROM toimenpidekoodi
      WHERE taso < 4);
-- asetellaan PK oikeaan kohtaan
SELECT setval('toimenpide_id_seq', (SELECT max(id) FROM toimenpidekoodi)+1);

-- Siirretään FK-viite toimenpidekoodi taso 4 - toimenpidekoodi taso 3 -->
-- tehtävä - toimenpide (taso 3)
ALTER TABLE toimenpidekoodi DROP CONSTRAINT toimenpidekoodi_emo_fkey;

-- Luodaan uusi FK
ALTER TABLE toimenpidekoodi RENAME TO tehtava;
ALTER TABLE tehtava ADD CONSTRAINT tehtava_toimenpide_emo_fkey FOREIGN KEY (emo) REFERENCES toimenpide (id);

--Sanktiotyyppi taulusta on FK viite toimenpidekoodi taso 3:een, eli uuteen toimenpide-tauluun. Siirretään FK.
ALTER TABLE sanktiotyyppi DROP CONSTRAINT sanktiotyyppi_toimenpidekoodi_fkey;
ALTER TABLE sanktiotyyppi ADD CONSTRAINT sanktiotyyppi_toimenpide_fkey FOREIGN KEY (toimenpidekoodi) REFERENCES toimenpide (id);

ALTER TABLE toimenpideinstanssi DROP CONSTRAINT urakka_toimenpide_toimenpide_fkey;
ALTER TABLE toimenpideinstanssi ADD CONSTRAINT toimenpideinstanssi_toimenpide_fkey FOREIGN KEY (toimenpide) REFERENCES toimenpide (id);

-- Poistetaan tasojen 1, 2 ja 3 toimenpidekoodit
DELETE FROM tehtava WHERE taso < 4;


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
                     AND tk4.taso = 4
                     AND (tr.id = tehtavaryhma_ OR tehtavaryhma_ IS NULL)
                     AND (ti.id = toimenpideinstanssi_ OR toimenpideinstanssi_ IS NULL))
      INTO kaikki_ok;
    RETURN kaikki_ok;
END;
$$ LANGUAGE plpgsql;
