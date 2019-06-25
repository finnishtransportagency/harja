INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='Destia Oy'),
  123,
  current_timestamp,
  31337,
  'aura-auto',
  ST_MakePoint(429493,7207739)::GEOMETRY,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
  ARRAY['harjaus', 'suolaus']::suoritettavatehtava[]
);

INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='NCC Roads Oy'),
  123,
  current_timestamp,
  31338,
  'aura-auto',
  ST_MakePoint(427861,7211247)::GEOMETRY,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
  ARRAY['pistehiekoitus']::suoritettavatehtava[]
);

INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='Destia Oy'),
  123,
  current_timestamp,
  31339,
  'aura-auto',
  ST_MakePoint(499399,7249077)::GEOMETRY,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
  ARRAY['muu']::suoritettavatehtava[]
);

INSERT INTO tyokonehavainto (tyokoneid, jarjestelma, organisaatio, viestitunniste,
                             lahetysaika, vastaanotettu, tyokonetyyppi, sijainti, urakkaid,
                             suunta, tehtavat)
VALUES (673, 'Aurausjannut Oy', (SELECT id FROM organisaatio WHERE nimi='YIT Rakennus Oy'), 108323,
             '2016-11-30 11:22:30', '2016-11-30 11:22:37', 'ajoneuvo', St_Point(480031.745918236, 7188116.027609303)::GEOMETRY, (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
             191, '{auraus ja sohjonpoisto}');

INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='NCC Roads Oy'),
  123,
  current_timestamp,
  41000,
  'tarkastajan-auto',
  ST_MakePoint(428024,7210433)::GEOMETRY,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
  ARRAY['tilaajan laadunvalvonta']::suoritettavatehtava[]
);

INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='NCC Roads Oy'),
  123,
  current_timestamp,
  41000,
  'tarkastajan-auto',
  ST_MakePoint(428871,7209925)::GEOMETRY,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
  ARRAY['tilaajan laadunvalvonta']::suoritettavatehtava[]
);


INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='NCC Roads Oy'),
  321,
  current_timestamp,
  41000,
  'aura-auto',
  ST_GeomFromGeoJSON('{"type":"LineString","coordinates":[[440271,7208395],[440271,7208395],[440399,7209019],[440820,7209885]]}')::GEOMETRY,
  60,
  (SELECT id FROM urakka WHERE nimi = 'Oulun alueurakka 2014-2019'),
  ARRAY['tilaajan laadunvalvonta']::suoritettavatehtava[]
);