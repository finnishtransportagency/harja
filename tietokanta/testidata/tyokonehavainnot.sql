INSERT INTO tyokonehavainto (jarjestelma, organisaatio, viestitunniste, lahetysaika, tyokoneid, tyokonetyyppi, sijainti, suunta,
urakkaid, tehtavat) VALUES (
  'Urakoitsijan järjestelmä 1',
  (SELECT id FROM organisaatio WHERE nimi='Destia Oy'),
  123,
  current_timestamp,
  31337,
  'aura-auto',
  ST_MakePoint(429493,7207739)::POINT,
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
  ST_MakePoint(427861,7211247)::POINT,
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
  ST_MakePoint(499399,7249077)::POINT,
  45,
  (SELECT id FROM urakka WHERE nimi = 'Pudasjärven alueurakka 2007-2012'),
  ARRAY['muu']::suoritettavatehtava[]
);