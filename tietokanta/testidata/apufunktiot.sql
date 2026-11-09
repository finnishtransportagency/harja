-- Testidatan generointia varten halutaan helppo tapa indeksikorjata MHU:iden kuluja.
-- Kopioitu R__indeksilaskenta.sql
CREATE OR REPLACE FUNCTION testidata_indeksikorjaa(korjattava_arvo NUMERIC, vuosi_ INTEGER, kuukausi_ INTEGER,
                                                   urakka_id INTEGER)
    RETURNS NUMERIC AS
$$
DECLARE
    -- Perusluku on urakalle sama riippumatta kuluvasta hoitokaudesta
    perusluku      NUMERIC := indeksilaskennan_perusluku(urakka_id);
    indeksin_nimi  TEXT;
    urakka_tyyppi TEXT;
    urakan_aloitusvuosi INTEGER;
    urakan_aloituskuu INTEGER;
    -- Tämä "urakkavuoden_alkuvuosi" on vesiväylä-hoito-urakoissa vastaava asia kuin mhu-urakoissa "hoitokauden-alkuvuosi", mutta
    -- vesiväyläurakoissa pitää käyttää termiä "urakkavuosi" niin siksi tämä nimetty "urakkavuoden_alkuvuosi"
    urakkavuoden_alkuvuosi INTEGER;
    arvo NUMERIC;
    vertailuvuosi INTEGER;
    vertailukk INTEGER;
    indeksikerroin NUMERIC;
BEGIN
    SELECT indeksi, tyyppi, EXTRACT(YEAR FROM alkupvm), EXTRACT(MONTH FROM alkupvm)
      FROM urakka u
     WHERE u.id = urakka_id
      INTO indeksin_nimi, urakka_tyyppi, urakan_aloitusvuosi, urakan_aloituskuu;

    -- Vesiväyläurakoissa urakkavuosi määräytyy hoitokauden perusteella.
    IF urakka_tyyppi = 'vesivayla-hoito' AND urakan_aloitusvuosi >= 2023 THEN
            IF vuosi_ = urakan_aloitusvuosi AND kuukausi_ >= urakan_aloituskuu
            THEN
                -- Ensimmäinen urakkavuosi.
                -- Tämä kattaa esimerkiksi 1.8.2023–30.9.2024.
                urakkavuoden_alkuvuosi := urakan_aloitusvuosi;
            ELSIF kuukausi_ >= 10 THEN
                -- Lokakuusta alkaen alkaa uusi normaali urakkavuosi.
                urakkavuoden_alkuvuosi := vuosi_;
            ELSE
                -- Tammi–syyskuu kuuluu edellisen lokakuussa alkaneen
                -- urakkavuoden loppuosaan.
                urakkavuoden_alkuvuosi := vuosi_ - 1;
            END IF;

            IF urakkavuoden_alkuvuosi = urakan_aloitusvuosi THEN
                -- Ensimmäinen urakkavuosi: käytetään aloitusvuoden kesäkuuta.
                vertailuvuosi := urakan_aloitusvuosi;
                vertailukk := 6;
            ELSE
                -- Toinen ja sitä seuraavat urakkavuodet: käytetään elokuuta.
                vertailuvuosi := urakkavuoden_alkuvuosi;
                vertailukk := 8;
            END IF;

    ELSE
            -- Muut urakkatyypit:
            -- Indeksikerroin on hoitokausikohtainen, katsotaan aina edellisen hoitokauden syyskuun indeksiä.
            IF kuukausi_ BETWEEN 1 AND 9 THEN
                vertailuvuosi := vuosi_ - 1;
            ELSE
                vertailuvuosi := vuosi_;
            END IF;

            -- Käytetään vertailukuukauden default-arvona syyskuuta.
            vertailukk := 9;

            -- 2023 tai sen jälkeen alkaville urakoille vertailuukausi on elokuu
            IF urakan_aloitusvuosi >= 2023 THEN
                vertailukk := 8;
            END IF;
    END IF;

    arvo := (SELECT i.arvo
               FROM indeksi i
              WHERE i.vuosi = vertailuvuosi
                AND i.kuukausi = vertailukk
                AND i.nimi = indeksin_nimi);

    -- Indeksikerroin pyöristetään 3 desimaaliin CLJ-puolella (budjettisuunnittelu/hae-urakan-indeksikertoimet)
    indeksikerroin := round((arvo / perusluku), 3);

    --RAISE NOTICE 'vuosi: %, kuukausi: %, arvo: %, indeksikerroin: %, korjattava arvo: %', vuosi_, kuukausi_, arvo, indeksikerroin, korjattava_arvo;

    return round(korjattava_arvo * indeksikerroin, 6);
