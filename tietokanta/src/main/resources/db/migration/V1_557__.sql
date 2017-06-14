-- Luodaan vesiväylien materiaalitaulu ja näkymät
-- Vesiväylien materiaalit ovat vapaasti kirjattavissa (ei koodistoa)

CREATE TABLE vv_materiaali (
  id SERIAL PRIMARY KEY,

  -- normaalit muokkaustiedot
  luoja INTEGER REFERENCES kayttaja (id),
  luotu TIMESTAMP DEFAULT current_timestamp,
  muokattu TIMESTAMP,
  muokkaaja INTEGER REFERENCES kayttaja (id),


  "urakka-id" INTEGER REFERENCES urakka (id),   -- urakka, johon tämä käyttö kuuluu
  nimi VARCHAR(128),   -- materiaalin nimi
  maara INTEGER NOT NULL, -- negatiivinen: käytetty, positiivinen: saatu/hankittu

  pvm DATE NOT NULL, -- pvm jolloin materiaali saatu/käytetty
  lisatieto TEXT -- vapaatekstinä lisätieto
);



CREATE VIEW vv_materiaalilistaus AS
  SELECT nimi,
         FIRST_VALUE(maara) over w AS "alkuperainen-maara",
	 SUM(maara) over w AS "maara-nyt"
    FROM vv_materiaali
WINDOW w AS (PARTITION BY nimi ORDER BY pvm ASC);
