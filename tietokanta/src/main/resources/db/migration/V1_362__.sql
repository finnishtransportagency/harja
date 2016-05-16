UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorateiden muokkaushoylays'::suoritettavatehtava
WHERE nimi = 'Sorateiden muokkaushöyläys';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'sorateiden polynsidonta'::suoritettavatehtava
WHERE nimi = 'Sorateiden pölynsidonta';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'paallysteiden juotostyot'::suoritettavatehtava
WHERE nimi = 'Päällysteiden juotostyöt';

UPDATE toimenpidekoodi
SET suoritettavatehtava = 'paallysteiden paikkaus'::suoritettavatehtava
WHERE nimi = 'Päällysteiden paikkaus';