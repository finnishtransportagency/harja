-- Estä sopimukseton ylläpitokohde
-- Törmättiin tilanteeseen, jossa Harjaan oli tuotu urakka ilman sopimusta,
-- joten kohteiden haku YHA:sta tuotti sopimuksettomia ylläpitokohteita, jotka eivät
-- näkyneet käyttöliittymässä
ALTER TABLE yllapitokohde ALTER COLUMN sopimus SET NOT NULL;