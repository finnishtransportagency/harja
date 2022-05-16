/*326	2.2 LIIKENNEYMPÄRISTÖN HOITO / Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito	Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito	ylataso
1069	2.2 LIIKENNEYMPÄRISTÖN HOITO / Tie-, levähdys- ja liitännäisalueiden Puhtaanapito ja kalusteiden hoito	Tie-, levähdys- ja liitännäisalueiden Puhtaanapito ja kalusteiden hoito	ylataso

327	2.2 LIIKENNEYMPÄRISTÖN HOITO / Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito	Välitaso Tie-, levähdys- ja liitännäisalueiden puhtaanapito ja kalusteiden hoito	valitaso
1070	2.2 LIIKENNEYMPÄRISTÖN HOITO / Tie-, levähdys- ja liitännäisalueiden Puhtaanapito ja kalusteiden hoito	Välitaso Tie-, levähdys- ja liitännäisalueiden Puhtaanapito ja kalusteiden hoito	valitaso
*/


select *
from tehtavaryhma
where emo = 327 -- pienellä kirjoitettua alaryhmää ei ole, mutta korjaus päivittää sen
select *
from toimenpidekoodi
where tehtavaryhma


select *
from urakka
where nimi like ('%Test%')

select *
from toimenpideinstanssi
where urakka = 258
  and toimenpide in (
    select * from toimenpidekoodi where id in (select toimenpide from toimenpideinstanssi where urakka = 361)

/*
    16402	MHU KORVAUSINVESTOINTI	14301
    13720	PÄÄLLYSTEIDEN PAIKKAUS	20107
    700	MHU YLLÄPITO	20191
    618	TALVIHOITO	23104
    612	LIIKENNEYMPÄRISTÖN HOITO	23116
    608	SORATEIDEN HOITO	23124
    601	MHU HOIDONJOHTO	23151
*/


select *
from urakka
where nimi like ('%Vantaa%')

select *
from toimenpidekoodi
where nimi like ('%Päällys%')
select *
from toimenpidekoodi
where id = 13720

select t.id, t2.nimi, t.alkanut
from toteuma t
         join toteuma_tehtava tt on t.id = tt.toteuma
    -- join toteuma_materiaali tm on t.id = tm.toteuma
         join toimenpidekoodi t2 on tt.toimenpidekoodi = t2.id and t2.id = 17299
where t.alkanut > '2019-04-01'
order by t.alkanut desc
    Kesäsuola
    (CaCl2, materiaali)
    Sorastus
Liikenteen varmistaminen kelirikkokohteissa
Reunantäyttö
Liukkaudentorjunta suolaamalla (materiaali)
Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)
Liukkaudentorjunta hiekoituksella (materiaali)
Ennalta arvaamattomien kuljetusten avustaminen, 17299, ei

select *
from toteuma_materiaali
where toteuma in (
    select toteuma
    from toteuma_tehtava
    where toimenpidekoodi = 1413)

select *
from toimenpidekoodi
where nimi like ('%Ennalta arvaamattomien kuljetusten%')
   or jarjestys = 28 -- vain yksi tehtävä 17299, ei löydy yhtään toteumaa tälle tehtävälle.
select *
from toimenpidekoodi
where nimi like ('%Liukkaudentorjunta hiekoituksella%')
   or jarjestys = 27 -- tehtäviä kolme: 17359 Linjahiekoitus 1367 ja Pistehiekoitus 1368. Toteumakirjaus linja- ja pistehiekoitukselle.
select *
from toimenpidekoodi
where nimi like ('%Kalium- tai natriumformiaatin käyttö%')
   or jarjestys = 26 -- vain yksi tehtävä 19910, ei toteumia. Materiaalitoteumat kirjautuvat tehtävälle Suolaus.
select *
from toimenpidekoodi
where nimi like ('%Suolaus%')
   or jarjestys = 25 -- Yksi tehtävä 1369, kaikki suolausmateriaalit tallentuvat tämän tehtävän taakse. Tämä tehtävä = liukkauden torjuntan suolaamalla (materiaali)
select *
from toimenpidekoodi
where nimi like ('%Reunantäyttö%')
   or jarjestys = 116
