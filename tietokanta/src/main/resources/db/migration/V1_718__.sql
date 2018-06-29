-- Tielupasanomissa tulee ELY-nimiä, jotka eivät ole Harjan elyissä mukana.
ALTER TYPE organisaatiotyyppi ADD VALUE 'hallintayksikko-tilu';


INSERT INTO organisaatio (nimi, lyhenne, elynumero,  liikennemuoto, tyyppi, luoja, luotu, poistettu)
VALUES ('Kainuu', null, 14, 'T', 'hallintayksikko-tilu' , null, current_timestamp, false);

INSERT INTO organisaatio (nimi, lyhenne, elynumero, liikennemuoto, tyyppi, luoja, luotu, poistettu)
VALUES ('Ahvenanmaa', null, 16, 'T', 'hallintayksikko-tilu' , null, current_timestamp, false);

INSERT INTO organisaatio (nimi, lyhenne, elynumero, liikennemuoto, tyyppi, luoja, luotu, poistettu)
VALUES ('Etelä-Savo', null, 7,'T', 'hallintayksikko-tilu' , null, current_timestamp, false);

INSERT INTO organisaatio (nimi, lyhenne, elynumero, liikennemuoto, tyyppi, luoja, luotu, poistettu)
VALUES ('Häme', null, 4,'T', 'hallintayksikko-tilu' , null, current_timestamp, false);

INSERT INTO organisaatio (nimi, lyhenne, elynumero, liikennemuoto, tyyppi, luoja, luotu, poistettu)
VALUES ('Pohjanmaa', null, 12,'T', 'hallintayksikko-tilu' , null, current_timestamp, false);

INSERT INTO organisaatio (nimi, lyhenne, elynumero, liikennemuoto, tyyppi, luoja, luotu, poistettu)
VALUES ('Pohjois-Karjala', null, 9,'T', 'hallintayksikko-tilu' , null, current_timestamp, false);

INSERT INTO organisaatio (nimi, lyhenne, elynumero, liikennemuoto, tyyppi, luoja, luotu, poistettu)
VALUES ('Satakunta', null, 3,'T', 'hallintayksikko-tilu' , null, current_timestamp, false);

