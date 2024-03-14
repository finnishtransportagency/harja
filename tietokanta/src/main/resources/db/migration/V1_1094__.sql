CREATE TABLE rahavaraus
(
    id   SERIAL PRIMARY KEY,
    nimi TEXT NOT NULL
);

CREATE TABLE rahavaraus_tehtava
(
    rahavaraus INT REFERENCES rahavaraus (id),
    tehtava    INT REFERENCES tehtava (id),
    PRIMARY KEY (rahavaraus, tehtava)
);

CREATE TABLE rahavaraus_urakka
(
    rahavaraus           INT REFERENCES rahavaraus (id),
    urakka               INT REFERENCES urakka (id),
    urakkakohtainen_nimi TEXT,
    PRIMARY KEY (rahavaraus, urakka)
);

ALTER TABLE kustannusarvioitu_tyo
    ADD COLUMN rahavaraus INT REFERENCES rahavaraus (id);
ALTER TABLE kulu_kohdistus
    ADD COLUMN rahavaraus INT REFERENCES rahavaraus (id);

-- TODO: Populoi taulut

INSERT INTO rahavaraus (nimi)
VALUES ('Äkilliset hoitotyöt'),
       ('Vahinkojen korjaukset'),
       ('Kannustinjärjestelmä'),
       ('Rahavaraus A'),
       ('Rahavaraus B - Äkilliset hoitotyöt'),
       ('Rahavaraus C - Vahinkojen korjaukset'),
       ('Rahavaraus D - Levähdys- ja P-alueet'),
       ('Rahavaraus E - Pysäkkikatokset'),
       ('Rahavaraus F - Meluesteet'),
       ('Rahavaraus G - Juurakkopuhdistamo'),
       ('Rahavaraus H - Aidat'),
       ('Rahavaraus I - Sillat ja laiturit'),
       ('Rahavaraus J - Tunnelien pienet korjaukset'),
       ('Rahavaraus K - Kannustinjärjestelmä');

WITH rahavaraus_a AS (SELECT id FROM rahavaraus WHERE nimi = 'Rahavaraus A')
INSERT
INTO rahavaraus_tehtava (rahavaraus, tehtava)
VALUES (rahavaraus_a.id,
        (SELECT id FROM tehtava WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi = 'Vakiokokoisten liikennemerkkien uusiminen, merkin vaihto tukirakenteineen')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Opastustaulun/-viitan uusiminen')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Opastustaulujen ja opastusviittojen uusiminen portaaliin')),
       -- Näyttäisi samalta tehtävältä eri tavalla kirjoitettuna. Laitetaan kuitenkin rahavaraukseen varalta.
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Opastustaulujen ja opastusviittojen uusiminen -portaalissa olevan viitan/opastetaulun uusiminen')),
       -- Näyttäisi samalta tehtävältä eri tavalla kirjoitettuna. Laitetaan kuitenkin rahavaraukseen varalta.
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Opastustaulujen ja opastusviittojen uusiminen -porttaalissa olevan viitan/opastetaulun uusiminen')),
       -- Näyttäisi samalta tehtävältä eri tavalla kirjoitettuna. Laitetaan kuitenkin rahavaraukseen varalta.
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                'Opastustaulujen ja opastusviittojen uusiminen -vanhan viitan/opastetaulun uusiminen')),
       -- Liikennemerkkipylvään tehostamismerkien uusiminen ei löydy
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                '')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                '')),
       (rahavaraus_a.id, (SELECT id
                          FROM tehtava
                          WHERE nimi =
                                '')),



SELECT *
FROM tehtava t
         LEFT JOIN tehtavaryhma tr ON t.tehtavaryhma = tr.id
         LEFT JOIN tehtavaryhmaotsikko tro ON tr.tehtavaryhmaotsikko_id = tro.id
WHERE tr.id = 323

SELECT * FROM tehtava where nimi ilike '%tolpp%'

SELECT * FROM toteuma t
    left join urakka u on t.urakka = u.id
LEFT join toteuma_tehtava tt on tt.toteuma = t.id
left join tehtava te on tt.toimenpidekoodi = te.id
where te.id in (6989, 1429, 1424)
and u.tyyppi = 'teiden-hoito';


