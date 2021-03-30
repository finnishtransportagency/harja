-- Avuksi kun halutaan kaivaa toteuma_reittipisteet-taulun datasta yksittäiset pisteet, joissa on käytetty jotakin materiaalia.
-- Jos otat nämä sovelluksessa käyttöön, poista dev_-etuliite.
-- Varmista myös että proseduurit toimivat järkevästi ja sutjakasti. Näitä on käytetty ongelmanselvityksessä apuna, eikä vaatimustaso silloin ollut sovelluskäyttötasoa.

-- Jatkokehitysideoita:
-- - Materiaalin määrätiedon voisi palauttaa myös, koska pisteiden materiaalimäärät ovat aivan yhtä kiinnostava tieto kuin niiden sijainti.
-- - Koko ELYn materiaalit, koko maan materiaalit... usein halutaan tietää lukuja korkeammalla tasolla.

-- Ottaa vastaan toteuma-id:n ja materiaalikoodin.
-- Kaivaa toteuman reittipisteistä niiden pisteiden koordinaatit, joissa on käytetty kyseistä materiaalia.
-- Palauttaa pisteet geometria-arrayna, jota voi hyödyntää esim. QGISissä.
CREATE OR REPLACE FUNCTION dev_toteuman_materiaalipisteet(toteumatunnus INTEGER, materiaalikoodi INTEGER)
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
CREATE OR REPLACE FUNCTION dev_hae_materiaalipisteet(urakkaid INTEGER, alkupvm DATE, loppupvm DATE,
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
