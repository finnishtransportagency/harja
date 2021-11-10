-- lisää koodistoja, intengration takia

INSERT INTO koodisto_konversio (id, nimi, koodisto)
VALUES ('v/pt-ab', 'velho/alusta-toimenpide-ABx', 'pot2_mk_alusta_toimenpide ABx');

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/pt-ab',  '2', 'paallystetyyppi/pt02'), -- AB
       ('v/pt-ab',  '21', 'paallystetyyppi/pt05'), -- ABK
       ('v/pt-ab',  '22', 'paallystetyyppi/pt06'); -- ABS


CREATE OR REPLACE VIEW pot2_massan_tiedot AS
    SELECT um.id,
           um.nimen_tarkenne as "nimen-tarkenne",
           um.tyyppi as "paallystetyyppi",
           um.max_raekoko as "max-raekoko",
           um.kuulamyllyluokka,
           um.litteyslukuluokka,
           (SELECT massaprosentti FROM pot2_mk_massan_runkoaine asfrouhe WHERE
                   asfrouhe.pot2_massa_id = um.id AND
                   asfrouhe.tyyppi = (SELECT koodi
                                        FROM pot2_mk_runkoainetyyppi
                                       WHERE nimi = 'Asfalttirouhe')) as "rc%",
           (SELECT array_to_string(array_agg(asfrouhe.tyyppi), ', ')
              FROM pot2_mk_massan_runkoaine asfrouhe
             WHERE asfrouhe.pot2_massa_id = um.id) as "runkoaine-koodit",
           mr.esiintyma,
           mr.kuulamyllyarvo as "km-arvo",
           mr.litteysluku as "muotoarvo",
           mla.tyyppi as "lisaaine-koodi",
           (SELECT array_to_string(array_agg(p2ml.nimi||': '||ml.pitoisuus||'%'), ', ')
            FROM pot2_mk_massan_lisaaine ml
                     JOIN pot2_mk_lisaainetyyppi p2ml on ml.tyyppi = p2ml.koodi
            WHERE ml.pot2_massa_id = um.id) as "lisaaineet",
           ms.pitoisuus,
           ms.tyyppi as "sideainetyyppi"
    FROM pot2_mk_urakan_massa um
    LEFT JOIN pot2_mk_massan_runkoaine mr ON mr.id = (SELECT p2mmr.id
                                                        FROM pot2_mk_massan_runkoaine p2mmr
                                                       WHERE p2mmr.pot2_massa_id = um.id
                                                       ORDER BY p2mmr.massaprosentti DESC LIMIT 1)
    LEFT JOIN pot2_mk_massan_lisaaine mla ON mla.id = (SELECT p2mma.id
                                                         FROM pot2_mk_massan_lisaaine p2mma
                                                        WHERE p2mma.pot2_massa_id = um.id
                                                        ORDER BY p2mma.pitoisuus DESC LIMIT 1)
    LEFT JOIN pot2_mk_massan_sideaine ms ON ms.id = (SELECT p2mms.id
                                                       FROM pot2_mk_massan_sideaine p2mms
                                                      WHERE p2mms.pot2_massa_id = um.id AND p2mms."lopputuote?" IS TRUE
                                                      LIMIT 1);

