ALTER TABLE kan_kohde
  ADD COLUMN jarjestys INTEGER;

CREATE OR REPLACE FUNCTION paivita_kanavakohteiden_jarjestys()
  RETURNS VOID AS $$

  DECLARE kohdekokonaisuus_ RECORD;
  DECLARE kohde_ RECORD;
  DECLARE ylakohde_ RECORD;
  DECLARE alakohde_ RECORD;
  DECLARE cnt_ INT;

BEGIN
  FOR kohdekokonaisuus_ IN (SELECT * from kan_kohdekokonaisuus) LOOP
    cnt_ = 0;

    -- Tallenna kohteiden maantieteellinen järjestys kohdekokonaisuuden sisällä
    FOR kohde_ IN (SELECT * from kan_kohde where "kohdekokonaisuus-id" = kohdekokonaisuus_.id ORDER BY st_ymax(sijainti)) LOOP
      cnt_ = cnt_ + 1;
      UPDATE kan_kohde set jarjestys = cnt_ where id = kohde_.id;
    END LOOP;

    -- Tallenna kohteelle ylä- ja alakanavaan seuraava kohde
    FOR kohde_ IN (SELECT * from kan_kohde where "kohdekokonaisuus-id" = kohdekokonaisuus_.id ORDER BY jarjestys) LOOP
      SELECT * from kan_kohde where "kohdekokonaisuus-id" = kohdekokonaisuus_.id and jarjestys = (kohde_.jarjestys + 1) INTO ylakohde_;
      SELECT * from kan_kohde where "kohdekokonaisuus-id" = kohdekokonaisuus_.id and jarjestys = (kohde_.jarjestys - 1) INTO alakohde_;
      UPDATE kan_kohde set "ylos-id" = ylakohde_.id, "alas-id" = alakohde_.id where id = kohde_.id;
    END LOOP;

  END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Päivitä kertaalleen järjestykseen liittyvät tiedot kantaan. Funktio ei ole triggerin takana, jotta olisi mahdollisuus
-- ylikirjoittaa automatiikkaa. Jos kanavasuluissa tapahtuu muutoksia (uusi sulku, vanha pois), täytyy sproc ajaa uudelleen.
-- Ole silloin tietoinen siitä ylikirjoitatko jotakin manuaalisesti korjattua.
SELECT paivita_kanavakohteiden_jarjestys();
