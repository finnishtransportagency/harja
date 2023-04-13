-- Uusi suolasakon laskenta

CREATE OR REPLACE FUNCTION hoitokauden_suolasakko(
       urakka_id INTEGER,
       hk_alkupvm DATE,
       hk_loppupvm DATE)
  RETURNS NUMERIC AS $$
DECLARE
 ss suolasakko%rowtype; -- hoitokauden suolasakkomäärittely
 lampotilat lampotilat%rowtype; -- talvikauden lämpötilat
 lampotilapoikkeama NUMERIC;
 suolankaytto NUMERIC; -- yhteenlaskettu suolan käyttö hoitokaudella
 sallittu_suolankaytto NUMERIC; -- kuinka paljon suolaa sallitaan
 suolasakko NUMERIC; -- suolasakon määrä
BEGIN
 -- Haetaan relevantti suolasakkomäärittely ja lämpötilat
 SELECT * INTO ss FROM suolasakko
  WHERE urakka = urakka_id
    AND hoitokauden_alkuvuosi = EXTRACT(YEAR from hk_alkupvm);

 IF ss IS NULL OR ss.kaytossa = FALSE
 THEN
   RAISE NOTICE 'Urakalle % ei ole suolasakkomäärittelyä tai suolasakot eivät ole käyttössä % - %', urakka_id, hk_alkupvm, hk_loppupvm;
   RETURN NULL;
 END IF;
 
 SELECT * INTO lampotilat FROM lampotilat
  WHERE urakka = urakka_id
    AND alkupvm = hk_alkupvm AND loppupvm = hk_loppupvm;
    
 IF (lampotilat IS NULL OR lampotilat.keskilampotila IS NULL OR lampotilat.pitka_keskilampotila IS NULL)
 THEN
   RAISE NOTICE 'Urakalle % ei ole lämpötiloja hoitokaudelle % - %', urakka_id, hk_alkupvm, hk_loppupvm;
   RAISE NOTICE 'Keskilämpötila hoitokaudella %, pitkän ajan keskilämpötila %', lampotilat.keskilampotila, lampotilat.pitka_keskilampotila;
   RETURN NULL;
 END IF;
 
 RAISE NOTICE 'maksukuukausi: %', ss.maksukuukausi;

 -- HUOM: sallittu suolankäyttö ja raportoitu suolankäyttö
 -- oletetaan olevan kirjattu tonneina, joka on nykytilanne
 -- tietokannan materiaalikoodin yksiköissä.
 -- Jos raportointia tehdään muissa yksiköissä,
 -- näiden hakujen tulee muuntaa ne tonneiksi laskentaa varten.
   
 -- Haetaan suolan käytön rajat hoitokaudella
 sallittu_suolankaytto := ss.talvisuolaraja;

 RAISE NOTICE 'sallittu suolankäyttö: %', sallittu_suolankaytto;
 
 -- Haetaan suolankäytön toteuma
 SELECT SUM(maara) INTO suolankaytto
   FROM toteuma_materiaali tm
        JOIN materiaalikoodi mk ON tm.materiaalikoodi=mk.id
        JOIN toteuma t ON tm.toteuma = t.id
  WHERE mk.materiaalityyppi = 'talvisuola'::materiaalityyppi
    AND t.urakka = urakka_id
    AND t.alkanut >= hk_alkupvm AND t.alkanut <= hk_loppupvm;
 RAISE NOTICE 'Suolaa käytetty: %', suolankaytto;
 
 -- Tarkistetaan lämpötilakorjaus sallittuun suolamäärään
 lampotilapoikkeama := lampotilat.keskilampotila - lampotilat.pitka_keskilampotila;
 IF lampotilapoikkeama >= 4.0 THEN
   RAISE NOTICE 'Lämpötilapoikkeama % >= 4 astetta, 30%% korotus sallittuun suolankäyttöön', lampotilapoikkeama;
   sallittu_suolankaytto := 1.30 * sallittu_suolankaytto;
 ELSIF lampotilapoikkeama >= 3.0 THEN
   RAISE NOTICE 'Lämpötilapoikkeama % >= 3 astetta, 20%% korotus sallittuun suolankäyttöön', lampotilapoikkeama;
   sallittu_suolankaytto := 1.20 * sallittu_suolankaytto;
 ELSIF lampotilapoikkeama > 2.0 THEN
   RAISE NOTICE 'Lämpötilapoikkeama % > 2 astetta, 10%% korotus sallittuun suolankäyttöön', lampotilapoikkeama;
   sallittu_suolankaytto := 1.10 * sallittu_suolankaytto;
 ELSE
   RAISE NOTICE 'Lämpötilapoikkeama % alle 2 astetta, ei korotusta sallittuun suolankäyttöön', lampotilapoikkeama;
 END IF;

 -- Tarkistetaan ylittyykö sallittu suolankäyttö yli 5%
 IF suolankaytto > 1.05 * sallittu_suolankaytto THEN
   suolasakko := ss.maara * (suolankaytto - (1.05 * sallittu_suolankaytto));
 ELSE
   suolasakko := 0.0;
 END IF;
   
 RETURN suolasakko;
END;
$$ LANGUAGE plpgsql;
