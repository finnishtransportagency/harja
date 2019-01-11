INSERT INTO urakkatyypin_indeksi (urakkatyyppi, indeksinimi, raakaaine, koodi) VALUES ('vesivayla-kanavien-hoito', 'Palvelujen tuottajahintaindeksi 2015', null, 'PTHI2015'); -- kanavien hoidon indeksi
INSERT INTO urakkatyypin_indeksi (urakkatyyppi, indeksinimi, raakaaine, koodi) VALUES ('hoito', 'MAKU 2015', null, 'MAKU2015'); -- teiden hoidon uusi indeksi

UPDATE urakka set indeksi = 'Palvelujen tuottajahintaindeksi 2010' where tyyppi = 'vesivayla-kanavien-hoito'; -- Saimaan kanava

CREATE OR REPLACE FUNCTION aseta_urakan_oletusindeksi()
RETURNS trigger AS $$
BEGIN

  IF NEW.tyyppi = 'hoito' THEN
    IF EXTRACT(year FROM NEW.alkupvm) < 2017 THEN
      NEW.indeksi := 'MAKU 2005';
    ELSEIF EXTRACT(year FROM NEW.alkupvm) < 2018 THEN
      NEW.indeksi := 'MAKU 2010';
    ELSE
      NEW.indeksi := 'MAKU 2015';
    END IF;

  ELSEIF NEW.tyyppi = 'vesivayla-hoito' THEN
    IF EXTRACT(year FROM NEW.alkupvm) < 2017 THEN
      NEW.indeksi := 'MAKU 2005 kunnossapidon osaindeksi';
    ELSE
      NEW.indeksi := 'MAKU 2010 Maarakennuskustannukset, kokonaisindeksi';
    END IF;

  ELSEIF NEW.tyyppi = 'vesivayla-kanavien-hoito' THEN
    IF EXTRACT(year FROM NEW.alkupvm) < 2017 THEN
      NEW.indeksi := 'Palvelujen tuottajahintaindeksi 2010';
    ELSE
      NEW.indeksi := 'Palvelujen tuottajahintaindeksi 2015';
    END IF;

  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION indeksilaskennan_perusluku (urakka_id integer) RETURNS numeric
AS $$
DECLARE
  kilpailutusta_edeltava_vuosi INTEGER;
  kilpailutusvuosi             INTEGER;
  tulosrivi                    RECORD;
  urakan_alkupvm               DATE;
  indeksinimi TEXT;
  urakkatyyppi TEXT;
BEGIN
  SELECT indeksi FROM urakka WHERE id = urakka_id INTO indeksinimi;
  SELECT alkupvm FROM urakka WHERE id = urakka_id INTO urakan_alkupvm;
  SELECT tyyppi FROM urakka WHERE id = urakka_id INTO urakkatyyppi;
  IF indeksinimi IS NULL THEN
    RAISE NOTICE 'Indeksit eivät ole käytössä urakassa %', urakka_id;
    RETURN NULL;
  ELSE
    SELECT INTO kilpailutusvuosi (SELECT EXTRACT(YEAR FROM urakan_alkupvm));
    kilpailutusta_edeltava_vuosi := kilpailutusvuosi - 1;

    -- Tien hoito:
      -- 1) ennen vuotta 2017 alkavien urakoiden indeksi MAKU 2005 ja perusluku on urakan kilpailuttamisvuotta
      -- ed. joulukuun ja kilp.vuoden tammi- ja helmikuun pistelukujen keskiarvo
      -- 2) 2017 alkavien urakoiden indeksin perusluku on 2016 syys-, loka-, marraskuun
      --pistelukujen keskiarvo.
    -- Vesiväyläurakoissa (maili Ismo Kohoselta Jarnolle ja Riitalle 11.1.2018 klo 8:50):
      -- 3) Vuonna 2013 alkaneissa (alkaneet vuoden alusta 1.1.2013 ja päättyvät päättymisvuonna 31.7) urakoissa Kunnossapidon osaindeksi MAKU 2005 = 100
      -- ja perusluku lasketaan vuoden 2012 touko-, kesä- ja heinäkuun keskiarvosta yhden desimaalin tarkkuudella (132,9).
      -- 4) Vuonna 2014 – 2016 alkaneissa urakoissa (alkaneet 1.8. ja päättyvät 31.7) Kunnossapidon osaindeksi MAKU 2005 = 100
      -- ja perusluku lasketaan urakan alkamisvuoden tammi-, helmi- ja maaliskuun keskiarvosta yhden indeksin tarkkuudella.
      -- 5) Vuonna 2017 ja eteenpäin alkaneissa urakoissa (alkaneet 1.8. ja päättyvät 31.7) Maarakennuskustannukset, kokonaisindeksi MAKU 2010 = 100
      -- ja perusluku lasketaan urakan alkamisvuoden tammi-, helmi- ja maaliskuun keskiarvosta yhden indeksin tarkkuudella.
    -- Kanavaurakoissa käytössä Palvelujen tuottajahintaindeksi PTHI
      -- 6) TODO: tarkista sääntö

    SELECT INTO tulosrivi
      AVG(arvo) AS perusluku,
      count(*)  AS indeksilukujen_lkm
    FROM indeksi
    WHERE nimi = indeksinimi
          AND (CASE

               -- 1)
               WHEN urakkatyyppi = 'hoito' AND urakan_alkupvm < '2017-10-1'
                 THEN (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 12) OR
                      (vuosi = kilpailutusvuosi AND kuukausi = 1) OR
                      (vuosi = kilpailutusvuosi AND kuukausi = 2)

               -- 2)
               WHEN urakkatyyppi = 'hoito' AND urakan_alkupvm > '2017-9-30'
                 THEN (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 9) OR
                      (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 10) OR
                      (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 11)

               -- 3)
               WHEN urakkatyyppi = 'vesivayla-hoito' AND urakan_alkupvm < '2013-8-2'
                 THEN (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 5) OR
                      (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 6) OR
                      (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 7)

               -- 4) ja 5)
               WHEN urakkatyyppi = 'vesivayla-hoito'
                 THEN (vuosi = kilpailutusvuosi AND kuukausi = 1) OR
                      (vuosi = kilpailutusvuosi AND kuukausi = 2) OR
                      (vuosi = kilpailutusvuosi AND kuukausi = 3)
               -- 6)
               WHEN urakkatyyppi = 'vesivayla-kanavien-hoito'
                 THEN (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 8) OR
                      (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 9) OR
                      (vuosi = kilpailutusta_edeltava_vuosi AND kuukausi = 10)
              END
          );
    RAISE NOTICE 'Laskettiin urakan id:llä % indeksilaskennan perusluvuksi:  %, käytetty indeksi: %, urakkatyyppi: %', urakka_id, tulosrivi.perusluku, indeksinimi, urakkatyyppi;
    -- Jos kaikkia kolmea indeksilukua ei ole, palautetaan NULL
    IF (tulosrivi.indeksilukujen_lkm = 3)
    THEN
      RETURN round(tulosrivi.perusluku, 1);
    ELSE
      RAISE NOTICE 'Peruslukua ei voitu laskea koska indeksilukuja indeksillä % tarkastelujakson aikana %-% löytyi vain : %', indeksinimi, kilpailutusta_edeltava_vuosi, kilpailutusvuosi, tulosrivi.indeksilukujen_lkm;
      RETURN NULL;
    END IF;
  END IF;
END;
$$ LANGUAGE plpgsql;

