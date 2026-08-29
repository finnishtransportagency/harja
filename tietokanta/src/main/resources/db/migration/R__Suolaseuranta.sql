/*
HUOM WORK IN PROGRESS eli kesken vielä. Kommentoitu ja dokumentoitu tähän etupeltoon.

Tämä tiedosto sisältää aputiedostoja urakoiden suolatilanteen tarkastamiseksi.

1. tarkista_urakoiden_suolatoteumat_hoitovuoden_ajalta
Käy läpi hoitovuoden ajalta kakikki toiminnassa olevat urakat.
HUOM!!!! ÄLÄ AJA TUOTANNOSSA.

2. tarkista_urakan_suolatoteumat_hoitovuoden_ajalta
Käy läpi yksittäisen urakan hoitovuoden aikana tekemät suolatoteumakirjaukset.
HUOM!!! En tiedä kannattaako ajaa tuotannossa

3. tarkista_urakan_suolatoteumat_kuukauden_ajalta
Käy läpi yksittäisen urakan hoitovuoden aikana tekemät suolatoteumakirjaukset.

Tarkastukset joita funktiot tekevät:
onko_kasin_kirjattuja_suolatoteumia
onko_puuttuvia_reittipisteita
onko_puuttuvia_suolapisteita
onko_kokonaissuolan_maara_eri_kuin_reittipisteiden *
onko_suolapisteiden_maara_eri_kuin_reittipisteiden *
onko_hoitoluokittainen_suolamaara_eri_kuin_kokonaismaara

* Näissä on käytetty raja-arvoa joka on kovakoodattu kutsuvaan funktioon. Muuta se sopivaksi.
 */


CREATE OR REPLACE FUNCTION onko_kasin_kirjattuja_suolatoteumia(urakan_id INTEGER, vuosi INTEGER, kuukausi INTEGER) RETURNS VOID AS
$$
DECLARE
    toteuma RECORD;
BEGIN

    -- Haetaan kuukauden aikana urakassa käsin kirjatut suolat.
    -- Toteuma on käsin kirjattu, kun lähde on Harjan käyttöliittymä.
    -- Käsin kirjaaminen on sallittu poikkeustapauksissa. Runsas määrä käsinkirjauksia ei ole suotavaa, mutta absoluuttista rajaa ei ole määritetty.
    -- Suolaa kirjataan käsin usein täsmäyttämään suolakirjanpitoa, kun kulunut suolamäärä ei vastaa koneellisesti raportoitua suolamäärää.
    RAISE NOTICE ' ';
    RAISE NOTICE '      KÄSIN KIRJATUT SUOLATOTEUMAT.';
    RAISE NOTICE '      Parametrit: %, %, %', urakan_id, vuosi, kuukausi;
    FOR toteuma IN
        SELECT tot.id   as id,
               mk.nimi  as materiaali,
               tm.maara as materiaalimaara
        FROM toteuma tot
                 JOIN toteuma_tehtava tt
                      ON tot.id = tt.toteuma AND tt.toimenpidekoodi = 1369 -- Suolaus, kaikissa ympäristöissä.
                 JOIN toteuma_materiaali tm ON tot.id = tm.toteuma AND tm.poistettu IS NOT TRUE AND tm.maara > 0
                 JOIN materiaalikoodi mk
                      on tm.materiaalikoodi = mk.id AND mk.materiaalityyppi IN ('talvisuola', 'formiaatti')
        WHERE tot.poistettu IS NOT TRUE
          AND tot.urakka = urakan_id
          AND tot.alkanut BETWEEN make_date(vuosi, kuukausi, 1) AND make_date(vuosi, kuukausi, 1) + interval '1 month' -- loppuajankohdan kuuluu olla seuraavan kuukauden 1. päivä klo 0.00.
          AND tot.lahde = 'harja-ui'
        ORDER BY tot.id, tm.materiaalikoodi
        LOOP
            -- Raportoidaan käsinkirjatut suolatoteumat
            RAISE NOTICE '          - toteuma-id: %, materiaali: %, määrä: %.', toteuma.id, toteuma.materiaali, toteuma.materiaalimaara;
        END LOOP;

END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION onko_puuttuvia_reittipisteita(urakan_id INTEGER, vuosi INTEGER, kuukausi INTEGER) RETURNS VOID AS
$$
DECLARE
    toteuma RECORD;
