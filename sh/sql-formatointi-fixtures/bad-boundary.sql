INSERT INTO urakka (id, nimi)
  SELECT id, nimi
    FROM toinen_urakka
  WHERE id = :id;