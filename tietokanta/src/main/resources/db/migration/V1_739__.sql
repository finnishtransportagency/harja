ALTER TABLE yksikkohintainen_tyo
    ADD COLUMN muokkaaja TEXT,
    ADD COLUMN muokattu TIMESTAMP;

ALTER TABLE muutoshintainen_tyo
    ADD COLUMN luoja TEXT,
    ADD COLUMN luotu TIMESTAMP;