BEGIN

    -- Haetaan kuukauden aikana urakassa koneellisesti raportoidut suolatoteumat, joiden reittipisteet puuttuvat.
    -- Reittipisteiden puuttuminen, kun on koneellisesti raportoitu kokonaissuolamäärä > 0, on virhetilanne.
    -- Toteuma on koneellisesti kirjattu, kun lähde on harja-api.
    -- Reittipisteet tulevat urakoitsijajärjestelmästä. Nk. suolapisteet ovat Harjan reittipisteiden pohjalta generoimia pisteitä.
    -- Niiden oikeellisuutta tarkastellaan toisissa funktioissa.
    RAISE NOTICE ' ';
    RAISE NOTICE '      KONEELLISESTI KIRJAUT SUOLATOTEUMAT ILMAN REITTIPISTEITÄ.';
    RAISE NOTICE '      Parametrit: %, %, %', urakan_id, vuosi, kuukausi;
    FOR toteuma IN
        SELECT tot.id   as id,
               mk.nimi  as materiaali,
               tm.maara as materiaalimaara
        FROM toteuma tot
                 JOIN toteuma_materiaali tm ON tot.id = tm.toteuma AND tm.poistettu IS NOT TRUE
                 JOIN materiaalikoodi mk
                      ON tm.materiaalikoodi = mk.id AND mk.materiaalityyppi IN ('talvisuola', 'formiaatti')
                 LEFT JOIN toteuman_reittipisteet rp ON tot.id = rp.toteuma
        WHERE tot.poistettu IS NOT TRUE
          AND tot.urakka = urakan_id
          AND tot.alkanut BETWEEN make_date(vuosi, kuukausi, 1)::DATE AND make_date(vuosi, kuukausi + 1, 1)::DATE
          AND tm.maara > 0
          AND rp.toteuma IS NULL
          AND tot.lahde = 'harja-api'
        LOOP
            -- Listataan virheelliset toteumat
            RAISE NOTICE '          * toteuma-id: %, materiaali: %, määrä: %', toteuma.id, toteuma.materiaali, toteuma.materiaalimaara;
        END LOOP;

END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION onko_puuttuvia_suolapisteita(urakan_id INTEGER, vuosi INTEGER, kuukausi INTEGER) RETURNS VOID AS
$$
DECLARE
    toteuma RECORD;
BEGIN

    -- Haetaan kuukauden aikana urakassa koneellisesti raportoidut suolatoteumat, joissa on reittipisteitä, mutta joiden suolapisteet puuttuvat kokonaan.
    -- Suolapisteiden puuttuminen, kun on koneellisesti raportoitu reittpisteen suolamäärä > 0, on virhetilanne. Käsin kirjatusta suolasta ei saada suolapisteitä, koska reittipisteitäkään ei ole.
    -- Toteuma on koneellisesti kirjattu, kun lähde on harja-api.
    -- Reittipisteet tulevat urakoitsijajärjestelmästä. Suolapisteet ovat Harjan reittipisteiden pohjalta generoimia pisteitä.

    RAISE NOTICE ' ';
    RAISE NOTICE '      KONEELLISESTI KIRJAUT SUOLATOTEUMAT ILMAN SUOLAPISTEITÄ.';
    RAISE NOTICE '      Parametrit: %, %, %', urakan_id, vuosi, kuukausi;
    FOR toteuma IN
        WITH vertailu as (SELECT tr.toteuma,
                                 mk.nimi                                 as materiaali,
                                 (SELECT SUM(maara)
                                  FROM suolatoteuma_reittipiste sr
                                  WHERE sr.toteuma = tr.toteuma
                                    AND sr.materiaalikoodi = 7
                                    AND sr.pohjavesialue IS NULL)        as suolapistesumma,
                                 (SELECT SUM(materiaalit.maara)
                                  FROM unnest(tr.reittipisteet) AS reittipiste,
                                       unnest(reittipiste.materiaalit) AS materiaalit
                                  WHERE materiaalit.materiaalikoodi = 7) AS reittipistesumma
                          FROM toteuman_reittipisteet tr
                                   JOIN toteuma tot ON tr.toteuma = tot.id
                              AND tot.poistettu IS NOT TRUE
                              AND
                                                       tot.alkanut BETWEEN make_date(2025, 3, 1) AND make_date(2025, 3 + 1, 1)
                              AND tot.urakka = 469
                                   JOIN toteuma_tehtava tt
                                        ON tot.id = tt.toteuma AND tt.toimenpidekoodi = 1369 -- Suolaus
                                   JOIN toteuma_materiaali tm ON tot.id = tm.toteuma AND tm.poistettu IS NOT TRUE
                                   JOIN materiaalikoodi mk
                                        ON tm.materiaalikoodi = mk.id AND mk.id = 7 AND
                                           mk.materiaalityyppi IN ('talvisuola', 'formiaatti'))
        SELECT vertailu.toteuma          as id,
               materiaali                as materiaali,
               vertailu.reittipistesumma AS reittipisteiden_summa
        FROM vertailu
        WHERE vertailu.suolapistesumma IS NULL
        ORDER BY vertailu.toteuma
        LOOP
            -- Listataan virheelliset toteumat
            RAISE NOTICE '          * toteuma-id: %, materiaali: %, määrä: %', toteuma.id, toteuma.materiaali, toteuma.reittipisteiden_summa;
        END LOOP;

