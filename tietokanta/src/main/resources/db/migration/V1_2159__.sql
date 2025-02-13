-- Tiedosto uudelleennimetään ennen mergeä 

-- Poista turhaksi jääneitä sarakkeita kanava aineistosta, mitä Harjassa ei käytetä 
ALTER TABLE kan_sulku DROP COLUMN kanavatyyppi;
ALTER TABLE kan_sulku DROP COLUMN alakanavan_alavertaustaso;
ALTER TABLE kan_sulku DROP COLUMN alakanavan_ylavertaustaso;
ALTER TABLE kan_sulku DROP COLUMN ylakanavan_ylavertaustaso;
ALTER TABLE kan_sulku DROP COLUMN ylakanavan_alavertaustaso;
ALTER TABLE kan_sulku DROP COLUMN kynnys_1;
ALTER TABLE kan_sulku DROP COLUMN kynnys_2;


-- Korjaa sulkujen linkitys kohdekokonaisuuteen 
-- Uudesta aineistosta on poistunut kohdekokonaisuus nimi, joten etsitään geometrian avulla 
-- Poista ensin triggerit jotta voidaan päivittää geometria gridi 
DROP TRIGGER tg_lisaa_kanavasulku_kohdetietoihin ON kan_sulku;
DROP FUNCTION IF EXISTS lisaa_tai_paivita_kanavasulku_kohdetietoihin();



-- Asetetaan geometrialle SRID tunniste (ei muuta koordinaatteja)
-- Jotta tulkitaan geometriat oikein ja voimme tehdä linkityksen sijainnin perusteella 
UPDATE kan_sulku SET geometria = ST_SetSRID(geometria, 3067);
UPDATE kan_kohde SET sijainti = ST_SetSRID(sijainti, 3067);


UPDATE kan_kohde  
  SET  sijainti = ST_SetSRID(sijainti, 3067)  
WHERE  ST_SRID(sijainti) = 0 AND sijainti IS NOT NULL;

ALTER TABLE kan_kohde ALTER COLUMN sijainti TYPE geometry(Point, 3067) USING ST_SetSRID(sijainti, 3067);


UPDATE kan_sulku  
  SET  geometria = ST_SetSRID(geometria, 3067)  
WHERE  ST_SRID(geometria) = 0 AND geometria IS NOT NULL;

ALTER TABLE kan_sulku ALTER COLUMN geometria TYPE geometry(MultiPolygon, 3067) USING ST_SetSRID(geometria, 3067);


UPDATE kan_kohteenosa  
  SET  sijainti = ST_SetSRID(sijainti, 3067)  
WHERE  ST_SRID(sijainti) = 0 AND sijainti IS NOT NULL;

ALTER TABLE kan_kohteenosa ALTER COLUMN sijainti TYPE geometry(Point, 3067) USING ST_SetSRID(sijainti, 3067);



-- Tehdään uusi triggeri & procci, tätä tuunattu vähän uudelleen   
-- Kutsutaan kun kan_sulku tulee päivityksiä 
CREATE OR REPLACE FUNCTION lisaa_tai_paivita_kanavasulku_kohdetietoihin()
  RETURNS TRIGGER AS $$
DECLARE
  integraatiokayttaja INTEGER;
  kohteen_osa         INTEGER;
  kohde               INTEGER;
  kohdekokonaisuus    INTEGER;
  oletuskayttotapa       TEXT;
BEGIN
  integraatiokayttaja := (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio');

  -- Katso löytyykö kohde tai kohteenosa jo kannasta 
  kohteen_osa := (SELECT id FROM kan_kohteenosa WHERE lahdetunnus = new."kanavanro" AND tyyppi = 'sulku');
  kohde := (SELECT "kohde-id" FROM kan_kohteenosa WHERE lahdetunnus = new."kanavanro" AND tyyppi = 'sulku');

  -- Jos kohdetta ei löydy tunnuksella, katso löytyykö nimellä
  IF (kohde ISNULL) THEN
    kohde := (SELECT id FROM kan_kohde WHERE nimi = new."nimi");
    RAISE NOTICE 'Kohdetta ei löytynyt tunnuksella, nimi: % kohde: %', new."nimi", kohde;
  END IF;

  -- Kohdetta ei löytynyt nimelläkään, katso löytyykö läheltä kohdetta (200 metriä) 
  IF (kohde ISNULL) THEN
    kohde := (SELECT id FROM kan_kohde WHERE ST_DWithin(new.geometria, sijainti, 200) LIMIT 1);
    RAISE NOTICE 'Kohdetta ei löytynyt nimelläkään, etsitään alueelta: %', kohde;
  END IF;

  -- Kohdetta ei löytynyt, ei  voida tehdä linkitystä
  -- kan_sulku taulun pohjalle tulee uusi sulku, mutta sitä ei pystytty täsmentämään kohteeseen 
  IF (kohde ISNULL) THEN
    RAISE NOTICE 'Linkitystä ei löytynyt';
    RETURN new;
  END IF;

  UPDATE kan_kohde SET 
    nimi = new."nimi", 
    muokattu = current_timestamp, 
    muokkaaja = integraatiokayttaja
  WHERE id = kohde;

  -- Käyttötapa, eli 'palvelumuoto'
  IF (new."kayttotapa" = 'Itsepalvelu') THEN oletuskayttotapa = 'itse';
  ELSIF (new."kayttotapa" = 'Kaukokäyttö') THEN oletuskayttotapa = 'kauko';
  ELSIF (new."kayttotapa" = 'Paikalliskäyttö') THEN oletuskayttotapa = 'paikallis';
  ELSE oletuskayttotapa = 'muu';
  END IF;

  -- Päivitä / tee uusi kohteenosa
  --   (sulku = kohteen osa. Tälle on vaan erillinen taulu)  
  IF (kohteen_osa ISNULL) THEN
    RAISE NOTICE 'Insert, kohde id: % kanavanro: %', kohde, new."kanavanro";

    INSERT INTO kan_kohteenosa (
      tyyppi, 
      "kohde-id", 
      oletuspalvelumuoto, 
      luoja, 
      luotu, 
      sijainti, 
      lahdetunnus, 
      poistettu, 
      etuliite
    ) VALUES (
      'sulku' :: KOHTEENOSA_TYYPPI, 
      kohde, 
      oletuskayttotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO, 
      integraatiokayttaja, 
      current_timestamp, 
      ST_Centroid(new."geometria") :: GEOMETRY, 
      new."kanavanro", 
      new."poistettu", 
      '-'
    );
  ELSE
    RAISE NOTICE 'Update, kohde id: % kohteen osa(id): %', kohde, kohteen_osa;

    UPDATE kan_kohteenosa SET 
      "kohde-id" = kohde, 
      oletuspalvelumuoto = oletuskayttotapa :: LIIKENNETAPAHTUMA_PALVELUMUOTO, 
      sijainti = ST_Centroid(new."geometria") :: GEOMETRY,
      muokattu = current_timestamp, 
      poistettu = new."poistettu", 
      muokkaaja = integraatiokayttaja, 
      etuliite = '-'
    WHERE id = kohteen_osa;
  END IF;

  RETURN new;
END;
$$ LANGUAGE plpgsql;


CREATE TRIGGER tg_lisaa_kanavasulku_kohdetietoihin
AFTER INSERT OR UPDATE ON kan_sulku
FOR EACH ROW EXECUTE PROCEDURE lisaa_tai_paivita_kanavasulku_kohdetietoihin();
