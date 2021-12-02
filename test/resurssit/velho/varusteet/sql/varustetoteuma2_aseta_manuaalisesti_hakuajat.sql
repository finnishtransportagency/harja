-- noinspection SqlWithoutWhere

DELETE
FROM varustetoteuma2_viimeisin_hakuaika_kohdeluokalle;

INSERT INTO varustetoteuma2_viimeisin_hakuaika_kohdeluokalle
    (kohdeluokka, viimeisin_hakuaika)
VALUES ('varusteet/kaiteet', DATE('2021-06-19')), -- 29 kpl
       ('varusteet/tienvarsikalusteet', DATE('2021-08-18')), -- 8 kpl
       ('varusteet/liikennemerkit', DATE('2021-06-19')), -- 3 kpl
       ('varusteet/rumpuputket', DATE('2021-06-07')), -- 39 kpl
       ('varusteet/kaivot', DATE('2021-05-28')), -- 375 kpl
       ('varusteet/reunapaalut', DATE('2021-05-15')), -- 2 kpl
       ('tiealueen-poikkileikkaus/luiskat', DATE('2021-06-18')), -- 54 (tl514) + 2 (518) *
       ('varusteet/aidat', DATE('2021-05-31')), -- 1 kpl
       ('varusteet/portaat', DATE('2021-05-31')), -- 1 kpl
       ('tiealueen-poikkileikkaus/erotusalueet', DATE('2021-06-29')), -- 2 (TL518) + 91 (TL165)
       ('varusteet/puomit-sulkulaitteet-pollarit', DATE('2021-06-17')), -- 2 kpl
       ('varusteet/reunatuet', DATE('2021-06-07')), -- 28 kpl
       ('ymparisto/viherkuviot', DATE('2022-07-02')); -- **

-- * Velhon migraatiostatus TL514 (ja TL518) on 2021-11-30 vielä kesken.
-- ** ei löytynyt pienempää joukkoa antavaa jalkeen rajausta

DELETE FROM varustetoteuma2;