END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION onko_kokonaissuolan_maara_eri_kuin_reittipisteiden(urakan_id INTEGER, vuosi INTEGER,
                                                                              kuukausi INTEGER, materiaalin_id INTEGER,
                                                                              raja_arvo NUMERIC) RETURNS VOID AS
$$
DECLARE
    toteuma          RECORD;
    materiaalin_nimi TEXT;
BEGIN

    materiaalin_nimi := (SELECT nimi FROM materiaalikoodi WHERE id = materiaalin_id);

    -- Haetaan kuukauden aikana urakassa koneellisesti raportoidut suolatoteumat, joissa reittipisteet on mukana.
    -- Funktio ei käsittele suolatoteumia, joissa reittipisteitä ei ole lähetetty tai joissa kaikki reittipisteet ovat jääneet tallentamatta.
    -- Tällaiset tapaukset tarkastaa funktio onko_puuttuvia_reittipisteita.
    -- Toteuma on koneellisesti kirjattu, kun lähde on harja-api.
    -- Reittipisteet tulevat urakoitsijajärjestelmästä. Nk. suolapisteet ovat Harjan reittipisteiden pohjalta generoimia pisteitä.
    -- Niiden oikeellisuutta tarkastellaan toisissa funktioissa.
    RAISE NOTICE ' ';
    RAISE NOTICE '      KONEELLISESTI KIRJATUT % (%)-TOTEUMAT, JOISSA KOKONAISSUOLAMÄÄRÄ EI VASTAA REITTIPISTEIDEN SUOLAMÄÄRÄÄ.', materiaalin_nimi, materiaalin_id;
    RAISE NOTICE '      Mukana vain toteumat, joissa on tallentunut reittipisteitä. Parametrit: %, %, %, %, %', urakan_id, vuosi, kuukausi, materiaalin_id, raja_arvo;
    FOR toteuma IN
        WITH vertailu as (SELECT tr.toteuma,
                                 mk.nimi                                              as materiaali,
                                 (SELECT SUM(maara)
                                  FROM toteuma_materiaali tm
                                  WHERE tm.toteuma = tr.toteuma
                                    AND tm.materiaalikoodi = materiaalin_id)          as kokonaismaara,
                                 (SELECT SUM(materiaalit.maara)
                                  FROM unnest(tr.reittipisteet) AS reittipiste,
                                       unnest(reittipiste.materiaalit) AS materiaalit
                                  WHERE materiaalit.materiaalikoodi = materiaalin_id) AS reittipistesumma
                          FROM toteuman_reittipisteet tr
                                   JOIN toteuma tot ON tr.toteuma = tot.id
                              AND tot.poistettu IS NOT TRUE
                              AND
                                                       tot.alkanut BETWEEN make_date(vuosi, kuukausi, 1) AND make_date(vuosi, kuukausi + 1, 1)
                              AND tot.urakka = urakan_id
                                   JOIN toteuma_tehtava tt
                                        ON tot.id = tt.toteuma AND tt.toimenpidekoodi = 1369 -- Suolaus
                                   JOIN toteuma_materiaali tm ON tot.id = tm.toteuma AND tm.poistettu IS NOT TRUE
                                   JOIN materiaalikoodi mk
                                        ON tm.materiaalikoodi = mk.id AND mk.id = materiaalin_id AND
                                           mk.materiaalityyppi IN ('talvisuola', 'formiaatti'))
        SELECT vertailu.toteuma                            as id,
               materiaali                                  as materiaali,
               vertailu.kokonaismaara                      AS ilmoitettu_kokonaismaara,
               vertailu.reittipistesumma                   AS toteuman_reittipisteiden_summa,
               (coalesce(vertailu.kokonaismaara, 0) -
                coalesce(vertailu.reittipistesumma, 0))    AS poikkeama_eli_kokonaismaaran_ja_reittipisteiden_ero,
               abs(coalesce(vertailu.kokonaismaara, 0) -
                   coalesce(vertailu.reittipistesumma, 0)) AS abs_kokonaismaaran_ja_reittipisteiden_ero
        FROM vertailu
        WHERE abs(coalesce(vertailu.kokonaismaara, 0) -
                  coalesce(vertailu.reittipistesumma, 0)) > raja_arvo
        ORDER BY abs_kokonaismaaran_ja_reittipisteiden_ero DESC
        LOOP
            -- Raportoidaan suolatoteumat, joissa kokonaissuolamäärä ei vastaa reittipisteiden suolamäärää
            RAISE NOTICE '          * toteuma-id: %, materiaali: %. Kokonaismaara/reittipisteiden summa %/%: . Erotus: %' , toteuma.id, toteuma.materiaali, toteuma.ilmoitettu_kokonaismaara, toteuma.toteuman_reittipisteiden_summa, toteuma.abs_kokonaismaaran_ja_reittipisteiden_ero;
        END LOOP;