-- Neljä tehtävää 17365, Päällystettyjen teiden sr-pientareen täyttö 1412, Päällystettyjen teiden pientareiden täyttö 1413, Päällystettyjen teiden sorapientareen täyttö 7067,
-- osa reunantäyttötehtävistä näyttää turhalta, vain Päällystettyjen teiden sorapientareen täyttö ottaa vastaan apin kautta toteumia, käsin lisätään sitten toiselle, tämä lienee ongelma. KAIKILLE ON KIRJATTU TOTEUMIA.
-- toteumia on paljon enemmän kuin materiaalimääriä.
-- Pitäisikö tehdä excel kaikista näistä ja niiden ongelmista. Joo.


-- Ennalta arvaamattomien kuljetusten avustaminen
select t2.nimi, tt.id, tr.reittipisteet
from toteuma t
         join toteuma_tehtava tt on t.id = tt.toteuma
    -- join toteuma_materiaali tm on t.id = tm.toteuma
         join toteuman_reittipisteet tr on tt.toteuma = tr.toteuma
         join toimenpidekoodi t2 on tt.toimenpidekoodi = t2.id and t2.id in
                                                                   (17299, 17359, 1367, 1368, 19910, 1369, 17365, 1412,
                                                                    1413, 17365, 1412, 1413, 7067)
where t.alkanut > '2019-01-01'



select *
from toteuman_reittipisteet
where toteuma in (
                  38140595,
                  37700725,
                  37700465,
                  39712331,
                  38140554,
                  38140567,
                  40297002,
                  37696112,
                  37695515,
                  37714273,
                  38140517,
                  38140502)


select string_to_array('23', '23')

select *, reittipisteet[1].materiaalit[1].materiaalikoodi
from toteuman_reittipisteet
where toteuma = 37700465

select x.aika
from (select string_to_array(unnest(reittipisteet))::reittipistedata as x
      from toteuman_reittipisteet
      where toteuma = 37700465) as jee

select jee.rivi.*
from (select unnest(reittipisteet) as rivi from toteuman_reittipisteet where toteuma = 37700465) as jee


select ARRAY(select reittipisteet from toteuman_reittipisteet where toteuma = 37700465)

select *
from toteuma_tehtava
where id = 20025244
SELECT reittipisteet[4].materiaalit
FROm toteuman_reittipisteet
where toteuma = 20338396
SELECT reittipisteet[4].materiaalit[1].materiaalikoodi
FROm toteuman_reittipisteet
where toteuma = 20338396
SELECT reittipisteet[4].materiaalit[2]
FROm toteuman_reittipisteet
where toteuma = 20338396

select array_length(reittipisteet, 1)
from toteuman_reittipisteet
where toteuma = 37700465
select unnest(reittipisteet)
from toteuman_reittipisteet
where id = 37700465



select *
from unnest(array [array [1, 2], array [2, 3]]);

CREATE TYPE reittipiste_materiaali AS
(
    materiaalikoodi INTEGER,
    maara           NUMERIC
);

CREATE TYPE reittipiste_tehtava AS
(
    toimenpidekoodi INTEGER,
    maara           NUMERIC
);

CREATE TYPE reittipistedata AS
(
    aika               TIMESTAMP,
    sijainti           POINT,
    talvihoitoluokka   INTEGER,
    soratiehoitoluokka INTEGER,
    tehtavat           reittipiste_tehtava[],
    materiaalit        reittipiste_materiaali[]
);

CREATE TABLE toteuman_reittipisteet
(
    toteuma       INTEGER PRIMARY KEY REFERENCES toteuma (id),
    luotu         timestamp DEFAULT NOW(),
    reittipisteet reittipistedata[]
);



select reittitoteuman_materiaalit();

select nimi, indeksi
from urakka
where id in (314, 158, 258)
select *
from urakka
where sampoid in ('PR00048428', 'PR00048434')
select *
from organisaatio
where id = 30 TPU DHJ EPO ELY ET 1 2021, Peab Industri Oy, P  PR00048428

