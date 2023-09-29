-- Lisätään urakka_paatos tauluun linkitys kulu tauluun, jotta päätökset ja kulut saadaan poistettua/muokattua jos on tarvetta
ALTER TABLE urakka_paatos
    ADD COLUMN kulu_id INTEGER DEFAULT null;
