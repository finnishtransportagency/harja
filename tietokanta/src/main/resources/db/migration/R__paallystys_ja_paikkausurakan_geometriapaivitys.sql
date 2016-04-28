-- Lisää funktio päällystys-/paikkausurakoiden geometrian päivittämiselle
CREATE OR REPLACE FUNCTION paivita_paallystys_ja_paikkausurakoiden_geometriat() RETURNS VOID AS $$
BEGIN
  UPDATE urakka
  SET alue = uusi_alue
  FROM (SELECT u.id, u.nimi, ST_ConvexHull(ST_UNION(sijainti)) AS uusi_alue
        FROM yllapitokohdeosa osa
          JOIN yllapitokohde ypk ON osa.yllapitokohde = ypk.id
          JOIN urakka u ON ypk.urakka = u.id
        GROUP BY u.id, u.nimi) AS geometriat
  WHERE urakka.id = geometriat.id;
  RETURN;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_paallystys_tai_paikkausurakan_geometria(urakkaid INTEGER) RETURNS VOID AS $$
BEGIN
  UPDATE urakka
  SET alue = uusi_alue
  FROM (SELECT u.id, u.nimi, ST_ConvexHull(ST_UNION(sijainti)) AS uusi_alue
        FROM yllapitokohdeosa osa
          JOIN yllapitokohde ypk ON osa.yllapitokohde = ypk.id
          JOIN urakka u ON ypk.urakka = u.id
        GROUP BY u.id, u.nimi) AS geometriat
  WHERE urakka.id = urakkaid;
  RETURN;
END;
$$ LANGUAGE plpgsql;