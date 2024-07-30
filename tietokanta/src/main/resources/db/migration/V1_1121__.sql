UPDATE tehtava
SET emo = (SELECT id FROM toimenpide WHERE koodi = '23116') -- Liikenneympäristön hoito laaja tpi
WHERE nimi IN ('Liikennemerkkipylvään tehostamismerkkien uusiminen',
               'Maakivien (< 1 m3) poisto päällystetyltä tieltä',
               'Muut päällysteiden paikkaukseen liittyvät työt',
               'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen',
               'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen',
               'Meluesteiden pienten vaurioiden korjaaminen',
               'Juurakkopuhdistamo, selkeytys- ja hulevesiallas sekä -painanne',
               'Aitojen vaurioiden korjaukset',
               'Siltakeilojen sidekiveysten purkaumien, suojaverkkojen ja kosketussuojaseinien pienet korjaukset',
               'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt');