TPU DHJ EPO ELY ET 2 2021 Asfalttikallio Oy, P   PR00048434

select *
from toimenpidekoodi
where emo = 13720
select *
from urakka
where id = 361
select *
from kayttaja
where sukunimi = 'Peltoniemi'
select *
from organisaatio
where id = 8
select reittitoteuman_materiaalit();

/* -------------------- */

CREATE OR REPLACE FUNCTION reittitoteuman_materiaalit()
    RETURNS VOID AS
$$
DECLARE
    reitti      RECORD;
    i           INTEGER = 0;
    cnt         INTEGER = 0;
    tehtavat    VARCHAR[];
    materiaalit INT[];
BEGIN
    RAISE NOTICE '**** ALKU *****';
    FOR reitti IN (SELECT t2.nimi, tt.toteuma, tr.reittipisteet
                   FROM toteuma t
                            join toteuma_tehtava tt on t.id = tt.toteuma
                       -- join toteuma_materiaali tm on t.id = tm.toteuma
                            join toteuman_reittipisteet tr on tt.toteuma = tr.toteuma
                            join toimenpidekoodi t2 on tt.toimenpidekoodi = t2.id and t2.id in
                       /*(17299, 17359, 1367, 1368, 19910,
                        1369, 17365, 1412, 1413, 17365,
                        1412, 1413, 7067) */
                                                                                      (select id from toimenpidekoodi where emo = 13720)
                   WHERE t.alkanut > '2019-01-01')
        LOOP
            WHILE (i < array_length(reitti.reittipisteet, 1))
                LOOP
                    i := i + 1;
                    cnt := 1;
                    IF (reitti.nimi = ANY (tehtavat)) THEN
                    ELSE
                        RAISE NOTICE 'Lisätään tehtävä: %.', reitti.nimi;
                        tehtavat := array_append(tehtavat, reitti.nimi);
                    END IF;
                    WHILE (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi IS NOT NULL)
                        LOOP
                            RAISE NOTICE 'i %, cnt %, materiaalikoodi %', i, cnt, reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi;

                            IF (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = ANY (materiaalit)) THEN
                            ELSE
                                RAISE NOTICE 'Lisätään materiaali: %.', reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi;
                                materiaalit := array_append(materiaalit,
                                                            reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi);
                            END IF;
                            cnt := cnt + 1;
                        END LOOP;
                END LOOP;
        END LOOP;
    RAISE NOTICE '**** LOPPU *****';
END;
$$ LANGUAGE plpgsql;


-----------------------

/*
talvihoitoluokat
  [{:nimi "IsE"  :numero 1 :numero-str "1"}
   {:nimi "Is"  :numero 2 :numero-str "2"}
   {:nimi "I"   :numero 3 :numero-str "3"}
   {:nimi "Ib"  :numero 4 :numero-str "4"}
   {:nimi "TIb" :numero 5 :numero-str "5"} ; 1.10.2019 alkaen oltava Ic!
   {:nimi "II"  :numero 6 :numero-str "6"}
   {:nimi "III" :numero 7 :numero-str "7"}
   {:nimi "L"  :numero 8 :numero-str "8"} ; 1.10.2019 alkaen käyttöön kentällä. On jo tierekisterissä 14.6.2018 alkaen ja talvihoitoluokat-geom.aineistossa.
   {:nimi "K1"  :numero 9 :numero-str "9"}
   {:nimi "K2"  :numero 10 :numero-str "10"}
   {:nimi "Ei talvihoitoa"  :numero 11 :numero-str "11"}
   {:nimi ei-talvihoitoluokkaa-nimi :numero 100 :numero-str "100"}])
*/


-- Ottaa vastaan toteuma-id:n ja materiaalikoodin.
-- Kaivaa toteuman reittipisteistä niiden pisteiden koordinaatit, joissa on käytetty kyseistä materiaalia.
-- Palauttaa pisteet geometria-arrayna, jota voi hyödyntää esim. QGISissä.
CREATE OR REPLACE FUNCTION toteuman_materiaalipisteet(toteumatunnus INTEGER, materiaalikoodi INTEGER)
    RETURNS GEOMETRY[] AS
