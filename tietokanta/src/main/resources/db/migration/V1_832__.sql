-- Luodaan apufunktioita partitiointihommiin...
CREATE OR REPLACE FUNCTION validoi_hoitokauden_alkupvm(hk_alkupvm DATE)
    RETURNS VOID AS
$$
BEGIN
    IF (SELECT EXTRACT (DAY FROM hk_alkupvm) != 1) THEN
        RAISE EXCEPTION 'Kvartaalin alkupvm:n on oltava kuun ensimmäinen päivä.';
    END IF;
    IF (SELECT EXTRACT (MONTH FROM hk_alkupvm) NOT IN (1, 4, 7, 10)) THEN
        RAISE EXCEPTION 'Kvartaalin alkupvm:n kuukauden on oltava 1, 4, 7 tai 10.';
    END IF;
    IF (SELECT EXTRACT (YEAR FROM hk_alkupvm) NOT BETWEEN 2019 AND 2050) THEN
        RAISE EXCEPTION 'Kvartaalin vuoden oltava välillä 2019 ja 2050';
    END IF;
END
$$
    LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION luo_kvartaalipartitio(kantataulu TEXT, q_alkupvm DATE)
    RETURNS VOID AS
$$
DECLARE
    partitio text;
BEGIN
    partitio := kantataulu || TO_CHAR(q_alkupvm, '_YY_"q"Q');
    RAISE NOTICE 'Luodaan kantatauluun % partitio nimeltä: %', kantataulu, partitio;
    PERFORM validoi_hoitokauden_alkupvm(q_alkupvm);

    -- luo hoitokauden Q4 partitio
    EXECUTE 'CREATE TABLE IF NOT EXISTS ' || partitio || ' PARTITION OF ' || kantataulu ||
            ' FOR VALUES FROM ('''|| q_alkupvm || ''') TO ('''|| date_trunc('quarter', q_alkupvm + '3 months'::interval)||''')';

END
$$
    LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION luo_hoitokauden_partitiot(kantataulu TEXT, hk_alkupvm DATE)
    RETURNS VOID AS
$$
DECLARE
    partitio text;
BEGIN
    partitio := kantataulu || TO_CHAR(hk_alkupvm, '_YY_"q"Q');

    PERFORM validoi_hoitokauden_alkupvm(hk_alkupvm);

    PERFORM luo_kvartaalipartitio(kantataulu, hk_alkupvm::DATE);
    PERFORM luo_kvartaalipartitio(kantataulu, DATE(date_trunc('quarter', hk_alkupvm + '3 months'::interval)));
    PERFORM luo_kvartaalipartitio(kantataulu, DATE(date_trunc('quarter', hk_alkupvm + '6 months'::interval)));
    PERFORM luo_kvartaalipartitio(kantataulu, DATE(date_trunc('quarter', hk_alkupvm + '9 months'::interval)));

END
$$
    LANGUAGE plpgsql;


-- Partitioi toteumataulu
ALTER TABLE toteuma DROP CONSTRAINT toteuma_pkey CASCADE;
-- tämä cascade pudottaa seuraavat foreign key viittaukset jotka myöhemmin palautettava
-- constraint toteuma_tehtava_toteuma_fkey on table toteuma_tehtava depends on index toteuma_pkey
-- constraint toteuma_materiaali_toteuma_fkey on table toteuma_materiaali depends on index toteuma_pkey
-- constraint varustetoteuma_toteuma_fkey on table varustetoteuma depends on index toteuma_pkey
-- constraint toteuman_reittipisteet_toteuma_fkey on table toteuman_reittipisteet depends on index toteuma_pkey
-- constraint toteuma_liite_toteuma_fkey on table toteuma_liite depends on index toteuma_pkey
-- constraint paikkaustoteuma_toteuma-id_fkey on table paikkaustoteuma depends on index toteuma_pkey

ALTER TABLE toteuma DROP CONSTRAINT uniikki_ulkoinen_id_luoja_urakka;

-- Partitioiduissa tauluissa partitiointiavaimen (alkanut) oltava läsnä primaty keyssä ja uniikkirajoitteessa
ALTER TABLE toteuma ADD PRIMARY KEY (id, alkanut);
CREATE UNIQUE INDEX ON toteuma (id, alkanut);

ALTER TABLE toteuma
    ADD CONSTRAINT uniikki_ulkoinen_id_luoja_urakka UNIQUE (ulkoinen_id, luoja, urakka, alkanut);

ALTER TABLE toteuma rename to toteuma_vanha;

CREATE TABLE toteuma (LIKE toteuma_vanha INCLUDING ALL)
PARTITION BY RANGE (alkanut);

-- ennen 1.10.2016 oli niin pieni määrä rivejä, että siitä yksi nippu
CREATE TABLE toteuma_before_2016_q4 PARTITION OF toteuma
FOR VALUES FROM (MINVALUE) TO ('2016-10-01');

-- rivit jotka eivät jostain syystä osu mihinkään muuhun partitioon
CREATE TABLE toteuma_default PARTITION OF toteuma DEFAULT;

-- ei enää tarpeen partitioida vanhoja hoitokausia niin tarkasti, niistä isot niput
CREATE TABLE toteuma_hk_16_17 PARTITION OF toteuma
    FOR VALUES FROM ('2016-10-01') TO ('2017-10-01');

CREATE TABLE toteuma_hk_17_18 PARTITION OF toteuma
    FOR VALUES FROM ('2017-10-01') TO ('2018-10-01');

CREATE TABLE toteuma_hk_18_19 PARTITION OF toteuma
    FOR VALUES FROM ('2018-10-1') TO ('2019-10-01');

SELECT * FROM luo_hoitokauden_partitiot('toteuma', '2019-10-01'::DATE);
SELECT * FROM luo_hoitokauden_partitiot('toteuma', '2020-10-01'::DATE);
SELECT * FROM luo_hoitokauden_partitiot('toteuma', '2021-10-01'::DATE);


--INSERT INTO toteuma SELECT * FROM toteuma_vanha;
-- INSERTOIDAAN DATA uuteen partitioituun tauluun palasina, attach partition avulla, vältämme downtimen ja pahat lukot
-- tauluissa joille kutsutaan attach partition, on oltava sillä hetkellä check constraint, joka on sama kuin
-- partitiointiavaimet, eli tietty date väli. Tämä poistaa parent tauluun tulevan ACCESS EXCLUSIVE lukon tarpeen
-- ja nopeuttaa merkittävästi migraation tekemistä, koska taulua ei tarvitse skannata kokonaan

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '0001-01-01' AND ('2016-10-01') returning *
  )
INSERT INTO toteuma SELECT * FROM x;

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '2016-10-01' AND '2017-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '2017-10-01' AND '2018-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '2018-10-01' AND '2019-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '2019-10-01' AND '2020-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '2019-10-01' AND '2020-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;


  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '2020-10-01' AND '2021-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut BETWEEN '2021-10-01' AND '2022-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;

  WITH x AS (
      DELETE FROM toteuma_vanha WHERE alkanut >= '2022-10-01' returning *
  )
INSERT INTO toteuma SELECT * FROM x;