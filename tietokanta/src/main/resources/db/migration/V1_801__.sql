INSERT INTO sanktiotyyppi(nimi, sanktiolaji, urakkatyyppi)
VALUES
 ('Lupaussanktio', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'C'::sanktiolaji, 'muistutus'::sanktiolaji],
   ARRAY['teiden-hoito']::urakkatyyppi[]),
  ('Vastuuhenkilöiden vaihtosanktio', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'C'::sanktiolaji, 'muistutus'::sanktiolaji],
   ARRAY['teiden-hoito']::urakkatyyppi[]),
  ('Sanktio vastuuhenkilöiden testikeskiarvon laskemisesta', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'C'::sanktiolaji, 'muistutus'::sanktiolaji],
   ARRAY['teiden-hoito']::urakkatyyppi[]),
  ('Sanktio vastuuhenkilöiden tenttikeskiarvon laskemisesta*', ARRAY['A'::sanktiolaji, 'B'::sanktiolaji, 'C'::sanktiolaji, 'muistutus'::sanktiolaji],
   ARRAY['teiden-hoito']::urakkatyyppi[]);