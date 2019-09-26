-- Älä salli kirjausta mistään urakasta näille tehtäville
UPDATE toimenpidekoodi SET piilota = TRUE
WHERE
  poistettu = TRUE AND
  emo = (select id from toimenpidekoodi where koodi = '23116') AND
  nimi in(
  'Konetiivistetty massasaumaus 10 cm leveä',
  'Konetiivistetty massasaumaus 20 cm leveä',
  'Kuumapäällyste, ab käsityönä',
  'Kuumapäällyste, valuasfaltti',
  'Päällysteen korjaus mastiksilla siltakohteiden heitoissa',
  'Päällysteiden paikkaus - Konetiivistetty massasaumaus 20 cm leveä',
  'Päällysteiden paikkaus - konetiivistetty -valuasfaltti',
  'Päällysteiden paikkaus - kuumapäällyste',
  'Päällysteiden paikkaus - massasaumaus',
  'Päällysteiden paikkaus - valuasfaltti',
  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - kuumapäällyste',
  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - massasaumaus',
  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) - valuasvaltti',
  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -konetivistetty valuasvaltti',
  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -kylmäpäällyste ml. SOP',
  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -puhallus SIP',
  'Päällysteiden paikkaus (ml. sillat ja siltapaikat) -saumojen juottaminen bitumilla',
  'Päällysteiden paikkaus -kylmäpäällyste ml. SOP',
  'Päällysteiden paikkaus -saumojen juottaminen bitumilla',
  'Päällysteiden paikkaus -saumojen juottaminen mastiksilla',
  'Päällysteiden paikkaus, kylmäpäällyste',
  'Reunapalkin ja päällysteen väl. sauman tiivistäminen',
  'Reunapalkin ja päällysteen välisen sauman tiivistäminen',
  'Reunapalkin liikuntasauman tiivistäminen',
  'Sillan kannen päällysteen päätysauman korjaukset',
  'Sillan päällysteen halkeaman avarrussaumaus',
  'Sillan päällysteen halkeaman sulkeminen',
  'SIP paikkaus (kesto+kylmä)') ;
