-- Muutetaan tehtävän ja tehtäväryhmän nimi, jotta yhteys on käyttäjälle ja tietokannan tonkijalle selkeä
UPDATE tehtava SET nimi = 'Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään' WHERE nimi = 'Tilaajan rahavaraus lupaukseen 1';
UPDATE tehtavaryhma SET nimi = 'Tilaajan rahavaraus lupaukseen 1 / kannustinjärjestelmään (T3)' WHERE nimi = 'Tilaajan rahavaraus (T3)';
