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
  SELECT DISTINCT ON (m1."urakka-id", m1.nimi)
         m1."urakka-id", m1.nimi,
         (SELECT maara FROM vv_materiaali m2
           WHERE m2."urakka-id" = m1."urakka-id" AND m2.nimi = m1.nimi
          ORDER BY pvm ASC LIMIT 1) AS "alkuperainen-maara",
	 	 (SELECT SUM(maara) FROM vv_materiaali m3
	 	   WHERE m3."urakka-id" = m1."urakka-id" AND m3.nimi = m1.nimi) AS "maara-nyt"
    FROM vv_materiaali m1
    ORDER BY nimi ASC;
