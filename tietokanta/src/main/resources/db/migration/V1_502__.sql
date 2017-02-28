-- Poista Liidosta tuodut duplikaatit
DELETE
  FROM ilmoitus
 WHERE id IN (SELECT id
                FROM (SELECT id, ulkoinen_id,
                ROW_NUMBER() OVER (partition BY ulkoinen_id, luoja ORDER BY id) AS rnum
  FROM ilmoitus) t
 WHERE t.rnum > 1 and t.ulkoinen_id IS NOT NULL);

-- Estä duplikaattien tuominen uudelleen Harja-järjestelmään
ALTER TABLE ilmoitus ADD CONSTRAINT uniikki_ulkoinen_ilmoitus UNIQUE (ulkoinen_id, luoja);