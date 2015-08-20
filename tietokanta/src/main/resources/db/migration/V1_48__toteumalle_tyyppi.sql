ALTER TABLE toteuma DROP COLUMN kokonaishintainentyo;
CREATE TYPE toteumatyyppi as ENUM('kokonaishintainen', 'yksikkohintainen', 'akillinen-hoitotyo', 'lisatyo', 'muutostyo');
ALTER TABLE toteuma ADD COLUMN tyyppi toteumatyyppi;