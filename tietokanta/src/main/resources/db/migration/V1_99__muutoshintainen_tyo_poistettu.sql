ALTER TABLE muutoshintainen_tyo
ADD COLUMN poistettu boolean default false,
ADD COLUMN muokkaaja integer,
ADD COLUMN muokattu timestamp;
