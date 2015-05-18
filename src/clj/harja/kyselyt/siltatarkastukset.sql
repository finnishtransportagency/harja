-- name: hae-urakan-sillat
-- Hakee hoidon alueurakalle sillat sekä niiden viimeiset tarkastuspvm:t.
SELECT s.id, s.siltanimi, s.siltanro, s.alue, s1.tarkastusaika, s1.tarkastaja
  FROM silta s
       LEFT JOIN siltatarkastus s1 ON s1.silta = s.id
       LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id AND s2.tarkastusaika > s1.tarkastusaika AND s2.poistettu = false)
  WHERE s.id IN (SELECT silta FROM sillat_alueurakoittain WHERE urakka = :urakka)
    AND s2.id IS NULL;

-- name: hae-urakan-sillat-puutteet
-- Hakee alueurakan sillat, joissa on puutteita (tulos muu kuin A) uusimmassa tarkastuksessa.
SELECT s.id, s.siltanimi, s.siltanro, s.alue, s1.tarkastusaika, s1.tarkastaja,
       (SELECT array_agg(concat(k.kohde, '=', k.tulos, ':', k.lisatieto))
          FROM siltatarkastuskohde k
	 WHERE k.siltatarkastus=s1.id
	   AND k.tulos != 'A') as kohteet
  FROM silta s
       LEFT JOIN siltatarkastus s1 ON s1.silta = s.id
       LEFT JOIN siltatarkastus s2 ON (s2.silta = s.id AND s2.tarkastusaika > s1.tarkastusaika AND s2.poistettu = false)
  WHERE s.id IN (SELECT silta FROM sillat_alueurakoittain WHERE urakka = :urakka)
    AND s2.id IS NULL




-- name: hae-sillan-tarkastukset
-- Hakee sillan sillantarkastukset
SELECT id, silta, urakka,
       tarkastusaika, tarkastaja,
       luotu, luoja, muokattu, muokkaaja, poistettu,
       (SELECT array_agg(concat(k.kohde, '=', k.tulos, ':', k.lisatieto))
          FROM siltatarkastuskohde k
	 WHERE k.siltatarkastus=id) as kohteet
  FROM siltatarkastus
 WHERE silta = :silta AND poistettu = false ORDER BY tarkastusaika DESC

-- name: hae-siltatarkastus
-- Hakee yhden siltatarkastuksen id:n mukaan
SELECT id, silta, urakka,
       tarkastusaika, tarkastaja,
       luotu, luoja, muokattu, muokkaaja, poistettu,
       (SELECT array_agg(concat(k.kohde, '=', k.tulos, ':', k.lisatieto))
          FROM siltatarkastuskohde k
	 WHERE k.siltatarkastus=id) as kohteet
  FROM siltatarkastus
 WHERE id = :id AND poistettu = false

-- name: luo-siltatarkastus<!
-- Luo uuden siltatarkastuksen annetulla sillalle.
INSERT
  INTO siltatarkastus
       (silta, urakka, tarkastusaika, tarkastaja, luotu, luoja, poistettu)
VALUES (:silta, :urakka, :tarkastusaika, :tarkastaja, current_timestamp, :luoja, false)


-- name: hae-siltatarkastusten-kohteet
-- Hakee annettujen siltatarkastusten kohteet ID:iden perusteella
SELECT siltatarkastus, kohde, tulos, lisatieto
  FROM siltatarkastuskohde
 WHERE siltatarkastus IN (:siltatarkastus_idt)


-- name: paivita-siltatarkastuksen-kohteet!
-- Päivittää olemassaolevan siltatarkastuksen kohteet
UPDATE siltatarkastuskohde
   SET tulos = :tulos, lisatieto = :lisatieto
 WHERE siltatarkastus = :siltatarkastus AND kohde = :kohde

-- name: poista-siltatarkastus!
-- Merkitsee annetun siltatarkastuksen poistetuksi
UPDATE siltatarkastus
   SET poistettu = TRUE
 WHERE id = :id

