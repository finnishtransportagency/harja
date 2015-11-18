-- Laskee_hoitourakan indeksilaskennassa käytettävän perusluvun
--
-- Anne Leppänen 17 November 2015 16:25
-- Perusluku, johon KAIKKIA indeksitarkistuksia verrataan on urakan kilpailuttamisvuotta edeltävän vuoden joulukuun ja
-- kilpailuttamisvuoden tammi- ja helmikuiden keskiarvo esim.2016 vuoden urakoiden perusluku on joulukuun 2015 ja 2016
-- tammi-helmikuiden keskiarvo. Perusluku on koko urakan ajan pysyvä.
CREATE OR REPLACE FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER, indeksinimi VARCHAR)
  RETURNS NUMERIC AS $$
DECLARE
  indeksilukujen_lkm           INTEGER;
  kilpailutusta_edeltava_vuosi INTEGER;
  kilpailutusvuosi             INTEGER;
  perusluku                    NUMERIC;
  tulosrivi                    RECORD;
BEGIN
  SELECT INTO kilpailutusvuosi (SELECT EXTRACT(YEAR FROM (SELECT alkupvm
                                                          FROM urakka
                                                          WHERE id = urakka_id)));
  kilpailutusta_edeltava_vuosi := kilpailutusvuosi - 1;
  -- Perusluku on urakan kilpailuttamisvuotta ed. joulukuun ja kilp.vuoden tammi- ja helmikuun pistelukujen keskiarvo
  SELECT INTO tulosrivi
    AVG(arvo) AS perusluku,
    count(*)  AS indeksilukujen_lkm
  FROM indeksi
  WHERE nimi = indeksinimi
        AND ((vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 12) OR
             (vuosi = kilpailutusvuosi AND kuukausi = 1) OR
             (vuosi = kilpailutusvuosi AND kuukausi = 2)
        );
  RAISE NOTICE 'Laskettiin hoitourakan id:llä % indeksilaskennan perusluvuksi:  %, käytetty indeksi: %', urakka_id, tulosrivi.perusluku, indeksinimi;
  -- Jos kaikkia kolmea indeksilukua ei ole, palautetaan NULL
  IF (tulosrivi.indeksilukujen_lkm = 3)
  THEN
    RETURN tulosrivi.perusluku;
  ELSE
    RAISE NOTICE 'Peruslukua ei voitu laskea koska indeksilukuja indeksillä % joulu-helmikuun aikana %-% löytyi vain : %', indeksinimi, kilpailutusta_edeltava_vuosi, kilpailutusvuosi, tulosrivi.indeksilukujen_lkm;
    RETURN NULL;
  END IF;

END;
$$ LANGUAGE plpgsql;


-- Laskee_hoitourakan asiakastyytyväisyysbonuksen indeksitarkistuksen
--
-- Paluttaa null jos joltakin aikajakson kuukaudelta puuttuu annetun indeksin pisteluvun arvo.
--
-- Hoidon ja ylläpidon palvelusopimuksesta
-- Hoidon ja ylläpidon alueurakan sanktiot, bonukset ja arvonvähennykset pvm:ltä 31.1.2014" -asiakirjan kohdassa 7 mainittuun
-- asiakastyytyväisyys-bonukseen tehdään indeksitarkistus vuosittain kaikkien loka-syyskuun piste-lukujen keskiarvon mukaan.
-- Asiakastyytyväisyysbonus maksetaan %-osuutena urakan tarjouslomakkeesta saatavasta vuosihinnasta em. indeksil-lä korotettuna.
-- Peruslukuna käytetään hoitourakan_indeksilaskennan_perusluku sprocin palauttamaa arvoa

