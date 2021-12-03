-- lisää koodistoja, intengration takia

ALTER TABLE koodisto_konversio_koodit
    ALTER COLUMN harja_koodi TYPE TEXT;

INSERT INTO koodisto_konversio (id, nimi, koodisto)
VALUES ('v/toimenpiteen-kohdeluokka', 'velho/alusta-toimenpide', 'pot2_mk_alusta_toimenpide'),
       ('v/kkm', 'velho/kantavan-kerroksen-materiaali', 'pot2_mk_mursketyyppi'),
       ('v/skkr', 'velho/kantavan-kerroksen-rakeisuus', 'pot2_mk_urakan_murske'),
       ('v/skki', 'velho/kantavan-kerroksen-iskunkestavyys', 'pot2_mk_urakan_murske'),
       ('v/m', 'velho/materiaali', 'pot2_mk_runkoainetyyppi');

DELETE FROM koodisto_konversio_koodit WHERE koodisto_konversio_id = 'v/mut';
DELETE FROM koodisto_konversio WHERE id = 'v/mut';

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/kkm',  '1', 'kantavan-kerroksen-materiaali/kkm03'), -- Kalliomurske
       ('v/kkm',  '6', 'kantavan-kerroksen-materiaali/NULL-6'), -- Muu
       ('v/kkm',  '4', 'kantavan-kerroksen-materiaali/kkm05'), -- (UUSIO) Betonimurske I
       ('v/kkm',  '5', 'kantavan-kerroksen-materiaali/NULL-5'), -- (UUSIO) Betonimurske II
       ('v/kkm',  '3', 'kantavan-kerroksen-materiaali/kkm07'), -- (UUSIO) RA, Asfalttirouhe (velhossa löytyy "pintauksen-uusiomateriaali/pu")
       ('v/kkm',  '2', 'kantavan-kerroksen-materiaali/kkm04'); -- Soramurske

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/skkr',  '0/32', 'kantavan-kerroksen-rakeisuus/skkr01'),
       ('v/skkr',  '0/40', 'kantavan-kerroksen-rakeisuus/skkr02'),
       ('v/skkr',  '0/45', 'kantavan-kerroksen-rakeisuus/skkr03'),
       ('v/skkr',  '0/56', 'kantavan-kerroksen-rakeisuus/skkr04'),
       ('v/skkr',  '0/63', 'kantavan-kerroksen-rakeisuus/skkr05');

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/skki',  'LA30', 'kantavan-kerroksen-iskunkestavyys/skki01'),
       ('v/skki',  'LA35', 'kantavan-kerroksen-iskunkestavyys/skki02'),
       ('v/skki',  'LA40', 'kantavan-kerroksen-iskunkestavyys/skki03');

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
VALUES ('v/m', '1', 'materiaali/m26'), -- Kiviaines PETAR: velhossa on "kivi"
       ('v/m', '2', 'materiaali/m01'), -- Asfalttirouhe
       ('v/m', '3', 'materiaali/m26'), -- Erikseen lisättävä fillerikiviaines PETAR: sama komentti kuin 1
       ('v/m', '4', 'materiaali/m09'), -- Masuunikuonajauhe (löytyy seosaine/sa03) PETAR: velhossa on "hiekka"
       ('v/m', '5', 'materiaali/m05'), -- Ferrokromikuona (OKTO) PETAR: velhossa on "Ferrokromikuonahiekka"
       ('v/m', '6', 'materiaali/m16'), -- Teräskuona PETAR: velhossa on "Teräskuonamurske"
       ('v/m', '7', 'materiaali/m20'); -- Muu

INSERT INTO koodisto_konversio_koodit (koodisto_konversio_id, harja_koodi, koodi)
    VALUES ('v/toimenpiteen-kohdeluokka',  1, 'paallysrakennekerrokset/kantavat-kerrokset'), -- MV (trtp32)
           ('v/toimenpiteen-kohdeluokka', 11, 'paallysrakennekerrokset/kantavat-kerrokset'), -- BEST (trtp33)
           ('v/toimenpiteen-kohdeluokka', 12, 'paallysrakennekerrokset/kantavat-kerrokset'), -- VBST (trtp34)
           ('v/toimenpiteen-kohdeluokka', 13, 'paallysrakennekerrokset/kantavat-kerrokset'), -- REST (trtp35)
           ('v/toimenpiteen-kohdeluokka', 14, 'paallysrakennekerrokset/kantavat-kerrokset'), -- SST (trtp36)
           ('v/toimenpiteen-kohdeluokka', 15, 'paallysrakennekerrokset/kantavat-kerrokset'), -- MHST (trtp37)
           ('v/toimenpiteen-kohdeluokka', 16, 'paallysrakennekerrokset/kantavat-kerrokset'), -- KOST (trtp38)
           ('v/toimenpiteen-kohdeluokka', 23, 'paallysrakennekerrokset/kantavat-kerrokset'), -- MS (trtp39)
           ('v/toimenpiteen-kohdeluokka', 24, 'paallysrakennekerrokset/kantavat-kerrokset'), -- SJYR (trtp26)
           ('v/toimenpiteen-kohdeluokka', 31, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- TASK (trtp27)
           ('v/toimenpiteen-kohdeluokka', 32, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- TAS (trtp28)
           ('v/toimenpiteen-kohdeluokka', 41, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- TJYR (trtp29)
           ('v/toimenpiteen-kohdeluokka', 42, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- LJYR (trtp30)
           ('v/toimenpiteen-kohdeluokka', 43, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- RJYR (trtp31)
           ('v/toimenpiteen-kohdeluokka',  2, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- AB (LTA velhossa)
           ('v/toimenpiteen-kohdeluokka', 21, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- ABK (LTA velhossa)
           ('v/toimenpiteen-kohdeluokka', 22, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- ABS (LTA velhossa)
           ('v/toimenpiteen-kohdeluokka',  3, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'), -- Verkko (LTA velhossa)
           ('v/toimenpiteen-kohdeluokka',  4, 'paallyste-ja-pintarakenne/sidotut-paallysrakenteet'); -- REM-TAS (trtp08)

-- ABx alusta toimenpiteet ovat velhossa LTA (toistaiseksi)
UPDATE koodisto_konversio_koodit
   SET koodi = 'tienrakennetoimenpide/trtp01'
 WHERE koodisto_konversio_id = 'v/at' AND
       harja_koodi IN ('2', '21', '22');

UPDATE pot2_mk_alusta_toimenpide
   SET lyhenne = 'LTA '||lyhenne
 WHERE koodi IN (2, 21, 22);
