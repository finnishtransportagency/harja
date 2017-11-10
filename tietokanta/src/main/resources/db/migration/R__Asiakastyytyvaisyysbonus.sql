CREATE OR REPLACE FUNCTION laske_hoitokauden_asiakastyytyvaisyysbonus(
  urakka_id         INTEGER,
  bonuksen_maksupvm DATE,
  indeksinimi       VARCHAR,
  summa             NUMERIC)
  RETURNS kuukauden_indeksikorotus_rivi AS $$
DECLARE
  kerroin            NUMERIC;
  perusluku          NUMERIC;
  alkuv              INTEGER;
  loppuv             INTEGER;
  tulosrivi          RECORD;
BEGIN
  -- asiakastyytyväisyysbonus kirjataan samalle hoitokaudelle kuin miltä se myön-
  -- netään, yleensä syyskuulle.
  CASE
    WHEN (SELECT EXTRACT(MONTH FROM bonuksen_maksupvm) :: INTEGER) BETWEEN 10 AND 12
    THEN
      alkuv := (SELECT EXTRACT(YEAR FROM bonuksen_maksupvm) :: INTEGER);
  ELSE
    alkuv := (SELECT EXTRACT(YEAR FROM bonuksen_maksupvm) :: INTEGER) - 1;
  END CASE;
  loppuv := alkuv + 1;

  RAISE NOTICE 'Lasketaan asiakastyytyväisyysbonus summasta % indeksillä % hoitokaudelle %-%', summa, indeksinimi, alkuv, loppuv;

  IF (indeksinimi IS NULL)
  THEN
    RAISE NOTICE 'Indeksiä ei käytetty tässä maksussa.';
    RETURN (summa, summa, 0 :: NUMERIC);
  END IF;

  perusluku := indeksilaskennan_perusluku(urakka_id);
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