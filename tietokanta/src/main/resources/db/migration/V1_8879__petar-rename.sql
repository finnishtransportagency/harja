-- koodisto konversio taulukko, intengration takia

CREATE TABLE koodisto_konversio (
    id TEXT PRIMARY KEY NOT NULL,
    nimi TEXT NOT NULL,
    koodisto TEXT
);

CREATE TABLE koodisto_konversio_koodit (
    koodisto_konversio_id TEXT NOT NULL REFERENCES koodisto_konversio(id),
    harja_koodi INT NOT NULL,
    koodi TEXT NOT NULL,
    PRIMARY KEY (koodisto_konversio_id, harja_koodi)
);

INSERT INTO koodisto_konversio (id, nimi, koodisto)
VALUES ('v/at', 'velho/alusta-toimenpide', 'pot2_mk_alusta_toimenpide'),
       ('v/mrk', 'velho/max-raekoko', NULL),
       ('v/sm', 'velho/sideaine-materiaali', 'pot2_mk_sideainetyyppi');

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/at',  1, 'tienrakennetoimenpide/trtp32'), -- MV
       ('v/at', 11, 'tienrakennetoimenpide/trtp33'), -- BEST
       ('v/at', 12, 'tienrakennetoimenpide/trtp34'), -- VBST
       ('v/at', 13, 'tienrakennetoimenpide/trtp35'), -- REST
       ('v/at', 14, 'tienrakennetoimenpide/trtp36'), -- SST
       ('v/at', 15, 'tienrakennetoimenpide/trtp37'), -- MHST
       ('v/at', 16, 'tienrakennetoimenpide/trtp38'), -- KOST
       ('v/at', 23, 'tienrakennetoimenpide/trtp39'), -- MS
       ('v/at', 24, 'tienrakennetoimenpide/trtp26'), -- SJYR
       ('v/at', 31, 'tienrakennetoimenpide/trtp27'), -- TASK
       ('v/at', 32, 'tienrakennetoimenpide/trtp28'), -- TAS
       ('v/at', 41, 'tienrakennetoimenpide/trtp29'), -- TJYR
       ('v/at', 42, 'tienrakennetoimenpide/trtp30'), -- LJYR
       ('v/at', 43, 'tienrakennetoimenpide/trtp31'), -- RJYR
       ('v/at',  2, 'tienrakennetoimenpide/NULL_2'), -- AB läytyy paallyste-ja-pintarakenne/paallystetyyppi
       ('v/at', 21, 'tienrakennetoimenpide/NULL_21'), -- ABK läytyy paallyste-ja-pintarakenne/paallystetyyppi
       ('v/at', 22, 'tienrakennetoimenpide/NULL_22'), -- ABS läytyy paallyste-ja-pintarakenne/paallystetyyppi
       ('v/at',  3, 'tienrakennetoimenpide/trtp01'), -- Verkko, velhossa se on LTA(?)
       ('v/at',  4, 'tienrakennetoimenpide/trtp08'); -- REM-TAS

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/mrk',  5, 'runkoaineen-maksimi-raekoko/rmr01'),
       ('v/mrk',  8, 'runkoaineen-maksimi-raekoko/rmr02'),
       ('v/mrk', 11, 'runkoaineen-maksimi-raekoko/rmr03'),
       ('v/mrk', 16, 'runkoaineen-maksimi-raekoko/rmr04'),
       ('v/mrk', 22, 'runkoaineen-maksimi-raekoko/rmr05'),
       ('v/mrk', 31, 'runkoaineen-maksimi-raekoko/rmr06');

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/sm',  1, 'sideaineen-materiaali/sm01'), -- Bitumi, 20/30
       ('v/sm',  2, 'sideaineen-materiaali/sm02'), -- Bitumi, 35/50
       ('v/sm',  3, 'sideaineen-materiaali/sm03'), -- Bitumi, 50/70
       ('v/sm',  4, 'sideaineen-materiaali/sm04'), -- Bitumi, 70/100
       ('v/sm',  5, 'sideaineen-materiaali/sm05'), -- Bitumi, 100/150
       ('v/sm',  6, 'sideaineen-materiaali/sm06'), -- Bitumi, 160/220
       ('v/sm',  7, 'sideaineen-materiaali/sm07'), -- Bitumi, 250/330
       ('v/sm',  8, 'sideaineen-materiaali/sm08'), -- Bitumi, 330/430
       ('v/sm',  9, 'sideaineen-materiaali/sm09'), -- Bitumi, 500/650
       ('v/sm', 10, 'sideaineen-materiaali/sm10'), -- Bitumi, 650/900
       ('v/sm', 11, 'sideaineen-materiaali/sm11'), -- Bitumi, V1500
       ('v/sm', 12, 'sideaineen-materiaali/sm12'), -- Bitumi, V3000
       ('v/sm', 13, 'sideaineen-materiaali/sm13'), -- Polymeerimodifioitu bitumi, PMB 75/130-65
       ('v/sm', 14, 'sideaineen-materiaali/sm14'), -- Polymeerimodifioitu bitumi, PMB 75/130-70
       ('v/sm', 15, 'sideaineen-materiaali/sm15'), -- Polymeerimodifioitu bitumi, PMB 40/100-70
       ('v/sm', 16, 'sideaineen-materiaali/sm16'), -- Polymeerimodifioitu bitumi, PMB 40/100-75
       ('v/sm', 17, 'sideaineen-materiaali/sm17'), -- Bitumiliuokset ja fluksatut bitumit, BL0
       ('v/sm', 18, 'sideaineen-materiaali/sm18'), -- Bitumiliuokset ja fluksatut bitumit, BL5
       ('v/sm', 19, 'sideaineen-materiaali/sm19'), -- Bitumiliuokset ja fluksatut bitumit, BL2Bio
       ('v/sm', 20, 'sideaineen-materiaali/sm20'), -- Bitumiemulsiot, BE-L
       ('v/sm', 21, 'sideaineen-materiaali/sm21'), -- Bitumiemulsiot, PBE-L
       ('v/sm', 22, 'sideaineen-materiaali/sm22'), -- Bitumiemulsiot, BE-SIP
       ('v/sm', 23, 'sideaineen-materiaali/sm23'), -- Bitumiemulsiot, BE-SOP
       ('v/sm', 24, 'sideaineen-materiaali/sm24'), -- Bitumiemulsiot, BE-AB
       ('v/sm', 25, 'sideaineen-materiaali/sm25'), -- Bitumiemulsiot, BE-PAB
       ('v/sm', 26, 'sideaineen-materiaali/NULL_26'), -- KF, Kalkkifilleri
       ('v/sm', 27, 'sideaineen-materiaali/sm26'); -- Muu, erikoisbitumi