$$
DECLARE
    reitti  RECORD;
    i       INTEGER = 0;
    cnt     INTEGER = 0;
    pisteet GEOMETRY[];
BEGIN
    FOR reitti IN (SELECT t.id, tr.reittipisteet
                   FROM toteuma t
                            join toteuman_reittipisteet tr on t.id = tr.toteuma
                   WHERE t.id = toteumatunnus)
        LOOP
            WHILE (i < array_length(reitti.reittipisteet, 1))
                LOOP
                    i := i + 1;
                    cnt := 1;
                    WHILE (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi IS NOT NULL)
                        LOOP
                            IF (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = materiaalikoodi) THEN
                                pisteet := array_append(pisteet, reitti.reittipisteet[i].sijainti ::GEOMETRY);
                                RAISE NOTICE 'i %, cnt %, materiaalikoodi %, koordinaatti %, talvihoitoluokka %', i, cnt, reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi, reitti.reittipisteet[i].sijainti, reitti.reittipisteet[i].talvihoitoluokka;
                            END IF;
                            cnt := cnt + 1;
                        END LOOP;
                END LOOP;
        END LOOP;
    RETURN pisteet;
END;
$$ LANGUAGE plpgsql;

-- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella pisteet, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
-- Palauttaa geometria-arrayn, jota voi hyödyntää esim QGISissa.
CREATE OR REPLACE FUNCTION hae_materiaalipisteet(urakkaid INTEGER, alkupvm DATE, loppupvm DATE,
                                                 materiaali INTEGER, hoitoluokkanro INTEGER,
                                                 hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
    RETURNS SETOF GEOMETRY AS
$piste$
DECLARE
    reitti                     GEOMETRY;
    toteuma                    RECORD;
    pistelaskuri               INT;
    toteuma_idt                INTEGER[];
    piste                      GEOMETRY;
    toteuman_materiaalipisteet GEOMETRY[];
    materiaalipisteet          GEOMETRY[];
BEGIN
    RAISE NOTICE '**** ALKU. Urakka: %. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', urakkaid, alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
    pistelaskuri := 0;
    FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                    from toteuma t
                             join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                    where t.urakka = urakkaid
                      and t.alkanut between alkupvm and loppupvm
                      and t.reitti is not null)
        LOOP
            FOR reitti IN (select geometria::GEOMETRY
                           from hoitoluokka
                           where hoitoluokka = hoitoluokkanro
                             and tietolajitunniste = hoitoluokkatyyppi)
                LOOP
                    IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                        toteuman_materiaalipisteet := (SELECT toteuman_materiaalipisteet(toteuma.toteuma, materiaali));
                        IF (array_length(toteuman_materiaalipisteet, 1) > 0) THEN
                            materiaalipisteet := concat(materiaalipisteet || toteuman_materiaalipisteet);
                            pistelaskuri := pistelaskuri + 1;
                            toteuma_idt := array_append(toteuma_idt, toteuma.toteuma);
                        END IF;
                    END IF;
                END LOOP;
        END LOOP;
    RAISE NOTICE '**** LOPPU. Hoitoluokkaan % osuneita pisteitä löytyi % kpl. Toteumat ovat: % *****', hoitoluokkanro,pistelaskuri, toteuma_idt;

    FOREACH piste IN ARRAY materiaalipisteet
        LOOP
            RETURN NEXT piste;
        END LOOP;

END;
$piste$ LANGUAGE plpgsql;


select hae_materiaalipisteet(363, '2021-01-01'::DATE, '2021-01-31'::DATE, 5, 1, 'talvihoito'); -- 28
select hae_materiaalipisteet(363, '2021-02-01'::DATE, '2021-02-28'::DATE, 5, 1, 'talvihoito');
select hae_materiaalipisteet(363, '2021-02-01'::DATE, '2021-02-28'::DATE, 5, 2, 'talvihoito');
select hae_materiaalipisteet(270, '2021-01-01'::DATE, '2021-01-31'::DATE, 5, 1, 'talvihoito'); -- 132
select hae_materiaalipisteet(270, '2021-01-01'::DATE, '2021-01-31'::DATE, 5, 2, 'talvihoito'); -- 319


