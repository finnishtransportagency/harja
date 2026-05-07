INSERT INTO urakka (id, nimi)
VALUES (:id, :nimi)
ON CONFLICT (id)
  DO
  UPDATE SET
    nimi = :nimi
  WHERE urakka.id = :id;