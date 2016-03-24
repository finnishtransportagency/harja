-- Lisää tauluihin luoja ja ulkoinen id konverisioita varten
ALTER TABLE yksikkohintainen_tyo ADD COLUMN luoja INTEGER;
ALTER TABLE yksikkohintainen_tyo ADD CONSTRAINT yksikkohintainen_tyo_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);
ALTER TABLE yksikkohintainen_tyo ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE yksikkohintainen_tyo ADD CONSTRAINT uniikki_ulkoinen_yksikkohintainen_tyo UNIQUE (ulkoinen_id, luoja);

ALTER TABLE kokonaishintainen_tyo ADD COLUMN luoja INTEGER;
ALTER TABLE kokonaishintainen_tyo ADD CONSTRAINT kokonaishintainen_tyo_luoja_fkey FOREIGN KEY (luoja) REFERENCES kayttaja (id);
ALTER TABLE kokonaishintainen_tyo ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE kokonaishintainen_tyo ADD CONSTRAINT uniikki_ulkoinen_kokonaishintainen_tyo UNIQUE (ulkoinen_id, luoja);

ALTER TABLE materiaalin_kaytto ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE materiaalin_kaytto ADD CONSTRAINT uniikki_ulkoinen_materiaalin_kaytto UNIQUE (ulkoinen_id, luoja);

ALTER TABLE suolasakko ADD COLUMN ulkoinen_id INTEGER;
ALTER TABLE suolasakko ADD CONSTRAINT uniikki_ulkoinen_suolasakko UNIQUE (ulkoinen_id, luoja);