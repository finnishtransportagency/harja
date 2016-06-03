-- Tässä tiedostossa on kantaan lisättävää testidataa

-- Tarkastusajot

INSERT INTO tarkastusajo (id, luotu, paatetty, tyyppi)
VALUES (666, '2005-12-20 00:00:00+02', '2005-12-20 00:20:00+02', 1);

INSERT INTO tarkastusreitti (id, pistetyyppi, tarkastusajo, aikaleima, vastaanotettu, sijainti, kitkamittaus, lampotila, tasaisuus, lumisuus, kuvaus, kuva, havainnot)
VALUES
  (1, 1, 666, '2005-12-20 00:01:00+02', '2005-12-20 00:01:00+02',
      ST_MakePoint(465641.5999816895, 7230492.000024414) :: GEOMETRY, 0.2, NULL, NULL, NULL, NULL, NULL,
   ARRAY [(SELECT id
           FROM vakiohavainto
           WHERE nimi = 'Liukasta')]),
  (2, 1, 666, '2005-12-20 00:02:00+02', '2005-12-20 00:01:00+02',
      ST_MakePoint(466089.5999816895, 7230676.000024414) :: GEOMETRY, NULL, NULL, NULL, NULL, NULL, NULL,
   ARRAY [(SELECT id
           FROM vakiohavainto
           WHERE nimi = 'Liukasta')]),
  (3, 1, 666, '2005-12-20 00:03:00+02', '2005-12-20 00:01:00+02',
      ST_MakePoint(466409.5999816895, 7230780.000024414) :: GEOMETRY, 0.3, NULL, NULL, NULL, NULL, NULL, NULL);
