-- Hoitourakoiden sanktiolajit ovat käytössä myös teiden hoidon urakoissa (MHU).
UPDATE sanktiotyyppi SET urakkatyyppi = '{hoito,teiden-hoito}' WHERE urakkatyyppi = '{hoito}';
-- Dummy-sanktiotyyppi teiden hoidon sanktiolajeille, jotka eivät varsinaisesti liity mihinkään sanktiotyyppiin.
INSERT INTO sanktiotyyppi (nimi, toimenpidekoodi, urakkatyyppi, sanktiolaji) VALUES ('Ei sanktiotyyppiä', null, '{teiden-hoito}', '{lupaussanktio, vaihtosanktio, testikeskiarvo-sanktio, tenttikeskiarvo-sanktio}');

