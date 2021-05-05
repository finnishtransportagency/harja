DELETE FROM pot2_mk_massatyyppi WHERE nimi =  'Pehmeät asfalttibetonit';
DELETE FROM pot2_mk_massatyyppi WHERE nimi =  'Kovat asfalttibetonit';
-- Fillerikiviaines haluaa yhden desimaalin, moni muu massa integerin
ALTER TABLE pot2_mk_massan_runkoaine
    ALTER COLUMN massaprosentti TYPE NUMERIC(3,1);