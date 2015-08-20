CREATE type sopimustyyppi as ENUM('palvelusopimus', 'kokonaisurakka');
ALTER TABLE urakka ADD COLUMN sopimustyyppi sopimustyyppi;