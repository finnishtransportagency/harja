-- Indeksilaskennan perusluvut

CREATE OR REPLACE FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER, indeksinimi VARCHAR)
  RETURNS NUMERIC AS $$
DECLARE
  indeksilukujen_lkm           INTEGER;
  kilpailutusta_edeltava_vuosi INTEGER;
  kilpailutusvuosi             INTEGER;
  perusluku                    NUMERIC;
  tulosrivi                    RECORD;
  indeksit_kaytossa            BOOLEAN;
BEGIN
  SELECT u.indeksit_kaytossa FROM urakka u WHERE u.id = urakka_id INTO indeksit_kaytossa;
  IF indeksit_kaytossa = FALSE THEN
    RETURN NULL;
  ELSE
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
  END IF;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER)
  RETURNS NUMERIC AS $$
DECLARE
  kilpailutusvuosi INTEGER;
  indeksinimi TEXT;
BEGIN
  -- Päätellään indeksilaskennan perustiedot
  SELECT INTO kilpailutusvuosi (SELECT EXTRACT(YEAR FROM (SELECT alkupvm
                                                          FROM urakka
                                                          WHERE id = urakka_id)));
  IF kilpailutusvuosi < 2017 THEN
    indeksinimi := 'MAKU 2005';
  ELSE
    indeksinimi := 'MAKU 2010';
  END IF;

  RETURN hoitourakan_indeksilaskennan_perusluku(urakka_id, indeksinimi);
END;
$$ LANGUAGE plpgsql;
