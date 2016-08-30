-- Lisää tarkemmat nime 4. tason kaatoluokille
UPDATE
  toimenpidekoodi taso_4
SET nimi = concat((SELECT taso_2.nimi
                   FROM toimenpidekoodi taso_2
                   WHERE
                     taso = 2 AND
                     NOT poistettu AND
                     id = (SELECT emo
                           FROM toimenpidekoodi
                           WHERE
                             taso = 3 AND
                             NOT poistettu AND
                             id = (SELECT emo
                                   FROM toimenpidekoodi
                                   WHERE
                                     taso = 4 AND
                                     NOT poistettu AND
                                     id = taso_4.id))),
                  ' - Ei yksilöity')
WHERE taso_4.nimi = 'Ei yksilöity' AND NOT taso_4.poistettu;
