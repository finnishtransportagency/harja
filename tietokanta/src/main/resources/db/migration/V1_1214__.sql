-- Lisää muutosten vaikutus urakka_tehtavamaara-tauluun
ALTER TABLE urakka_tehtavamaara
    ADD COLUMN maaramuutos NUMERIC;