END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION onko_suolapisteiden_maara_eri_kuin_reittipisteiden(urakan_id INTEGER, vuosi INTEGER,
                                                                              kuukausi INTEGER, materiaalin_id INTEGER,
                                                                              raja_arvo NUMERIC) RETURNS VOID AS
$$
DECLARE
    toteuma          RECORD;
    materiaalin_nimi TEXT;
BEGIN

    materiaalin_nimi := (SELECT nimi FROM materiaalikoodi WHERE id = materiaalin_id);

    -- Haetaan kuukauden aikana urakassa koneellisesti raportoidut suolatoteumat ja verrataan reittipisteissä raportoitua suolamäärää Harjan generoimien suolapisteiden suolamäärään.
    -- Funktio ei käsittele suolatoteumia, joissa reittipisteitä ei ole lähetetty tai joissa kaikki reittipisteet ovat jääneet tallentamatta.
    -- Tällaiset tapaukset tarkastaa funktio onko_puuttuvia_reittipisteita.
    -- Funktio ei myöskään käsittele toteumia, joissa materiaalin suolapisteitä ei ole lainkaan. Nämä tarkastaa funktio onko_puuttuvia_suolapisteita.
    -- Toteuma on koneellisesti kirjattu, kun lähde on harja-api.

    RAISE NOTICE ' ';
    RAISE NOTICE '      KONEELLISESTI KIRJATUT % (%)-TOTEUMAT, JOISSA SUOLAPISTEIDEN SUOLAMÄÄRÄ EI VASTAA REITTIPISTEIDEN SUOLAMÄÄRÄÄ.', materiaalin_nimi, materiaalin_id;
    RAISE NOTICE '      Mukana vain toteumat, joissa on tallentunut reittipisteitä ja suolapisteitä. Parametrit: %, %, %, %, %', urakan_id, vuosi, kuukausi, materiaalin_id, raja_arvo;
    FOR toteuma IN
        WITH vertailu as (SELECT tr.toteuma,
                                 mk.nimi                                              as materiaali,
                                 (SELECT SUM(maara)
                                  FROM suolatoteuma_reittipiste sr
                                  WHERE sr.toteuma = tr.toteuma
                                    AND sr.materiaalikoodi = materiaalin_id
                                    AND sr.pohjavesialue IS NULL)                     as suolapistesumma,
                                 (SELECT SUM(materiaalit.maara)
                                  FROM unnest(tr.reittipisteet) AS reittipiste,
                                       unnest(reittipiste.materiaalit) AS materiaalit
                                  WHERE materiaalit.materiaalikoodi = materiaalin_id) AS reittipistesumma
                          FROM toteuman_reittipisteet tr
                                   JOIN toteuma tot ON tr.toteuma = tot.id
                              AND tot.poistettu IS NOT TRUE
                              AND
                                                       tot.alkanut BETWEEN make_date(vuosi, kuukausi, 1) AND make_date(vuosi, kuukausi + 1, 1)
                              AND tot.urakka = urakan_id
                                   JOIN toteuma_tehtava tt
                                        ON tot.id = tt.toteuma AND tt.toimenpidekoodi = 1369 -- Suolaus
                                   JOIN toteuma_materiaali tm ON tot.id = tm.toteuma AND tm.poistettu IS NOT TRUE
                                   JOIN materiaalikoodi mk
                                        ON tm.materiaalikoodi = mk.id AND mk.id = materiaalin_id AND
                                           mk.materiaalityyppi IN ('talvisuola', 'formiaatti'))
        SELECT vertailu.toteuma                            as id,
               materiaali                                  as materiaali,
               vertailu.suolapistesumma                    AS ilmoitettu_suolapistesumma,
               vertailu.reittipistesumma                   AS toteuman_reittipisteiden_summa,
               (coalesce(vertailu.suolapistesumma, 0) -
                coalesce(vertailu.reittipistesumma, 0))    AS poikkeama_eli_suolapistesumman_ja_reittipisteiden_ero,
               abs(coalesce(vertailu.suolapistesumma, 0) -
                   coalesce(vertailu.reittipistesumma, 0)) AS abs_suolapistesumman_ja_reittipisteiden_ero
        FROM vertailu
        WHERE vertailu.suolapistesumma IS NOT NULL
          AND abs(coalesce(vertailu.suolapistesumma, 0) -
                  coalesce(vertailu.reittipistesumma, 0)) > raja_arvo
        ORDER BY abs_suolapistesumman_ja_reittipisteiden_ero DESC
        LOOP
            -- Raportoidaan käsinkirjatut suolatoteumat
            RAISE NOTICE '          * toteuma-id: %, materiaali: %. Suolapistesumma/reittipisteiden summa %/%: . Erotus: %' , toteuma.id, toteuma.materiaali, toteuma.ilmoitettu_suolapistesumma, toteuma.toteuman_reittipisteiden_summa, toteuma.abs_suolapistesumman_ja_reittipisteiden_ero;
        END LOOP;

