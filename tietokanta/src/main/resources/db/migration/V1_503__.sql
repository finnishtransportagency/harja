-- Tauluun on tullut alustusvaiheessa rivejä, joilla talvihoitoluokka on NULL.
-- Uusia rivejä luotaessa NULL talvihoitoluokka asetetaan nollaksi
-- Tehdään sama operaatio näille alustuksessa syntyneille riveille
-- Täytyy vaan ottaa huomioon, että muutos voi epäonnistua, koska pvm+luokka+materiaali
-- yhdistelmälle voi olla jo rivi
-- tällöin lasketaan yhteen määrät, ja asetetaan uusi arvo
-- Siksi UPSERT.

INSERT INTO urakan_materiaalin_kaytto_hoitoluokittain
SELECT pvm, materiaalikoodi, 0, urakka, maara FROM
urakan_materiaalin_kaytto_hoitoluokittain WHERE talvihoitoluokka IS NULL
ON CONFLICT ON CONSTRAINT uniikki_urakan_materiaalin_kaytto_hoitoluokittain DO
UPDATE SET maara = urakan_materiaalin_kaytto_hoitoluokittain.maara + EXCLUDED.maara;

-- Poista lopuksi duplikoidut, nyt turhat rivit.
DELETE FROM urakan_materiaalin_kaytto_hoitoluokittain
  WHERE talvihoitoluokka IS NULL;
