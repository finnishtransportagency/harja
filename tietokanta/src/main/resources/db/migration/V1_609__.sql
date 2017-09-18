-- Lisää vesiväylien prosenttijaolle taulu

ALTER TABLE kokonaishintainen_tyo
  ADD COLUMN "osuus-hoitokauden-summasta" NUMERIC (6,6);