END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION onko_hoitoluokittainen_suolamaara_eri_kuin_kokonaismaara(urakan_id INTEGER, vuosi INTEGER,
                                                                                    kuukausi INTEGER,
                                                                                    materiaalin_id INTEGER) RETURNS VOID AS
$$
DECLARE
    summa_toteumamateriaali     NUMERIC;
    summa_hoitoluokkamateriaali NUMERIC;
    materiaalin_nimi TEXT;
BEGIN

    materiaalin_nimi := (SELECT nimi FROM materiaalikoodi WHERE id = materiaalin_id);

    -- Verrataan ensin koneellisesti kirjattujen suolatoteumien materiaalimäärää urakan hoitoluokittaiseen materiaalinkäyttöön. Täsmäävätkö luvut?
    -- Verrataan sitten käsin kirjattujen suolatoteumien materiaalimäärää urakan hoitoluokittaiseen materiaalinkäyttöön. Täsmäävätkö luvut?
    -- Toteuma on koneellisesti kirjattu, kun lähde on harja-api. Käsin kirjattu suola luokitellaan hoitoluokalle "99".
    RAISE NOTICE ' ';
    RAISE NOTICE '      %-MATERIAALIA (%) KOSKEVAT VIRHEET HOITOLUOKKAKOHTAISESSA ERITTELYSSÄ', materiaalin_nimi, materiaalin_id;
    RAISE NOTICE '      Parametrit: %, %, %, % ', urakan_id, vuosi, kuukausi, materiaalin_id;

    summa_toteumamateriaali := (SELECT sum(tm.maara)
                                FROM toteuma tot
                                         JOIN toteuma_materiaali tm ON tot.id = tm.toteuma AND tm.poistettu IS NOT TRUE
                                WHERE tot.poistettu IS NOT TRUE
                                  AND tot.urakka = urakan_id
                                  AND tot.alkanut BETWEEN make_date(vuosi, kuukausi, 1) AND make_date(vuosi, kuukausi + 1, 1)
                                  AND tm.materiaalikoodi = materiaalin_id
                                  AND tot.lahde = 'harja-api'); -- Ei käsin kirjattuja

    summa_hoitoluokkamateriaali := (SELECT SUM(maara)
                                    FROM urakan_materiaalin_kaytto_hoitoluokittain umkh
                                    WHERE umkh.materiaalikoodi = materiaalin_id
                                      AND umkh.pvm BETWEEN make_date(vuosi, kuukausi, 1) AND make_date(vuosi, kuukausi + 1, 1)
                                      AND umkh.urakka = urakan_id
                                      AND talvihoitoluokka != 99); -- Ei käsin kirjattuja

    IF (summa_toteumamateriaali > summa_hoitoluokkamateriaali) THEN
        RAISE NOTICE '          * Osa koneellisesti raportoidusta materiaalista % puuttuu urakan hoitoluokittaisesta materiaalinkäyttömäärästä', materiaalin_id;
        RAISE NOTICE '            Kokonaissumma/hoitoluokittainen summa: %/%', summa_toteumamateriaali, summa_hoitoluokkamateriaali;
        -- TODO: Tässä vois tutkia puuttuuko jonkun tietyn päivän urakan hoitoluokittainen materiaalinkäyttö
        -- Korjaustakin voisi yrittää?
    ELSEIF (summa_toteumamateriaali < summa_hoitoluokkamateriaali) THEN
        RAISE NOTICE '          * Materiaalia % on liikaa urakan hoitoluokittaisessa koneellisesti kirjatussa materiaalinkäyttömäärässä', materiaalin_id;
        RAISE NOTICE '            Kokonaissumma/hoitoluokittainen summa: %/%', summa_toteumamateriaali, summa_hoitoluokkamateriaali;
        -- TODO: Tässä vois tutkia onko poistettu toteumia ja jäänyt päivittämättä
        -- Korjaustakin voisi yrittää?
    END IF;

    summa_toteumamateriaali := (SELECT sum(tm.maara)
                                FROM toteuma tot
                                         JOIN toteuma_materiaali tm ON tot.id = tm.toteuma AND tm.poistettu IS NOT TRUE
                                WHERE tot.poistettu IS NOT TRUE
                                  AND tot.urakka = urakan_id
                                  AND tot.alkanut BETWEEN make_date(vuosi, kuukausi, 1) AND make_date(vuosi, kuukausi + 1, 1)
                                  AND tm.materiaalikoodi = materiaalin_id
                                  AND tot.lahde = 'harja-ui'); -- Vain käsinkirjatut

    summa_hoitoluokkamateriaali := (SELECT SUM(maara)
                                    FROM urakan_materiaalin_kaytto_hoitoluokittain umkh
                                    WHERE umkh.materiaalikoodi = materiaalin_id
                                      AND umkh.pvm BETWEEN make_date(vuosi, kuukausi, 1) AND make_date(vuosi, kuukausi + 1, 1)
                                      AND umkh.urakka = urakan_id
                                      AND talvihoitoluokka = 99); -- Vain käsinkirjatut

    IF (summa_toteumamateriaali > summa_hoitoluokkamateriaali) THEN
        RAISE NOTICE '          * Osa koneellisesti raportoidusta materiaalista % puuttuu urakan hoitoluokittaisesta materiaalinkäyttömäärästä', materiaalin_id;
        RAISE NOTICE '            Kokonaissumma/hoitoluokittainen summa: %/%', summa_toteumamateriaali, summa_hoitoluokkamateriaali;
        -- TODO: Tässä vois tutkia puuttuuko jonkun tietyn päivän urakan hoitoluokittainen materiaalinkäyttö
        -- Korjaustakin voisi yrittää?
    ELSEIF (summa_toteumamateriaali < summa_hoitoluokkamateriaali) THEN
        RAISE NOTICE '          * Materiaalia % on liikaa urakan hoitoluokittaisessa koneellisesti kirjatussa materiaalinkäyttömäärässä', materiaalin_id;
        RAISE NOTICE '            Kokonaissumma/hoitoluokittainen summa: %/%', summa_toteumamateriaali, summa_hoitoluokkamateriaali;
        -- TODO: Tässä vois tutkia onko poistettu toteumia ja jäänyt päivittämättä
        -- Korjaustakin voisi yrittää?
    END IF;

