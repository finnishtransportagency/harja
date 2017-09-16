ALTER TABLE kokonaishintainen_tyo ALTER COLUMN "osuus-hoitokauden-summasta" TYPE NUMERIC (7,6);
ALTER TABLE kokonaishintainen_tyo ADD CONSTRAINT osuus_yksi_tai_alle CHECK ("osuus-hoitokauden-summasta" BETWEEN  0 AND 1);