-- BONUS = (vertailuluku / perusluku) * summa
--paluuarvon tyyppinä käytetään olemassaolevaa kuukauden_indeksikorotus_rivi (summa, korotettuna, korotus)
--DROP FUNCTION laske_hoitokauden_asiakastyytyvaisyysbonus(bonuksen_maksupvm DATE, indeksinimi       VARCHAR, summa             NUMERIC);
--DROP FUNCTION laske_hoitokauden_asiakastyytyvaisyysbonus(urakka_id INTEGER, bonuksen_maksupvm DATE, indeksinimi       VARCHAR, summa             NUMERIC);
CREATE OR REPLACE FUNCTION laske_hoitokauden_asiakastyytyvaisyysbonus(
  urakka_id         INTEGER,
  bonuksen_maksupvm DATE,
  indeksinimi       VARCHAR,
  summa             NUMERIC)
  RETURNS kuukauden_indeksikorotus_rivi AS $$
DECLARE
  kerroin            NUMERIC;
  perusluku          NUMERIC;
  vertailuluku       NUMERIC;
  alkuv              INTEGER;
  loppuv             INTEGER;
  indeksilukujen_lkm INTEGER;
  tulosrivi          RECORD;
BEGIN
  -- asiakastyytyväisyysbonus myönnetään hoitokauden jälkeen.
  -- Etsitään edellisen hoitokauden alkuvuosi
  CASE
    WHEN (SELECT EXTRACT(MONTH FROM bonuksen_maksupvm) :: INTEGER) BETWEEN 10 AND 12
    THEN
      alkuv := (SELECT EXTRACT(YEAR FROM bonuksen_maksupvm) :: INTEGER) - 1;
  ELSE
    alkuv := (SELECT EXTRACT(YEAR FROM bonuksen_maksupvm) :: INTEGER) - 2;
  END CASE;
  loppuv := alkuv + 1;

  RAISE NOTICE 'Lasketaan asiakastyytyväisyysbonus summasta % indeksillä % hoitokaudelle %-%', summa, indeksinimi, alkuv, loppuv;

  IF (indeksinimi IS NULL)
  THEN
    RAISE NOTICE 'Indeksiä ei käytetty tässä maksussa.';
    RETURN (summa, summa, 0 :: NUMERIC);
  END IF;

  perusluku := hoitourakan_indeksilaskennan_perusluku(urakka_id, indeksinimi);
  -- Kerroin on hoitokauden loka-syyskuun keskiarvo
  SELECT INTO tulosrivi
    AVG(arvo) AS vertailuluku,
    count(*)  AS indeksilukujen_lkm
  FROM indeksi
  WHERE nimi = indeksinimi
        AND ((vuosi = alkuv AND kuukausi = 10) OR
             (vuosi = alkuv AND kuukausi = 11) OR
             (vuosi = alkuv AND kuukausi = 12) OR
             (vuosi = loppuv AND kuukausi = 1) OR
             (vuosi = loppuv AND kuukausi = 2) OR
             (vuosi = loppuv AND kuukausi = 3) OR
             (vuosi = loppuv AND kuukausi = 4) OR
             (vuosi = loppuv AND kuukausi = 5) OR
             (vuosi = loppuv AND kuukausi = 6) OR
             (vuosi = loppuv AND kuukausi = 7) OR
             (vuosi = loppuv AND kuukausi = 8) OR
             (vuosi = loppuv AND kuukausi = 9));
  kerroin := tulosrivi.vertailuluku / perusluku;
  RAISE NOTICE 'Bonuslaskennassa käytetty vertailuluku: %, perusluku: %, kerroin: %', tulosrivi.vertailuluku, perusluku, kerroin;

  -- Jos kaikkia hoitokauden indeksilukuja ei ole, palautetaan NULL
  IF (tulosrivi.indeksilukujen_lkm = 12)
  THEN
    RETURN (summa, summa * kerroin, summa * kerroin - summa);
  ELSE
    RAISE NOTICE 'Asiakastyytyväisyysbonusta ei voitu laskea koska indeksilukuja indeksillä % hoitokaudelle %-% löytyi vain : %', indeksinimi, alkuv, loppuv, tulosrivi.indeksilukujen_lkm;
    RETURN (summa, NULL :: NUMERIC, NULL :: NUMERIC);
  END IF;
END;
$$ LANGUAGE plpgsql;