END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION tarkista_urakan_suolatoteumat_kuukauden_ajalta(urakan_id INTEGER, vuosi INTEGER, kuukausi INTEGER) RETURNS VOID AS
$$
DECLARE
    urakan_nimi     TEXT;
    suolamateriaali RECORD;
BEGIN

    -- Käydään läpi erilaiset suolatietoja tarkastelevat apufunktiot ja selvitetään urakan tilanne annetun kuukauden suolatoteumien osalta.
    -- Tarkastuksen tulokset raportoidaan lokille.

    urakan_nimi := (SELECT nimi FROM urakka WHERE id = urakan_id);
    RAISE NOTICE ' ';
    RAISE NOTICE ' ';
    RAISE NOTICE ' ';
    RAISE NOTICE ' ****************** SUOLATARKASTUS: %  ****************** ', urakan_nimi;
    RAISE NOTICE 'Urakka: %, Ajankohta: %/%', urakan_id, kuukausi, vuosi;

    PERFORM onko_kasin_kirjattuja_suolatoteumia(urakan_id, vuosi, kuukausi);
    PERFORM onko_puuttuvia_reittipisteita(urakan_id, vuosi, kuukausi);
    PERFORM onko_puuttuvia_suolapisteita(urakan_id, vuosi, kuukausi);

    FOR suolamateriaali IN
        SELECT id FROM materiaalikoodi WHERE materiaalityyppi IN ('talvisuola', 'formiaatti')
        LOOP
        -- RAJA-ARVOSTA
        -- Se millainen ero pisteiden ja raportoidun suolamäärän välillä voidaan sallia, riippuu oikeastaan toteuman koosta.
        -- Lyhessä toteumassa pienempi suolamäärä indikoi suurempaa poikkeamaa. Toisaalta lyhyissä suolatoteumissa on harvemmin virheitä kuin toteumissa, jossa pisteitä on enemmän.
        -- TODO: salliun eron voisi laskea myös prosenttina, millainen heitto silloin olisi sallittu?
            PERFORM onko_kokonaissuolan_maara_eri_kuin_reittipisteiden(urakan_id, vuosi, kuukausi, suolamateriaali.id,
                                                                       0.01); -- 10 kg
            PERFORM onko_suolapisteiden_maara_eri_kuin_reittipisteiden(urakan_id, vuosi, kuukausi, suolamateriaali.id,
                                                                       0.01); -- 10 kg
            PERFORM onko_hoitoluokittainen_suolamaara_eri_kuin_kokonaismaara(urakan_id, vuosi, kuukausi,
                                                                             suolamateriaali.id);
        END LOOP;

