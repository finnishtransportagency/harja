DELETE FROM pot2_mk_massatyyppi WHERE nimi =  'Pehmeät asfalttibetonit';
DELETE FROM pot2_mk_massatyyppi WHERE nimi =  'Kovat asfalttibetonit';
ALTER TABLE pot2_mk_massatyyppi ADD COLUMN jarjestys INTEGER;

-- VHAR-4424 eniten käytetyt massatyypit ensin
UPDATE pot2_mk_massatyyppi set jarjestys = 1 WHERE nimi = 'AB, Asfalttibetoni';
UPDATE pot2_mk_massatyyppi set jarjestys = 2 WHERE nimi = 'SMA, Kivimastiksiasfaltti';
UPDATE pot2_mk_massatyyppi set jarjestys = 3 WHERE nimi = 'PAB-B, Pehmeät asfalttibetonit';
UPDATE pot2_mk_massatyyppi set jarjestys = 4 WHERE nimi = 'PAB-V, Pehmeät asfalttibetonit';
UPDATE pot2_mk_massatyyppi set jarjestys = 5 WHERE nimi = 'ABK, Kantavan kerroksen AB';

--sitten loput aakkosjärjestyksessä
UPDATE pot2_mk_massatyyppi set jarjestys = 6 WHERE nimi = 'AA, Avoin asfaltti';
UPDATE pot2_mk_massatyyppi set jarjestys = 7 WHERE nimi = 'ABS, Sidekerroksen AB';
UPDATE pot2_mk_massatyyppi set jarjestys = 8 WHERE nimi = 'ABtiivis';
UPDATE pot2_mk_massatyyppi set jarjestys = 9 WHERE nimi = 'BET, Betoni';
UPDATE pot2_mk_massatyyppi set jarjestys = 10 WHERE nimi = 'EA, Epäjatkuva asfaltti (poistunut)';
UPDATE pot2_mk_massatyyppi set jarjestys = 11 WHERE nimi = 'EAB, Asfalttibetoni';
UPDATE pot2_mk_massatyyppi set jarjestys = 12 WHERE nimi = 'EABK, Kantavan kerroksen EAB';
UPDATE pot2_mk_massatyyppi set jarjestys = 13 WHERE nimi = 'EPAB-B, Pehmeät E asfalttibetonit';
UPDATE pot2_mk_massatyyppi set jarjestys = 14 WHERE nimi = 'EPAB-V, Pehmeät asfalttibetonit';
UPDATE pot2_mk_massatyyppi set jarjestys = 15 WHERE nimi = 'Ei tietoa';
UPDATE pot2_mk_massatyyppi set jarjestys = 16 WHERE nimi = 'Komposiittiasfaltti';
UPDATE pot2_mk_massatyyppi set jarjestys = 17 WHERE nimi = 'PAB-O, Pehmeät asfalttibetonit';
UPDATE pot2_mk_massatyyppi set jarjestys = 18 WHERE nimi = 'VA, Valuasfaltti';


-- Fillerikiviaines haluaa yhden desimaalin, moni muu massa integerin
ALTER TABLE pot2_mk_massan_runkoaine
    ALTER COLUMN massaprosentti TYPE NUMERIC(4,1);
ALTER TABLE pot2_mk_massan_sideaine
    ALTER COLUMN pitoisuus TYPE NUMERIC(4,1);
ALTER TABLE pot2_mk_massan_lisaaine
    ALTER COLUMN pitoisuus TYPE NUMERIC(4,1);
