
-- Lisätään liitteeseen urakka linkki, jotta pääsynvalvonta liitteiden lataukseen voidaan toteuttaa

ALTER TABLE liite ADD COLUMN urakka integer REFERENCES urakka (id);
