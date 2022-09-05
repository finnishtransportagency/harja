ALTER TYPE sanktiolaji ADD VALUE 'talvisuolan_ylitys'; -- Talvisuolan kokonaiskäytön ylitys
ALTER TYPE sanktiolaji ADD VALUE 'pohjavesisuolan_ylitys'; -- Pohjavesialueiden suolankäytön ylitys

INSERT INTO sanktiotyyppi (nimi, toimenpidekoodi, urakkatyyppi, sanktiolaji) 
VALUES ('Suolasakko',
        (SELECT id FROM toimenpidekoodi WHERE koodi = '23104'),
        '{hoito, teiden-hoito}',
        '{talvisuolan_ylitys, pohjavesisuolan_ylitys}');
