ALTER TABLE johto_ja_hallintokorvaus
    DROP CONSTRAINT "uniikki_johto_ja_hallintokorvaus",
    DROP CONSTRAINT "johto_ja_hallintokorvaus_ennen-urakkaa-id_fkey",
    ALTER COLUMN "ennen-urakkaa-id" TYPE BOOLEAN USING "ennen-urakkaa-id"::BOOLEAN,
    ADD COLUMN "osa-kuukaudesta" NUMERIC DEFAULT 1;

ALTER TABLE johto_ja_hallintokorvaus RENAME COLUMN "ennen-urakkaa-id" TO "ennen-urakkaa";

-- Käytetään -1 arvoa merkkaamaan, että kyseessä ei ole ennen-urakkaa suunniteltu työ.
-- Muuten palautetaan id, sillä se on aina uniikki.
CREATE FUNCTION ei_ennen_urakka(ennen_urakkaa boolean, id integer) RETURNS integer AS $$
BEGIN
    IF ennen_urakkaa IS TRUE
    THEN
      RETURN id;
    ELSE
      RETURN -1;
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Halutaan käytännössä unique constraint PAITSI SILLOIN, kun kyseessä on ennen-urakkaa suunniteltu työ.
ALTER TABLE johto_ja_hallintokorvaus ADD CONSTRAINT uniikki_johto_ja_hallintokorvaus EXCLUDE ("urakka-id" WITH =, "toimenkuva-id" WITH =, vuosi WITH =, kuukausi WITH =, ei_ennen_urakka("ennen-urakkaa", id) WITH =);
ALTER TABLE johto_ja_hallintokorvaus ADD CONSTRAINT ennen_urakkaa_arvot_samalle_kuukaudelle CHECK (("ennen-urakkaa" IS TRUE AND kuukausi = 10) OR ("ennen-urakkaa" IS NOT TRUE));

-- Aikasemmin ennen-urakkaa työt on laitettu vain yhdelle riville sillä oletuksella, että kaikki arvot on samoja neljälle ja puolelle kuukaudelle
-- Tämä ei enää pidä paikkaansa, niin otetaan kyseiset arvot omille riveillensä. Eli pitää luoda neljä riviä lisää sillä viides on jo olemassa.
DO $$
    DECLARE jh johto_ja_hallintokorvaus%ROWTYPE;
            i INTEGER;
            tunnit_ NUMERIC;
            puolikas_tunnit_ NUMERIC;
BEGIN
    FOR jh IN (SELECT *
              FROM johto_ja_hallintokorvaus
              WHERE "ennen-urakkaa" IS TRUE)
    LOOP
        tunnit_ = (SELECT round(jh.tunnit / 4.5, 2));
        puolikas_tunnit_ = (SELECT round(tunnit_, 2));

        FOR i IN 1..3
        LOOP
          INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu, luoja, muokattu, muokkaaja, vuosi, kuukausi, "ennen-urakkaa")
          VALUES (jh."urakka-id", jh."toimenkuva-id", tunnit_, jh."tuntipalkka", jh.luotu, jh.luoja, now(), (SELECT id FROM kayttaja WHERE kayttajanimi='Integraatio'),
                  jh.vuosi, jh.kuukausi, TRUE);
        END LOOP;
        INSERT INTO johto_ja_hallintokorvaus ("urakka-id", "toimenkuva-id", tunnit, tuntipalkka, luotu, luoja, muokattu, muokkaaja, vuosi, kuukausi, "ennen-urakkaa", "osa-kuukaudesta")
        VALUES (jh."urakka-id", jh."toimenkuva-id", puolikas_tunnit_, jh."tuntipalkka", jh.luotu, jh.luoja, now(), (SELECT id FROM kayttaja WHERE kayttajanimi='Integraatio'),
                jh.vuosi, jh.kuukausi, TRUE, 0.5);
    END LOOP;
END $$;

DROP TABLE johto_ja_hallintokorvaus_ennen_urakkaa;
