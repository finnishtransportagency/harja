-- lisää koodistoja, intengration takia

ALTER TABLE harja.public.koodisto_konversio_koodit
    ALTER COLUMN harja_koodi TYPE TEXT;

INSERT INTO koodisto_konversio (id, nimi, koodisto)
VALUES ('v/kkm', 'velho/kantavan-kerroksen-materiaali', 'pot2_mk_mursketyyppi'),
       ('v/skkr', 'velho/kantavan-kerroksen-rakeisuus', 'pot2_mk_urakan_murske'),
       ('v/skki', 'velho/kantavan-kerroksen-iskunkestavyys', 'pot2_mk_urakan_murske');

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