END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION tarkista_urakan_suolatoteumat_hoitovuoden_ajalta(urakan_id INTEGER, hoitovuosi INTEGER) RETURNS VOID AS
$$
DECLARE
    urakat    RECORD;
    urk       RECORD;
    kuukaudet RECORD;
    rivi      RECORD;
BEGIN

    -- Haetaan hoitovuoden aikana suolausta tehneet urakat
    SELECT *
    FROM urakka
    WHERE tyyppi = 'teiden-hoito'
      AND alkupvm < make_date(hoitovuosi, 1, 1)
      AND loppupvm > make_date(hoitovuosi, 1, 1)
      AND id = 469 -- TODO:. Poista tämä sitten. Rajoitettu kehityksen ajaksi Kemin urakkaan
    order by hallintayksikko, nimi
    INTO urakat;

    -- Pohjustetaan talteen hoitokauden vuosi+kuukausi-yhdistelmät käsittelyä varten
    -- SELECT generate_series(make_date(hoitovuosi, 10, 1), make_date(hoitovuosi + 1, 9, 30), '1 month')::DATE INTO kuukaudet;
    -- TODO: Väliaikainen, rajoitetaan maaliskuuhun
    SELECT generate_series(make_date(hoitovuosi, 3, 1), make_date(hoitovuosi, 3, 1), '1 month')::DATE INTO kuukaudet;
    RAISE NOTICE 'DEBUG, kuukaudet: % ', kuukaudet;

    -- Käydään läpi jokainen urakka ja kuukausi yksitellen ja tehdään niihin suolaan liittyvät tarkastukset.
    -- Tarkastuksen tulokset raportoidaan lokille.
    FOR urk IN
        SELECT u.id   as id,
               u.nimi as nimi
        FROM urakka u
        WHERE u.tyyppi = 'teiden-hoito'
          AND u.alkupvm < make_date(hoitovuosi, 1, 1)
          AND u.loppupvm > make_date(hoitovuosi, 1, 1)
          AND u.id = 469 -- TODO:. Poista tämä sitten. Rajoitettu kehityksen ajaksi Kemin urakkaan
        order by u.hallintayksikko, u.nimi
        LOOP
            RAISE NOTICE '******** % ********', urk.nimi;
            FOR rivi IN
                SELECT date_part('year', ajankohta::DATE)::INTEGER  as vuosi,
                       date_part('month', ajankohta::DATE)::INTEGER as kuukausi
                FROM generate_series(make_date(2025, 3, 1), make_date(2025, 9, 1), '1 month') ajankohta
                LOOP
                    RAISE NOTICE 'Kuukausi %', rivi.kuukausi;
                    -- Onko käsin kirjattuja suolatoteumia?
                    SELECT onko_kasin_kirjattuja_suolatoteumia(urk.id, rivi.vuosi,
                                                               rivi.kuukausi);
                    -- Onko väärälle tehtävälle kirjattuja suolatoteumia?
                    -- Onko suolatoteumia, joilla ei ole reittipisteitä lainkaan?

                END LOOP;

        END LOOP;

