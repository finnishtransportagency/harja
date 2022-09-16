INSERT INTO sanktiotyyppi (nimi, toimenpidekoodi, urakkatyyppi, sanktiolaji) 
VALUES ('Suolasakko',
        (SELECT id FROM toimenpidekoodi WHERE koodi = '23104'),
        '{hoito, teiden-hoito}',
        '{talvisuolan_ylitys, pohjavesisuolan_ylitys}');
