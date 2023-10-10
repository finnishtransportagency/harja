-- Annetaan kaikille järjestelmäkäyttäjille kirjoitusoikeus alkuun. Eli tilanne aivan sama kuten ennen.
-- Ei yliaja vanhoja oikeuksia, eli jos käyttäjällä on ennestään 'analytiikka', siihen tulee nyt ['analytiikka', 'kirjoitus']
-- Tämän migraation jälkeen Hallinnasta voidaan naputella pelkkä luku oikeus niille käyttäjille ketkä ei kirjoitusta tarvitse
UPDATE kayttaja 
SET api_oikeudet = 
    CASE
        WHEN api_oikeudet IS NULL THEN ARRAY['kirjoitus'::apioikeus]
        ELSE api_oikeudet || ARRAY['kirjoitus'::apioikeus]
    END
WHERE poistettu IS FALSE 
AND jarjestelma IS TRUE 
AND (api_oikeudet IS NULL OR NOT api_oikeudet @> ARRAY['kirjoitus'::apioikeus]);
