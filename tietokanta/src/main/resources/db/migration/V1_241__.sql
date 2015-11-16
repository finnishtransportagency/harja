-- Laskee_hoitourakan asiakastyytyväisyysbonuksen indeksitarkistuksen
--
-- Paluttaa null jos joltakin aikajakson kuukaudelta puuttuu annetun indeksin pisteluvun arvo.
--
-- Hoidon ja ylläpidon palvelusopimuksesta
-- Hoidon ja ylläpidon alueurakan sanktiot, bonukset ja arvonvähennykset pvm:ltä 31.1.2014" -asiakirjan kohdassa 7 mainittuun
-- asiakastyytyväisyys-bonukseen tehdään indeksitarkistus vuosittain kaikkien loka-syyskuun piste-lukujen keskiarvon mukaan.
-- Asiakastyytyväisyysbonus maksetaan %-osuutena urakan tarjouslomakkeesta saatavasta vuosihinnasta em. indeksil-lä korotettuna.

--paluuarvon tyyppinä käytetään olemassaolevaa kuukauden_indeksikorotus_rivi (summa, korotettuna, korotus)

CREATE OR REPLACE FUNCTION laske_hoitokauden_asiakastyytyvaisyysbonus(
  bonuksen_maksupvm DATE,
  indeksinimi       VARCHAR,
  summa             NUMERIC)
  RETURNS kuukauden_indeksikorotus_rivi AS $$
DECLARE
  kerroin            NUMERIC;
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

  IF (indeksinimi IS NULL) THEN
    RAISE NOTICE 'Indeksiä ei käytetty tässä maksussa.';
    RETURN (summa, summa, 0::NUMERIC);
  END IF;

  -- Kerroin on hoitokauden loka-syyskuun keskiarvo
  SELECT INTO tulosrivi
    AVG(arvo) / 100.0 AS kerroin,
    count(*)          AS indeksilukujen_lkm
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
  RAISE NOTICE 'tulosrivi: %', tulosrivi;
  -- Jos kaikkia hoitokauden indeksilukuja ei ole, palautetaan NULL
  IF (tulosrivi.indeksilukujen_lkm = 12)
  THEN
    RETURN (summa, summa * tulosrivi.kerroin, summa * tulosrivi.kerroin - summa);
  ELSE
    RAISE NOTICE 'Asiakastyytyväisyysbonusta ei voitu laskea koska indeksilukuja indeksillä % hoitokaudelle %-% löytyi vain : %', indeksinimi, alkuv, loppuv, tulosrivi.indeksilukujen_lkm;
    RETURN NULL;
  END IF;
END;
$$ LANGUAGE plpgsql;
