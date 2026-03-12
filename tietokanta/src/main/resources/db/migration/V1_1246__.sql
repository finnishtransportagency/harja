-- Lisätään uusi tarkastustyyppi, koska myös tarkastustyypit on mukana suoritettavien tehtävien enumeraatiossa
ALTER TYPE suoritettavatehtava ADD VALUE 'tieturvallisuustarkastus' AFTER 'tiestotarkastus';
