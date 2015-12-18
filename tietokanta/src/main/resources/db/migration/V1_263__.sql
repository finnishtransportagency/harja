-- Kuvaus: uudelleennimeä toimenpidekoodin historiakuvasarake tilannekuvaksi
ALTER TABLE toimenpidekoodi DROP COLUMN historiakuva;
ALTER TABLE toimenpidekoodi ADD COLUMN suoritettavatehtava suoritettavatehtava;

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'suolaus' :: suoritettavatehtava
WHERE nimi = 'Suolaus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'auraus ja sohjonpoisto' :: suoritettavatehtava
WHERE nimi = 'Auraus ja sohjonpoisto';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'pistehiekoitus' :: suoritettavatehtava
WHERE nimi = 'Pistehiekoitus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'linjahiekoitus' :: suoritettavatehtava
WHERE nimi = 'Linjahiekoitus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'lumivallien madaltaminen' :: suoritettavatehtava
WHERE nimi = 'Lumivallien madaltaminen';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sulamisveden haittojen torjunta' :: suoritettavatehtava
WHERE nimi = 'Sulamisveden haittojen torjunta';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'kelintarkastus' :: suoritettavatehtava
WHERE nimi = 'Kelintarkastus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'tiestotarkastus' :: suoritettavatehtava
WHERE nimi = 'Tiestotarkastus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'koneellinen niitto' :: suoritettavatehtava
WHERE nimi = 'Koneellinen niitto';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'koneellinen vesakonraivaus' :: suoritettavatehtava
WHERE nimi = 'Koneellinen vesakonraivaus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'liikennemerkkien puhdistus' :: suoritettavatehtava
WHERE nimi = 'Liikennemerkkien puhdistus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorateiden muokkaushoylays' :: suoritettavatehtava
WHERE nimi = 'Sorateiden muokkaushoylays';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorateiden polynsidonta' :: suoritettavatehtava
WHERE nimi = 'Sorateiden polynsidonta';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorateiden tasaus' :: suoritettavatehtava
WHERE nimi = 'Sorateiden tasaus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorastus' :: suoritettavatehtava
WHERE nimi = 'Sorastus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'harjaus' :: suoritettavatehtava
WHERE nimi = 'Harjaus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'pinnan tasaus' :: suoritettavatehtava
WHERE nimi = 'Pinnan tasaus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'paallysteiden paikkaus' :: suoritettavatehtava
WHERE nimi = 'Paallysteiden paikkaus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'paallysteiden juotostyot' :: suoritettavatehtava
WHERE nimi = 'Paallysteiden juotostyot';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'siltojen puhdistus' :: suoritettavatehtava
WHERE nimi = 'Siltojen puhdistus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'l- ja p-alueiden puhdistus' :: suoritettavatehtava
WHERE nimi = 'L- ja p-alueiden puhdistus';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'muu' :: suoritettavatehtava
WHERE nimi = 'Muu';
