-- Triggerit, jotka päivittävät kohteen geometrian, aina kun kohteenosien tietoja päivitetään
-- Kohteenosien (sulkujen ja siltojen) tiedot saadaan integraatiolla (vatusta ja taitorakennerekisteristä)
-- Kanavaurakoiden geometriat koostetaan kohteiden geometrioista

CREATE OR REPLACE FUNCTION paivita_urakoiden_geometriat() RETURNS TRIGGER AS $$
BEGIN
  UPDATE urakka
  SET
    alue = urakan_alue,
    muokattu = NOW()
  FROM (SELECT
          linkki."urakka-id"                                             AS id,
          ST_BUFFER(ST_SIMPLIFY(ST_COLLECT(kohde.sijainti), 3000), 3000) AS urakan_alue
        FROM kan_kohde kohde
          JOIN kan_kohde_urakka linkki ON kohde.id = linkki."kohde-id"
        GROUP BY linkki."urakka-id") AS geometriat
  WHERE urakka.id = geometriat.id;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION paivita_osien_kohteiden_geometriat() RETURNS TRIGGER AS $$
BEGIN
  UPDATE kan_kohde
  SET
    sijainti = kohteen_sijainti,
    muokattu = NOW()
  FROM (SELECT
          kohde.id,
          ST_BUFFER(ST_SIMPLIFY(ST_COLLECT(osa.sijainti), 3000), 3000) AS kohteen_sijainti
        FROM kan_kohteenosa osa
          JOIN kan_kohde kohde ON osa."kohde-id" = kohde.id
       GROUP BY kohde.id) AS geometriat
  WHERE kan_kohde.id = geometriat.id;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Kun osa liitetään kohteeseen, päivitetään kohteen geometria
DROP TRIGGER IF EXISTS kohteenosat_muuttuneet_trigger
ON kan_kohteenosa;

CREATE TRIGGER kohteenosat_muuttuneet_trigger
AFTER INSERT OR UPDATE ON kan_kohteenosa
FOR EACH STATEMENT EXECUTE PROCEDURE paivita_osien_kohteiden_geometriat();

-- Kun urakkaan liitetään kohteita, päivitetään urakan geometria
DROP TRIGGER IF EXISTS urakkalinkit_muuttuneet_trigger
ON kan_kohde_urakka;

CREATE TRIGGER urakkalinkit_muuttuneet_trigger
AFTER INSERT OR UPDATE ON kan_kohde_urakka
FOR EACH STATEMENT EXECUTE PROCEDURE paivita_urakoiden_geometriat();

