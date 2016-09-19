-- Näitä ei enää haluta nähdä
DELETE FROM yhteyshenkilo
WHERE id IN (SELECT y.id
             FROM yhteyshenkilo y
               LEFT JOIN yhteyshenkilo_urakka yu ON yu.yhteyshenkilo = y.id
             WHERE rooli = 'Kunnossapitopäällikkö'
                   OR rooli = 'Sillanvalvoja'
                   OR rooli = 'Kelikeskus'
                   OR rooli = 'Tieliikennekeskus');