END ;
$$ language plpgsql;

create or replace function luo_testitarjousmaarat_tehtavalle(urakka_id integer, tpk integer, maara integer, urakan_alkuvuosi integer, urakan_loppuvuosi integer) 
returns boolean as 
$$
declare 
	tpk_rivi record;
	urakka_rivi record;
begin
	for v in urakan_alkuvuosi..urakan_loppuvuosi loop
		insert into sopimus_tehtavamaara(urakka, tehtava, maara, muokattu, hoitovuosi)
		values (urakka_id, tpk, maara, now(), v) on conflict do nothing;
	end loop ;
	return true;

end
$$ language plpgsql;

create or replace function luo_kaikille_tehtaville_testitarjousmaarat(urakka_nimi varchar, maara integer) returns boolean as 
$$
declare 
	tpk_rivi record;
	urakan_loppuvuosi integer;
	urakan_alkuvuosi integer;
    urakka_rivi record;
begin
	select * into urakka_rivi from urakka ur where ur.nimi = urakka_nimi;
	select extract(year from urakka_rivi.alkupvm) into urakan_alkuvuosi;
	select extract(year from urakka_rivi.loppupvm) into urakan_loppuvuosi;

	for tpk_rivi in select tpk.id from tehtava tpk join tehtavaryhma tr on tr.id = tpk.tehtavaryhma and tpk.yksikko is not null and tpk.poistettu is not null and tpk.aluetieto = true loop
		insert into sopimus_tehtavamaara(urakka, tehtava, maara, muokattu, hoitovuosi) values (urakka_rivi.id, tpk_rivi.id, maara, now(), urakan_alkuvuosi) on conflict do nothing;
	end loop ;

for tpk_rivi in select tpk.id from tehtava tpk join tehtavaryhma tr on tr.id = tpk.tehtavaryhma and tpk.yksikko is not null and tpk.poistettu is not null and tpk.aluetieto = false loop
		perform luo_testitarjousmaarat_tehtavalle(urakka_rivi.id, tpk_rivi.id, maara, urakan_alkuvuosi, urakan_loppuvuosi - 1);
	end loop ;
	return true;
end
$$ language plpgsql;

CREATE OR REPLACE FUNCTION kuukauden_nimi(kuukausi INT) RETURNS TEXT AS
$$
BEGIN
    RETURN
        CASE kuukausi
               WHEN 1 THEN 'tammikuu'
               WHEN 2 THEN 'helmikuu'
               WHEN 3 THEN 'maaliskuu'
               WHEN 4 THEN 'huhtikuu'
               WHEN 5 THEN 'toukokuu'
               WHEN 6 THEN 'kesakuu'
               WHEN 7 THEN 'heinakuu'
               WHEN 8 THEN 'elokuu'
               WHEN 9 THEN 'syyskuu'
               WHEN 10 THEN 'lokakuu'
               WHEN 11 THEN 'marraskuu'
               WHEN 12 THEN 'joulukuu'
        END;
    END;
$$ LANGUAGE plpgsql;

-- Rajoitusalueiden tai muiden tieosoitteiden ajoratakilometrien laskentaan soveltuva funktio
CREATE OR REPLACE FUNCTION laske_tieosoitteen_ajoratapituudet(tr_numero_ INTEGER,
                                                              tr_alkuosa_ INTEGER,
                                                              tr_alkuetaisyys_ INTEGER,
                                                              tr_loppuosa_ INTEGER,
                                                              tr_loppuetaisyys_ INTEGER)
    RETURNS INTEGER AS
$$
DECLARE
    kilometrit INTEGER;

