-- ***********************************************
-- VATU-TURVALAITTEET
-- ***********************************************

INSERT INTO vatu_turvalaite (
  turvalaitenro, nimi, koordinaatit, sijainti, tyyppi, tarkenne, tila, vah_pvm, toimintatila, rakenne, navigointilaji, valaistu, omistaja, turvalaitenro_aiempi, paavayla, vaylat, geometria, luoja, luotu, muokkaaja, muokattu)
VALUES
  (666666, 'Sillviken alempi', 'POINT (421544.60804728785 6689983.7247258555)', 'Sillvikenin pohjukassa', 'Linjamerkki', 'KIINTEÄ', 'VAHVISTETTU', '2016-12-07', 'Jatkuva',
   '', 'Ei sovellettavissa', true, 'Liikennevirasto', 0, '5151: Tolkkisten väylä', '{5150,5151}', '0101000000BFF2A36EA2BA19418EE861EE2F855941', 'Testidata', current_timestamp, null, null);


INSERT INTO vatu_turvalaite (
  turvalaitenro, nimi, koordinaatit, sijainti, tyyppi, tarkenne, tila, vah_pvm, toimintatila, rakenne, navigointilaji, valaistu, omistaja, turvalaitenro_aiempi, paavayla, vaylat, geometria, luoja, luotu, muokkaaja, muokattu)
VALUES
  (666667, 'Klobbudden läntinen alempi', 'POINT (418961.9204654185 6684132.571862175)', 'Klobbuddenin E-osassa.', 'Linjamerkki', 'KIINTEÄ', 'VAHVISTETTU', '2016-12-07', 'Jatkuva',
           '', 'Ei sovellettavissa', true, 'Liikennevirasto', 0, '5105: Kalvön 7,0m väylä', '{4007,5105}', '0101000000967C8EAE47921941CF639924797F5941', 'Testidata', current_timestamp, null, null);