-- Ottaa vastaan toteuma-id:n ja materiaalikoodin.
-- Kaivaa toteuman reittipisteistä niiden pisteiden materiaalimäärät, joissa on käytetty kyseistä materiaalia.
-- Palauttaa määrän yhteenlaskettuna.
CREATE OR REPLACE FUNCTION toteuman_materiaalimaarat(toteumatunnus INTEGER, materiaalikoodi INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    reitti RECORD;
    i      INTEGER = 0;
    cnt    INTEGER = 0;
    maara  NUMERIC = 0;
BEGIN
    FOR reitti IN (SELECT t.id, tr.reittipisteet
                   FROM toteuma t
                            join toteuman_reittipisteet tr on t.id = tr.toteuma
                   WHERE t.id = toteumatunnus)
        LOOP
            WHILE (i < array_length(reitti.reittipisteet, 1))
                LOOP
                    i := i + 1;
                    cnt := 1;
                    WHILE (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = materiaalikoodi)
                        LOOP
                            IF (reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi = materiaalikoodi) THEN
                                maara := maara + reitti.reittipisteet[i].materiaalit[cnt].maara;
                                -- RAISE NOTICE 'i %, cnt %, materiaalikoodi %, maara %, talvihoitoluokka %', i, cnt, reitti.reittipisteet[i].materiaalit[cnt].materiaalikoodi, reitti.reittipisteet[i].materiaalit[cnt].maara, reitti.reittipisteet[i].talvihoitoluokka;
                            END IF;
                            cnt := cnt + 1;
                        END LOOP;
                END LOOP;
        END LOOP;
    -- RAISE NOTICE 'Palautetaan määrä %', maara;
    RETURN maara;
END;
$$ LANGUAGE plpgsql;


-- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella materiaalimäärät, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
-- Palauttaa materiaalin yhteenlasketun määrän, jota voi hyödyntää esim QGISissa.
CREATE OR REPLACE FUNCTION hae_materiaalimaara(urakkaid INTEGER, alkupvm DATE, loppupvm DATE,
                                               materiaali INTEGER, hoitoluokkanro INTEGER,
                                               hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
    RETURNS NUMERIC AS
$$
DECLARE
    reitti          GEOMETRY;
    toteuma         RECORD;
    materiaalimaara NUMERIC = 0;
BEGIN
    RAISE NOTICE '**** ALKU. Urakka: %. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', urakkaid, alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
    FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                    from toteuma t
                             join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                    where t.urakka = urakkaid
                      and t.alkanut between alkupvm and loppupvm
                      and t.reitti is not null)
        LOOP
            FOR reitti IN (select geometria::GEOMETRY
                           from hoitoluokka
                           where hoitoluokka = hoitoluokkanro
                             and tietolajitunniste = hoitoluokkatyyppi)
                LOOP
                    IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                        materiaalimaara := materiaalimaara +
                                           (SELECT toteuman_materiaalimaarat(toteuma.toteuma, materiaali))::NUMERIC;
                    END IF;
                END LOOP;
        END LOOP;
    RAISE NOTICE '**** LOPPU. Käytetty materiaalimäärä on: % *****', materiaalimaara;

    RETURN materiaalimaara;
END;
$$ LANGUAGE plpgsql;

--
-- select hae_materiaalimaara(363, '2021-01-01'::DATE, '2021-01-31'::DATE, 5, 1, 'talvihoito'); -- 28
-- select hae_materiaalimaara(363, '2021-02-01'::DATE, '2021-02-28'::DATE, 5, 1, 'talvihoito');
-- select hae_materiaalimaara(363, '2021-02-01'::DATE, '2021-02-28'::DATE, 5, 2, 'talvihoito');
-- select hae_materiaalimaara(270, '2021-01-01'::DATE, '2021-01-31'::DATE, 5, 1, 'talvihoito'); -- 132
-- select hae_materiaalimaara(270, '2021-01-01'::DATE, '2021-01-31'::DATE, 5, 2, 'talvihoito'); -- 319
--




-- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella materiaalimäärät, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
-- Palauttaa materiaalin yhteenlasketun määrän, jota voi hyödyntää esim QGISissa.
CREATE OR REPLACE FUNCTION hae_materiaalimaara_ely(ely INTEGER, alkupvm DATE, loppupvm DATE,
                                               materiaali INTEGER, hoitoluokkanro INTEGER,
                                               hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
    RETURNS NUMERIC AS
$$
DECLARE
    reitti          GEOMETRY;
    toteuma         RECORD;
    materiaalimaara NUMERIC = 0;
BEGIN
    RAISE NOTICE '**** ALKU. Urakka: %. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', urakkaid, alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
    FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                    from toteuma t
                             join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                    where t.urakka in (select id from urakka where hallintayksikko = ely)
                      and t.alkanut between alkupvm and loppupvm
                      and t.reitti is not null)
        LOOP
            FOR reitti IN (select geometria::GEOMETRY
                           from hoitoluokka
                           where hoitoluokka = hoitoluokkanro
                             and tietolajitunniste = hoitoluokkatyyppi)
                LOOP
                    IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                        materiaalimaara := materiaalimaara +
                                           (SELECT toteuman_materiaalimaarat(toteuma.toteuma, materiaali))::NUMERIC;
                    END IF;
                END LOOP;
        END LOOP;
    RAISE NOTICE '**** LOPPU. Käytetty materiaalimäärä on: % *****', materiaalimaara;

    RETURN materiaalimaara;
END;
$$ LANGUAGE plpgsql;



-- Hakee urakan, aikarajauksen ja materiaalikoodin perusteella materiaalimäärät, joissa materiaalia on käytetty annetun hoitoluokan tasoisella tiellä.
-- Palauttaa materiaalin yhteenlasketun määrän, jota voi hyödyntää esim QGISissa.
CREATE OR REPLACE FUNCTION hae_materiaalimaara_suomi(alkupvm DATE, loppupvm DATE,
                                                   materiaali INTEGER, hoitoluokkanro INTEGER,
                                                   hoitoluokkatyyppi HOITOLUOKAN_TIETOLAJITUNNISTE)
    RETURNS NUMERIC AS
$$
DECLARE
    reitti          GEOMETRY;
    toteuma         RECORD;
    materiaalimaara NUMERIC = 0;
BEGIN
    RAISE NOTICE '**** ALKU. Koko maa. Aikaväli: % - %. Materiaalikoodi: %. Hoitoluokka: %, %. *****', alkupvm, loppupvm, materiaali, hoitoluokkanro, hoitoluokkatyyppi;
    FOR toteuma IN (select t.id as toteuma, tm.materiaalikoodi as materiaali, t.reitti as reitti
                    from toteuma t
                             join toteuma_materiaali tm on t.id = tm.toteuma and tm.materiaalikoodi = materiaali
                    where t.urakka in (select id from urakka where tyyppi in ('hoito', 'teiden-hoito'))
                      and t.alkanut between alkupvm and loppupvm
                      and t.reitti is not null)
        LOOP
            FOR reitti IN (select geometria::GEOMETRY
                           from hoitoluokka
                           where hoitoluokka = hoitoluokkanro
                             and tietolajitunniste = hoitoluokkatyyppi)
                LOOP
                    IF (st_overlaps(toteuma.reitti::geometry, reitti::geometry)) THEN
                        materiaalimaara := materiaalimaara +
                                           (SELECT toteuman_materiaalimaarat(toteuma.toteuma, materiaali))::NUMERIC;
                    END IF;
                END LOOP;
        END LOOP;
    RAISE NOTICE '**** LOPPU. Käytetty materiaalimäärä on: % *****', materiaalimaara;

    RETURN materiaalimaara;
END;
$$ LANGUAGE plpgsql;

select hae_materiaalimaara_suomi('2020-12-01','2021-12-31', 5, 1,'talvihoito'); -- joulukuun hiekat suomessa, hoitoluokka 1, 1074.19021099999999539486
select hae_materiaalimaara_suomi('2020-12-01','2021-12-31', 5, 9,'talvihoito'); -- joulukuun hiekat suomessa, K1 = 9