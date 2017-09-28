ALTER TABLE vv_materiaali ADD COLUMN halytysraja INTEGER;

CREATE OR REPLACE VIEW vv_materiaalilistaus AS
  SELECT DISTINCT ON (m1."urakka-id", m1.nimi)
    m1."urakka-id",
    m1.nimi,
    -- Haetaan alkuperäinen määrä (ajallisesti ensimmäinen kirjaus)
    (SELECT maara
     FROM vv_materiaali m2
     WHERE m2."urakka-id" = m1."urakka-id"
           AND m2.nimi = m1.nimi
           AND m2.poistettu IS NOT TRUE
     ORDER BY pvm ASC
     LIMIT 1)                            AS "alkuperainen-maara",
    -- Haetaan "nykyinen" määrä: kaikkien kirjausten summa
    (SELECT SUM(maara)
     FROM vv_materiaali m3
     WHERE m3."urakka-id" = m1."urakka-id"
           AND m3.nimi = m1.nimi
           AND m3.poistettu IS NOT TRUE) AS "maara-nyt",
    -- Kerätään kaikki muutokset omaan taulukkoon
    (SELECT array_agg(ROW (l.pvm, l.maara, l.lisatieto, l.id) :: VV_MATERIAALI_MUUTOS)
     FROM vv_materiaali l
     WHERE l."urakka-id" = m1."urakka-id"
           AND l.poistettu IS NOT TRUE
           AND m1.nimi = l.nimi)         AS muutokset,
    m1.halytysraja
  FROM vv_materiaali m1
  WHERE m1.poistettu IS NOT TRUE
  ORDER BY nimi ASC;