END;
$$ LANGUAGE plpgsql;

-- Tämä kannattaa mielummin pyöräyttää replika-kannassa kuin suoraan tuotannossa, mutta replikat on defaulttina tehottomia
-- ja kannattaa varmaan antaa sille vähän enemmän voimia ennen kuin ihan kaikkien urakoiden analyysin tekee.
-- Hoitovuosi on ensimmäinen vuosi. Kun urakka on voimassa 2024-25, hoitovuosi tässä on 2024.
CREATE OR REPLACE FUNCTION tarkista_urakoiden_suolatoteumat_hoitovuoden_ajalta(hoitovuosi INTEGER) RETURNS VOID AS
$$
DECLARE
    urakat    RECORD;
    urk       RECORD;
    kuukaudet RECORD;
    rivi      RECORD;
BEGIN

    -- Haetaan hoitovuoden aikana suolausta tehneet urakat
    SELECT *
    FROM urakka
    WHERE tyyppi = 'teiden-hoito'
      AND alkupvm < make_date(hoitovuosi, 1, 1)
      AND loppupvm > make_date(hoitovuosi, 1, 1)
      AND id = 469 -- TODO:. Poista tämä sitten. Rajoitettu kehityksen ajaksi Kemin urakkaan
    order by hallintayksikko, nimi
    INTO urakat;

    -- Pohjustetaan talteen hoitokauden vuosi+kuukausi-yhdistelmät käsittelyä varten
    -- SELECT generate_series(make_date(hoitovuosi, 10, 1), make_date(hoitovuosi + 1, 9, 30), '1 month')::DATE INTO kuukaudet;
    -- TODO: Väliaikainen, rajoitetaan maaliskuuhun
    SELECT generate_series(make_date(hoitovuosi, 3, 1), make_date(hoitovuosi, 3, 1), '1 month')::DATE INTO kuukaudet;
    RAISE NOTICE 'DEBUG, kuukaudet: % ', kuukaudet;

    -- Käydään läpi jokainen urakka ja kuukausi yksitellen ja tehdään niihin suolaan liittyvät tarkastukset.
    -- Tarkastuksen tulokset raportoidaan lokille.
    FOR urk IN
        SELECT u.id   as id,
               u.nimi as nimi
        FROM urakka u
        WHERE u.tyyppi = 'teiden-hoito'
          AND u.alkupvm < make_date(hoitovuosi, 1, 1)
          AND u.loppupvm > make_date(hoitovuosi, 1, 1)
          AND u.id = 469 -- TODO:. Poista tämä sitten. Rajoitettu kehityksen ajaksi Kemin urakkaan
        order by u.hallintayksikko, u.nimi
        LOOP
            RAISE NOTICE '******** % ********', urk.nimi;
            FOR rivi IN
                SELECT date_part('year', ajankohta::DATE)::INTEGER  as vuosi,
                       date_part('month', ajankohta::DATE)::INTEGER as kuukausi
                FROM generate_series(make_date(2025, 3, 1), make_date(2025, 9, 1), '1 month') ajankohta
                LOOP
                    RAISE NOTICE 'Kuukausi %', rivi.kuukausi;
                    -- Onko käsin kirjattuja suolatoteumia?
                    SELECT onko_kasin_kirjattuja_suolatoteumia(urk.id, rivi.vuosi,
                                                               rivi.kuukausi);
                    -- Onko väärälle tehtävälle kirjattuja suolatoteumia?
                    -- Onko suolatoteumia, joilla ei ole reittipisteitä lainkaan?

                END LOOP;

        END LOOP;

END;
$$ LANGUAGE plpgsql;

GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO harja;
