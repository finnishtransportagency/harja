ALTER TABLE erilliskustannus ADD COLUMN urakka INTEGER REFERENCES urakka(id);

-- Erilliskustannusten raportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
  'erilliskustannukset', 'Erilliskustannukset',
  ARRAY['urakka'::raporttikonteksti, 'hallintayksikko'::raporttikonteksti, 'hankinta-alue'::raporttikonteksti, 'koko maa'::raporttikonteksti],
  ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri,
  ('Toimenpide','urakan-toimenpide',false,NULL)::raporttiparametri],
  '#''harja.palvelin.raportointi.raportit.erilliskustannukset/suorita',
  'hoito'::urakkatyyppi
);

-- Laskee_hoitourakan indeksilaskennassa käytettävän perusluvun
--
-- Anne Leppänen 17 November 2015 16:25
-- Perusluku, johon KAIKKIA indeksitarkistuksia verrataan on urakan kilpailuttamisvuotta edeltävän vuoden joulukuun ja
-- kilpailuttamisvuoden tammi- ja helmikuiden keskiarvo esim. 2016 vuoden urakoiden perusluku on joulukuun 2015 ja 2016
-- tammi-helmikuiden keskiarvo. Perusluku on koko urakan ajan pysyvä.
CREATE OR REPLACE FUNCTION hoitourakan_indeksilaskennan_perusluku(urakka_id INTEGER)
  RETURNS NUMERIC AS $$
DECLARE
  indeksilukujen_lkm           INTEGER;
  kilpailutusta_edeltava_vuosi INTEGER;
  kilpailutusvuosi             INTEGER;
  indeksinimi                  VARCHAR;
  perusluku                    NUMERIC;
  tulosrivi                    RECORD;
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



DROP FUNCTION kuukauden_indeksikorotus(DATE, VARCHAR, NUMERIC);

-- Korjataan myös kuukauden_indeksikorotus käyttämään indeksilaskennan peruslukua
-- BONUS = (vertailuluku / perusluku) * summa
-- esim. indeksin arvo 105, perusluku 103 summa 1000e:
-- summa indeksillä korotettuna (105/103) * 1 000 € = 1 019,42 €
CREATE OR REPLACE FUNCTION kuukauden_indeksikorotus(pvm date, indeksinimi varchar, summa NUMERIC, urakka_id INTEGER)
  RETURNS NUMERIC(10,2) AS $$
DECLARE
  vertailuluku NUMERIC;
  perusluku NUMERIC;
BEGIN
  -- Kerroin on ko. indeksin arvo ko. kuukautena ja vuonna
  perusluku := hoitourakan_indeksilaskennan_perusluku(urakka_id);
  SELECT arvo
  FROM indeksi
  WHERE nimi = indeksinimi
        AND vuosi = (SELECT EXTRACT(YEAR FROM pvm)) AND kuukausi = (SELECT EXTRACT(MONTH FROM pvm))
  INTO vertailuluku;
  -- Jos yhtään indeksilukuja ei ole, kerroin on NULL, jolloin myös
  -- tämä lasku palauttaa NULL.
  RETURN (vertailuluku / perusluku) * summa;
END;
$$ LANGUAGE plpgsql;
