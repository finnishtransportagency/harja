-- Lisää hoitoluokka-tauluun tietolajitunniste

CREATE TYPE hoitoluokan_tietolajitunniste AS ENUM ('talvihoitoluokka', 'soratieluokka', 'viherhoitoluokka');

ALTER TABLE hoitoluokka ADD COLUMN tietolajitunniste hoitoluokan_tietolajitunniste;

UPDATE hoitoluokka SET tietolajitunniste = 'talvihoitoluokka'::hoitoluokan_tietolajitunniste;