BEGIN
      WITH relevantit AS
               (SELECT *
                  FROM tr_osoitteet
                 WHERE "tr-numero" = tr_numero_ AND
                     "tr-osa" BETWEEN tr_alkuosa_ AND tr_loppuosa_ AND
                   -- alkupään tarkastelu
                     (tr_alkuosa_ < "tr-osa" OR (tr_alkuosa_ = "tr-osa" AND tr_alkuetaisyys_ < "tr-loppuetaisyys")) AND
                   -- loppupään tarkastelu
                     (tr_loppuosa_ > "tr-osa" OR (tr_loppuosa_ = "tr-osa" AND  tr_loppuetaisyys_ > "tr-alkuetaisyys")) AND
                   -- huomioitava vain pääkaistat 11 ja 21, muuten esim. kääntymiskaistoista tulee häiriötä laskentaan
                     (("tr-ajorata" = 0 AND "tr-kaista" = 11) OR
                      ("tr-ajorata" = 1 AND "tr-kaista" = 11) OR
                      ("tr-ajorata" = 2 AND "tr-kaista" = 21) OR
                      -- joitakin kävelyn ja pyöräilyn väyliä on rajoitusalueina, niillä on aina tienumero 70000-80000, ajorata 0 ja kaista 31
                      ("tr-numero" BETWEEN 70000 AND 80000 AND "tr-ajorata" = 0 AND "tr-kaista" = 31))
                 ORDER BY "tr-osa", "tr-alkuetaisyys")
    SELECT SUM(
               CASE
                   -- jos ko. osat ovat varmuudella kaikki kokonaisuudessan mukana
                   WHEN (tr_alkuosa_ < "tr-osa" AND tr_loppuosa_ > "tr-osa") THEN "tr-loppuetaisyys" - "tr-alkuetaisyys"
                   -- jos alkuosa osoitteessa on pienempi, mutta loppuosa sama
                   WHEN (tr_alkuosa_ < "tr-osa" AND tr_loppuosa_ = "tr-osa") THEN
                       LEAST("tr-loppuetaisyys", tr_loppuetaisyys_) - "tr-alkuetaisyys"
                   -- jos loppuosa osoitteessa on suurempi, mutta alkuosa sama
                   WHEN (tr_loppuosa_ > "tr-osa" AND tr_alkuosa_ = "tr-osa") THEN
                       "tr-loppuetaisyys" - GREATEST("tr-alkuetaisyys", tr_alkuetaisyys_)
                   -- jos alku- ja loppuosa on sama, saadaan haluttu väli least ja greatest avulla
                   WHEN tr_alkuosa_ = "tr-osa" AND tr_loppuosa_ = "tr-osa" THEN
                       LEAST("tr-loppuetaisyys", tr_loppuetaisyys_) - GREATEST("tr-alkuetaisyys", tr_alkuetaisyys_)
                   END) AS ajoratakilometrit
      FROM relevantit INTO kilometrit;
    RETURN kilometrit;
END
$$ LANGUAGE plpgsql;

-- tässä voimakas työkalu, jolla voi kerralla korjata kaikkien rajoitusalueiden ajoratapituudet, käytä harkiten
CREATE OR REPLACE FUNCTION korjaa_rajoitusalueiden_ajoratapituudet() RETURNS BOOLEAN AS
$$
DECLARE
    rajoitusaluerivi RECORD;
    ajoratapituus INTEGER;
BEGIN
    FOR rajoitusaluerivi IN
        SELECT id,
               pituus,
               (tierekisteriosoite).tie AS tie,
               (tierekisteriosoite).aosa AS aosa,
               (tierekisteriosoite).aet AS aet,
               (tierekisteriosoite).losa AS losa,
               (tierekisteriosoite).let AS let
          FROM rajoitusalue
        LOOP
            RAISE NOTICE 'Rajoitusalue: %', rajoitusaluerivi;
            SELECT * FROM laske_tieosoitteen_ajoratapituudet(rajoitusaluerivi.tie,
                rajoitusaluerivi.aosa, rajoitusaluerivi.aet, rajoitusaluerivi.losa, rajoitusaluerivi.let) INTO ajoratapituus;
            RAISE NOTICE 'ajoratapituus %', ajoratapituus;
            -- joissakin harvinaisissa tilanteissa (tienumero 30000-40000 välillä eli rampit) on kaista-aineisto epämääräinen
            -- tällöin ei voida rakentaa luotettavaa logiikkaa miten ajoratakm lasketaan, mutta kyseessä yksikaistainen ja yksi
            -- ajoratainen tilanne, joten tällöin käytetään osan pituutta. Tämä tapahtui alle 1% rajoitusalueista.
            IF ajoratapituus IS NULL THEN
                ajoratapituus := rajoitusaluerivi.pituus;
            END IF;

            UPDATE rajoitusalue SET ajoratojen_pituus = ajoratapituus WHERE id = rajoitusaluerivi.id;
        END LOOP;

    RETURN TRUE;
END
$$ LANGUAGE plpgsql;
