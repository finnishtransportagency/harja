DROP FUNCTION IF EXISTS lisaa_tai_paivita_toimenpidekoodi
(p_nimi VARCHAR(255),
p_koodi VARCHAR(16),
p_taso INTEGER,
p_yksikko VARCHAR(32),
p_tuotenumero INTEGER,
p_historiakuva BOOLEAN,
p_kokonaishintainen BOOLEAN,
p_emo_nimi VARCHAR(255),
p_emo_koodi VARCHAR(16),
p_emo_taso INTEGER );

CREATE OR REPLACE FUNCTION lisaa_toimenpidekoodi
  (p_nimi        VARCHAR(255),
   p_koodi       VARCHAR(16),
   p_taso        INTEGER,
   p_yksikko     VARCHAR(32),
   p_tuotenumero INTEGER,
   p_emo_nimi    VARCHAR(255),
   p_emo_koodi   VARCHAR(16),
   p_emo_taso    INTEGER)
  RETURNS VOID AS $$
DECLARE
  toimenpiteen_id INTEGER;
  p_emo_id        INTEGER;
BEGIN

  -- Hae rivin id
  IF p_koodi IS NULL
  THEN
    SELECT id
    INTO toimenpiteen_id
    FROM toimenpidekoodi
    WHERE nimi = p_nimi AND taso = p_taso;
  ELSE
    SELECT id
    INTO toimenpiteen_id
    FROM toimenpidekoodi
    WHERE koodi = p_koodi AND taso = p_taso;
  END IF;

  -- Hae emon id
  IF p_taso = 1
  THEN
    SELECT NULL
    INTO p_emo_id;
  ELSE
    IF p_emo_koodi IS NULL
    THEN
      SELECT id
      INTO p_emo_id
      FROM toimenpidekoodi
      WHERE nimi = p_emo_nimi AND taso = p_emo_taso;
    ELSE
      SELECT id
      INTO p_emo_id
      FROM toimenpidekoodi
      WHERE koodi = p_emo_koodi AND taso = p_emo_taso;
    END IF;
  END IF;

  INSERT INTO toimenpidekoodi
  (nimi,
   koodi,
   emo,
   taso,
   yksikko,
   tuotenumero)
  VALUES
    (p_nimi,
     p_koodi,
     p_emo_id,
     p_taso,
     p_yksikko,
     p_tuotenumero)
  ON CONFLICT DO NOTHING;

END;
$$
LANGUAGE plpgsql;

SELECT lisaa_toimenpidekoodi(
    'Uus- tai laajennusinvestointi, rautatie','10000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Ratalinja','10100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Uuden radan rakentaminen, päällysrakenne','10101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10100') );
SELECT lisaa_toimenpidekoodi(
    'Uuden radan rakentaminen, alus- ja pohjarakenne','10102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10100') );
SELECT lisaa_toimenpidekoodi(
    'Olemassa olevan radan päällysrakenteen vahvistaminen','10103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10100') );
SELECT lisaa_toimenpidekoodi(
    'Olemassa olevan radan alus- ja pohjarakenteen vahvistaminen','10104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10100') );
SELECT lisaa_toimenpidekoodi(
    'Liikennettä palveleva alue rautatie','10110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Laiturialueet','10111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Terminaalialueet, henkilö (matkakeskus)','10112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Terminaalialueet, tavara','10113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Ratapihan rakentaminen, päällysrakenne','10114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Ratapihan rakentaminen,  alus- ja pohjarakenne','10115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Kohtauspaikan rakentaminen, päällysrakenne','10116',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Kohtauspaikan rakentaminen,  alus- ja pohjarakenne','10117',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Raiteenvaihtopaikan rakentaminen, päällysrakenne','10118',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Raiteenvaihtopaikan rakentaminen,  alus- ja pohjarakenne','10119',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10110') );
SELECT lisaa_toimenpidekoodi(
    'Rautatietunneliterminaalin rakentaminen','10120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10120') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen järjestely','10130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen ylikulku','10131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10130') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen alikulku','10132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10130') );
SELECT lisaa_toimenpidekoodi(
    'Muu kevyen liikenteen järjestely','10133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10130') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeys','10140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeyksen rakentaminen','10141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10140') );
SELECT lisaa_toimenpidekoodi(
    'Rautatiesilta','10150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Uuden  ratasillan rakentaminen ','10151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10150') );
SELECT lisaa_toimenpidekoodi(
    'Uuden  ylikulkusillan rakentaminen','10152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10150') );
SELECT lisaa_toimenpidekoodi(
    'Uuden  alikulkusillan rakentaminen ','10153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10150') );
SELECT lisaa_toimenpidekoodi(
    'Uuden  risteyssillan rakentaminen ','10154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10150') );
SELECT lisaa_toimenpidekoodi(
    'Tunneli ja kallioleikkaus','10160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Rautatietunnelin rakentaminen','10161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10160') );
SELECT lisaa_toimenpidekoodi(
    'Muun rautateihin liittyvän tunnelin rakentaminen','10162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10160') );
SELECT lisaa_toimenpidekoodi(
    'Tunneleiden varustelu','10163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10160') );
SELECT lisaa_toimenpidekoodi(
    'Kallioleikkausten tekeminen','10164',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10165',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10160') );
SELECT lisaa_toimenpidekoodi(
    'Melun ja tärinän torjunta','10170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Tärinän vaimennusrakenteet','10171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10170') );
SELECT lisaa_toimenpidekoodi(
    'Melusuojaus','10172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10170') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojaus','10180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Pohjaveden suojaus ','10181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10180') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','10190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Asetinlaitteiden rakentaminen (sisälaitteet)','10191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitejärjestelmien ulkolaitteiden rakentaminen (opastimet, vapaanaolon valvonta)','10192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Kauko-ohjausjärjestelmien rakentaminen','10193',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Kaluston kunnonseurantajärjestelmän rakentaminen','10194',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeyksen varoituslaitteen rakentaminen','10195',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Junien kulunvalvontajärjestelmien rakentaminen','10196',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Muiden turvalaitteiden rakentaminen','10197',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Liikenneviestintäjärjestelmän (GSM-R) rakentaminen','10198',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Laskumäkiautomatiikka','10199',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10190') );
SELECT lisaa_toimenpidekoodi(
    'Radan merkit','10200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Turva-automaation tietoliikenneyhteyksien rakentaminen','10201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    '       Matkustajainformaatiojärjestelmä','10202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    'Näyttölaitteet','10203',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    'Kuulutuslaitteet','10204',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    'Kamerat','10205',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteät opasteet','10206',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikenneverkon laitteet','10207',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10208',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10200') );
SELECT lisaa_toimenpidekoodi(
    'Sähkörata','10210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Linjatyöt','10211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10210') );
SELECT lisaa_toimenpidekoodi(
    'Syöttöasemat','10212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10210') );
SELECT lisaa_toimenpidekoodi(
    'Kaukokäyttö','10213',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10214',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10210') );
SELECT lisaa_toimenpidekoodi(
    'Muu sähkötyö','10220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Vaihteenlämmitys','10221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10220') );
SELECT lisaa_toimenpidekoodi(
    'Valaistus ja muu sähkö','10222',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10220') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10223',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10220') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite','10230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Suoja-aidat','10231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10230') );
SELECT lisaa_toimenpidekoodi(
    'Muut varusteet ja laitteet','10232',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10230') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10233',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10230') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','10240',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Tarvemuistio','10241',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Tarveselvitys','10242',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Alustava yleissuunnittelu','10243',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Ympäristövaikutusten arviointi','10244',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu, rata','10245',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu, rataturvalaite','10246',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu, sähkörata','10247',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, rata','10248',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, rataturvalaite','10249',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10240') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, sähkörata','10250',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, rata','10251',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, rataturvalaite','10252',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, sähkörata','10253',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Muu rakennussuunnittelu','10254',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','10255',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Tarkastuslaitoksen palvelut','10256',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Suunnitelmien tarkastaminen ja ohjaus','10257',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Riskienhallinta','10258',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','10259',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10250') );
SELECT lisaa_toimenpidekoodi(
    'Rakennuttamis-, suunnitteluttamis- ja hankintapalvelut','10260',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Viestintäpalvelut','10261',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10260') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönottomenettely','10262',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10260') );
SELECT lisaa_toimenpidekoodi(
    'Laadun seuranta','10263',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10260') );
SELECT lisaa_toimenpidekoodi(
    'Muut avustustyöt','10264',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10260') );
SELECT lisaa_toimenpidekoodi(
    'Muut suunnitelmat / selvitykset','10265',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10260') );
SELECT lisaa_toimenpidekoodi(
    'Luvat','10266',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10260') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10267',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10260') );
SELECT lisaa_toimenpidekoodi(
    'Radanpidon materiaalihallinta','10270',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Betonisten ratapölkkyjen hankinta','10271',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Uusien kiskojen hankinta','10272',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Uusien vaihteiden hankinta','10273',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Kierrätyskiskojen hankinta','10274',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Kierrätysvaihteiden hankinta','10275',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Raidemateriaalien kuljetukset','10276',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Vaunuvuokrat','10277',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Rautatie-erityiset materiaalit (REM)','10278',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Puupölkkyjen hävitys','10279',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10270') );
SELECT lisaa_toimenpidekoodi(
    'Betonipölkkyjen hävitys','10280',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Pölkkyjen hävitystulot','10281',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10280') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10280') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10280') );
SELECT lisaa_toimenpidekoodi(
    'Kiskojen hävitystulot','10282',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10280') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10280') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10280') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10283',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10280') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10280') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10280') );
SELECT lisaa_toimenpidekoodi(
    'Tiedonhallinta','10290',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Esiselvitys','10291',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Vaatimusmäärittely','10292',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Toteutus','10293',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönotto','10294',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Erittelemättömät','10295',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Projektinhallinta','10296',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Laadun varmistus','10297',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','10298',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10290') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10290') );
SELECT lisaa_toimenpidekoodi(
    'Muut','10300',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='10000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='10000') );
SELECT lisaa_toimenpidekoodi(
    'Uus- tai laajennusinvestointi, tie','11000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Tieväylä','11100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Mo -tien rakentaminen','11101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Keskikaidetien rakentaminen','11102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Ohituskaistatien rakentaminen','11103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Leveäkaistatien rakentaminen','11104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Muun tien rakentaminen','11105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Ohituskaistan rakentaminen','11106',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Soratien suuntauksen parantaminen ja päällystäminen','11107',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Joukkoliikenneinvestointi','11108',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Taajamatien saneeraus','11109',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenneturvallisuustoimenpiteet','11110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11110') );
SELECT lisaa_toimenpidekoodi(
    'Liikennettä palveleva alue tie','11120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Linja-autopysäkit  ','11121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11120') );
SELECT lisaa_toimenpidekoodi(
    'Levähdys- ja palvelualueet','11122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11120') );
SELECT lisaa_toimenpidekoodi(
    'Pysäköintialueet','11123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11120') );
SELECT lisaa_toimenpidekoodi(
    'Liityntäpysäköintialueet','11124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11120') );
SELECT lisaa_toimenpidekoodi(
    'Terminaalialueet','11125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11120') );
SELECT lisaa_toimenpidekoodi(
    'Raja-asemat','11126',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11127',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11120') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen järjestely T','11130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen väylän rakentaminen','11131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11130') );
SELECT lisaa_toimenpidekoodi(
    'Suojatiejärjestelyt','11132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11130') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen ylikulku','11133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11130') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen alikulku','11134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11135',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11130') );
SELECT lisaa_toimenpidekoodi(
    'Tasoliittymä','11140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoliittymän rakentaminen','11141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11140') );
SELECT lisaa_toimenpidekoodi(
    'Kiertoliittymän rakentaminen','11142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11140') );
SELECT lisaa_toimenpidekoodi(
    'Eritasoliittymä','11150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Eritasoliittymän rakentaminen','11151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11150') );
SELECT lisaa_toimenpidekoodi(
    'Eritasoliittymän täydentäminen','11152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11150') );
SELECT lisaa_toimenpidekoodi(
    'Tiesilta','11160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Maasillan rakentaminen','11161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11160') );
SELECT lisaa_toimenpidekoodi(
    'Vesistösillan rakentaminen','11162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11160') );
SELECT lisaa_toimenpidekoodi(
    'Radan ylittävän sillan rakentaminen (sähköistetty rata)','11163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11160') );
SELECT lisaa_toimenpidekoodi(
    'Laiturin rakentaminen','11164',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11165',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11160') );
SELECT lisaa_toimenpidekoodi(
    'Tunneli','11170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Yksiaukkoisen tietunnelin rakentaminen','11171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11170') );
SELECT lisaa_toimenpidekoodi(
    'Kaksiaukkoisen tietunnelin rakentaminen','11172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11170') );
SELECT lisaa_toimenpidekoodi(
    'Muun tietunnelin rakentaminen','11173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11170') );
SELECT lisaa_toimenpidekoodi(
    'Tunnelin varustelu','11174',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11175',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11170') );
SELECT lisaa_toimenpidekoodi(
    'Meluntorjunta','11180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Meluntorjunta','11181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11180') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojaus','11190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojaus','11191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11190') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11190') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','11200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Ajantasaisen muuttuvan ohjausjärjestelmän rakentaminen','11201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Tiesääaseman rakentaminen','11202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Keli- ja liikennekameran rakentaminen','11203',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen mittausaseman rakentaminen','11204',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen automaattivalvonnan rakentaminen','11205',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Joukkoliikenteen informaatiojärjestelmän rakentaminen','11206',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Liikennevalojen rakentaminen','11207',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Häiriönhallintajärjestelmän rakentaminen','11208',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Muun ohjauksen (esim. sähköiset liik. ohjauspuomit) rakentaminen','11209',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11200') );
SELECT lisaa_toimenpidekoodi(
    'Tieliikennekeskusten laiteinvestointi','11210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Muu sekalainen liikenteen hallinnan investointi','11211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11210') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite','11220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Varuste- ja laiteinvestoinnit','11221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11220') );
SELECT lisaa_toimenpidekoodi(
    'Tievalaistus','11222',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11220') );
SELECT lisaa_toimenpidekoodi(
    'Keskikaiteen rakentaminen','11223',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11220') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11224',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11220') );
SELECT lisaa_toimenpidekoodi(
    'Saaristoliikenteen investointi','11230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Lautta-alukset','11231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11230') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11232',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11230') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','11240',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu','11241',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Tiesuunnittelu','11242',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu','11243',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','11244',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Riskienhallinta','11245',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','11246',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Rakennuttamis-, suunnitteluttamis- ja hankintapalvelut','11247',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Laadun seuranta','11248',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Viestintäpalvelut','11249',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11240') );
SELECT lisaa_toimenpidekoodi(
    'Muut avustustyöt','11250',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11251',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11250') );
SELECT lisaa_toimenpidekoodi(
    'Tiedonhallinta','11260',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Esiselvitys','11261',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Vaatimusmäärittely','11262',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Toteutus','11263',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönotto','11264',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Erittelemättömät','11265',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Projektinhallinta','11266',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Laadun varmistus','11267',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','11268',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11260') );
SELECT lisaa_toimenpidekoodi(
    'Muut','11270',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','11271',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='11270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='11270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='11270') );
SELECT lisaa_toimenpidekoodi(
    'Uus- tai laajennusinvestointi, meri','12000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Vesiväylä','12100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Ruoppaus- ja läjitystyöt','12101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12100') );
SELECT lisaa_toimenpidekoodi(
    'Läjitysaltaiden rakentaminen','12102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12100') );
SELECT lisaa_toimenpidekoodi(
    'Muut väylätyöt','12103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','12104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12100') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaite','12110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Materiaalit ja konepajatyöt','12111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12110') );
SELECT lisaa_toimenpidekoodi(
    'Laitteet, laitetyöt ','12112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12110') );
SELECT lisaa_toimenpidekoodi(
    'Rakennus- ja asennustyöt','12113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','12114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12110') );
SELECT lisaa_toimenpidekoodi(
    'Kanava','12120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Maanrakennustyöt','12121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12120') );
SELECT lisaa_toimenpidekoodi(
    'Sulkurakenteet','12122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12120') );
SELECT lisaa_toimenpidekoodi(
    'Sulkutekniikka (kojeistot ja laitteet)','12123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12120') );
SELECT lisaa_toimenpidekoodi(
    'Muut kanavarakenteet','12124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','12125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12120') );
SELECT lisaa_toimenpidekoodi(
    'Muu vesirakennustyö','12130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Laiturien rakentaminen','12131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12130') );
SELECT lisaa_toimenpidekoodi(
    'Johteiden rakentaminen','12132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12130') );
SELECT lisaa_toimenpidekoodi(
    'Muut vesirakennustyöt','12133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','12134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12130') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','12140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Tutka','12141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'Kamera','12142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'AIS-laite','12143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'VHF-laite','12144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'Reititin','12145',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen tietokoneet','12146',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen palvelimet','12147',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen näytöt','12148',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','12149',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12140') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','12150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu','12151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, meri','12152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','12153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Merenmittaustyöt','12154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Vesilupasuunnittelu','12155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Suunnitelmien tarkastaminen ja ohjaus','12156',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Riskienhallinta','12157',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','12158',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Rakennuttamis-, suunnitteluttamis- ja hankintapalvelut','12159',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12150') );
SELECT lisaa_toimenpidekoodi(
    'Viestintäpalvelut','12160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönottomenettely (väyläpäätösasiakirjojejn valmistelu)','12161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12160') );
SELECT lisaa_toimenpidekoodi(
    'Laadun seuranta','12162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12160') );
SELECT lisaa_toimenpidekoodi(
    'Laitesuunnittelu','12163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12160') );
SELECT lisaa_toimenpidekoodi(
    'YVA','12164',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12160') );
SELECT lisaa_toimenpidekoodi(
    'Ympäristö- ja olosuhdeselvitykset','12165',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','12166',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12160') );
SELECT lisaa_toimenpidekoodi(
    'Tiedonhallinta','12170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Esiselvitys','12171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Vaatimusmäärittely','12172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Toteutus','12173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönotto','12174',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Erittelemättömät','12175',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Projektinhallinta','12176',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Laadun varmistus','12177',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','12178',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12170') );
SELECT lisaa_toimenpidekoodi(
    'Muut','12180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','12181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='12180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='12180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='12180') );
SELECT lisaa_toimenpidekoodi(
    'Korvausinvestointi, rautatie ','13000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Ratalinja','13100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Koko päällysrakenteen uusiminen','13101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Pölkyn ja kiskon vaihto','13102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Pölkyn vaihto','13103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Kiskon vaihto','13104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Tukikerroksen seulonta tai uusiminen','13105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Vaihteiden uusiminen','13106',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Pohja- ja alusrakenteen vahvistaminen','13107',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Pengerlevitys','13108',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Rummun korjaus ','13109',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13100') );
SELECT lisaa_toimenpidekoodi(
    'Muu kuivatusjärjestelmän korjaus','13110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Muut ratalinjan muutostyöt','13111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13110') );
SELECT lisaa_toimenpidekoodi(
    'Liikennettä palveleva alue','13120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Laiturialueen muutostyöt','13121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'Terminaalialueen muutostyöt, henkilö (matkakeskus) ','13122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'Terminaalialueen muutostyöt, tavara ','13123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'LP: koko päällysrakenteen uusiminen','13124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'LP: Pölkyn ja kiskon vaihto','13125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'LP: Pölkyn vaihto','13126',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'LP: Kiskon vaihto','13127',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'LP: Tukikerroksen seulonta tai uusiminen','13128',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'LP: Vaihteiden uusiminen','13129',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13120') );
SELECT lisaa_toimenpidekoodi(
    'LP: Pohja- ja alusrakenteen vahvistaminen','13130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'LP: Pengerlevitys','13131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13130') );
SELECT lisaa_toimenpidekoodi(
    'LP: Rummun korjaus ','13132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13130') );
SELECT lisaa_toimenpidekoodi(
    'LP: Muu kuivatusjärjestelmän korjaus','13133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13130') );
SELECT lisaa_toimenpidekoodi(
    'LP: Muut muutostyöt','13134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13135',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13130') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen järjestely','13140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen ylikulun muutostyöt','13141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13140') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen alikulun muutostyöt','13142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13140') );
SELECT lisaa_toimenpidekoodi(
    'Muut kevyen liikenteen järjestelyjen muutostyöt','13143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13140') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeys','13150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeysten muutostyöt','13151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13150') );
SELECT lisaa_toimenpidekoodi(
    'Rautatiesilta','13160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Ratasillan peruskorjaus','13161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13160') );
SELECT lisaa_toimenpidekoodi(
    'Ylikulkusillan peruskorjaus','13162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13160') );
SELECT lisaa_toimenpidekoodi(
    'Alikulkusillan peruskorjaus','13163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13160') );
SELECT lisaa_toimenpidekoodi(
    'Risteyssillan peruskorjaus','13164',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13165',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13160') );
SELECT lisaa_toimenpidekoodi(
    'Tunneli ja kallioleikkaus','13170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Rautatietunnelin peruskorjaus','13171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13170') );
SELECT lisaa_toimenpidekoodi(
    'Muun rautateihin liittyvän tunnelin  peruskorjaus','13172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13170') );
SELECT lisaa_toimenpidekoodi(
    'Tunneleiden varustelun muutostyöt','13173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13170') );
SELECT lisaa_toimenpidekoodi(
    'Kallioleikkausten peruskorjaus','13174',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13175',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13170') );
SELECT lisaa_toimenpidekoodi(
    'Melun ja tärinän torjunta','13180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Tärinän vaimennusrakenteiden muutostyöt','13181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13180') );
SELECT lisaa_toimenpidekoodi(
    'Melusuojauksen (erittelemätön) muutostyöt','13182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13180') );
SELECT lisaa_toimenpidekoodi(
    'Meluvallin  muutostyöt','13183',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13180') );
SELECT lisaa_toimenpidekoodi(
    'Melukaiteen  muutostyöt','13184',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13180') );
SELECT lisaa_toimenpidekoodi(
    'Meluseinän  muutostyöt','13185',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13186',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13180') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojaus','13190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Pohjaveden suojauksen muutostyöt','13191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13190') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13190') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laitteet','13200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Asetinlaitteiden (sisälaitteet) muutostyöt ja laajennukset','13201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitejärjestelmien ulkolaitteiden muutostyöt ja laajennukset (opastimet, vapaanaolon valvonta)','13202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Kauko-ohjausjärjestelmän muutostyöt','13203',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Kaluston kunnonseurantajärjestelmien muutostyöt','13204',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Muiden turvalaitteiden muutostyöt','13205',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Junien kulunvalvontajärjestelmien muutostyöt','13206',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Liikenneviestintäjärjestelmän (GSM-R) muutostyöt','13207',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeyksen varoituslaitteiden muutostyöt','13208',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Laskumäkiautomatiikan muutostyöt','13209',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13200') );
SELECT lisaa_toimenpidekoodi(
    'Radan merkkien muutostyöt','13210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Turva-automaation tietoliikenneyhteyksien muutostyöt','13211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    '       Matkustajainformaatiojärjestelmä','13212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    'Näyttölaitteet','13213',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    'Kuulutuslaitteet','13214',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    'Kamerat','13215',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteät opasteet','13216',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikenneverkon laitteet','13217',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13218',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13210') );
SELECT lisaa_toimenpidekoodi(
    'Sähkörata','13220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Linjatöiden muutostyöt','13221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13220') );
SELECT lisaa_toimenpidekoodi(
    'Syöttöasemien muutostyöt','13222',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13220') );
SELECT lisaa_toimenpidekoodi(
    'Kaukokäyttö','13223',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13220') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13224',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13220') );
SELECT lisaa_toimenpidekoodi(
    'Muu sähkötyö','13230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Vaihteenlämmityksen  muutostyöt','13231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13230') );
SELECT lisaa_toimenpidekoodi(
    'Valaistus ja muu sähkö','13232',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13230') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13233',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13230') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite','13240',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Suoja-aitojen muutokset','13241',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13240') );
SELECT lisaa_toimenpidekoodi(
    'Muut varusteisiin ja laitteisiin liittyvät muutokset','13242',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13240') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13243',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13240') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','13250',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Tarvemuistio','13251',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Tarveselvitys','13252',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Alustava yleissuunnittelu','13253',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Ympäristövaikutusten arviointi','13254',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu, rata','13255',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu, rataturvalaite','13256',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu, sähkörata','13257',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, rata','13258',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, rataturvalaite','13259',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13250') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, sähkörata','13260',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, rata','13261',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, rataturvalaite','13262',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, sähkörata','13263',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Muu rakennussuunnittelu','13264',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','13265',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Tarkastuslaitoksen palvelut','13266',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Suunnitelmien tarkastaminen ja ohjaus','13267',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Riskienhallinta','13268',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','13269',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13260') );
SELECT lisaa_toimenpidekoodi(
    'Rakennuttamis-, suunnitteluttamis- ja hankintapalvelut','13270',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Viestintäpalvelut','13271',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13270') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönottomenettely','13272',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13270') );
SELECT lisaa_toimenpidekoodi(
    'Laadun seuranta','13273',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13270') );
SELECT lisaa_toimenpidekoodi(
    'Muut avustustyöt','13274',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13270') );
SELECT lisaa_toimenpidekoodi(
    'Muut suunnitelmat / selvitykset','13275',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13270') );
SELECT lisaa_toimenpidekoodi(
    'Luvat','13276',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13270') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','13277',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13270') );
SELECT lisaa_toimenpidekoodi(
    'Muut','13280',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','13281',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='13280') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='13280') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='13280') );
SELECT lisaa_toimenpidekoodi(
    'Korvausinvestointi, tie','14000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Tieväylä','14100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Päällystetyn tien uudelleen rakentaminen','14101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Soratien uudelleen rakentaminen ','14102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Soratien peruskorjaus','14103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Soratien kelirikkokorjaukset','14104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Ajoradan rakenteen korjaus ja päällysteen uusiminen','14105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen väylän rakenteiden korjaus ja päällysteen uusiminen ','14106',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Ramppien rakenteiden korjaus ja päällysteen uusiminen ','14107',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Erillisalueiden rakenteiden korjaus ja päällysteen uusiminen','14108',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14109',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14100') );
SELECT lisaa_toimenpidekoodi(
    'Liikennettä palveleva alue','14110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Levähdys- tai palvelualueen uudelleen rakentaminen','14111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14110') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen järjestely','14120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen yhteyden uudelleen rakentaminen','14121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14120') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite','141210',2,  null,  260,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Tievalaistuksen saneeraus','141211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='141210') );
SELECT lisaa_toimenpidekoodi(
    'Kaiteiden uusiminen','141212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='141210') );
SELECT lisaa_toimenpidekoodi(
    'Valaistuksen uusiminen','141213',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='141210') );
SELECT lisaa_toimenpidekoodi(
    'Aitojen ja meluesteiden uusiminen','141214',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='141210') );
SELECT lisaa_toimenpidekoodi(
    'Pysäkkikatosten ja levähdysalueiden varusteiden uusiminen','141215',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='141210') );
SELECT lisaa_toimenpidekoodi(
    'Muiden varusteiden ja laitteiden saneeraus','141216',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='141210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','141217',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='141210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='141210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14120') );
SELECT lisaa_toimenpidekoodi(
    'Tasoliittymä','14130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoliittymän uudelleen rakentaminen','14131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14130') );
SELECT lisaa_toimenpidekoodi(
    'Eritasoliittymä','14140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Eritasoliittymän uudelleen rakentaminen','14141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14140') );
SELECT lisaa_toimenpidekoodi(
    'Tiesilta','14150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Sillan uudelleen rakentaminen','14151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14150') );
SELECT lisaa_toimenpidekoodi(
    'Laiturien uudelleen rakentaminen','14152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14150') );
SELECT lisaa_toimenpidekoodi(
    'Siltojen peruskorjaus','14153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14150') );
SELECT lisaa_toimenpidekoodi(
    'Putkisiltojen uusiminen','14154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14150') );
SELECT lisaa_toimenpidekoodi(
    'Tunneli','14160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Tunnelin uudelleen rakentaminen','14161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14160') );
SELECT lisaa_toimenpidekoodi(
    'Tunnelin varustelun uusiminen','14162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14160') );
SELECT lisaa_toimenpidekoodi(
    'Meluntorjunta','14170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Meluntorjuntarakenteiden uusiminen','14171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14170') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojaus','14180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojausrakenteiden uusiminen','14181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14180') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','14190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Ajantasaisen muuttuvan ohjausjärjestelmän muutostyöt','14191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Tiesääaseman muutostyöt','14192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Keli- ja liikennekameran muutostyöt','14193',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen mittausaseman muutostyöt','14194',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen automaattivalvonnan muutostyöt','14195',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Joukkoliikenteen informaatiojärjestelmän muutostyöt','14196',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Liikennevalojen muutostyöt','14197',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Häiriönhallintajärjestelmän muutostyöt','14198',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Muun ohjauksen (esim. sähköiset liik. ohjauspuomit) muutostyöt','14199',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14190') );
SELECT lisaa_toimenpidekoodi(
    'Tieliikennekeskusten laitteiden muutostyöt','14200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Muu sekalainen liikenteen hallinnan muutostyö','14201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14200') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14200') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','14220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Yleissuunnittelu','14221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Tiesuunnittelu','14222',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, tie','14223',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','14224',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Riskienhallinta','14225',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','14226',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Rakennuttamis-, suunnitteluttamis- ja hankintapalvelut','14227',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Laadun seuranta','14228',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Viestintäpalvelut','14229',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14220') );
SELECT lisaa_toimenpidekoodi(
    'Muut avustustyöt','14230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','14231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14230') );
SELECT lisaa_toimenpidekoodi(
    'Muut','14240',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','14241',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14240') );
SELECT lisaa_toimenpidekoodi(
    'MHU Korvausinvestointi','14300',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14000') );
SELECT lisaa_toimenpidekoodi(
    'MHU Korvausinvestointi','14301',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='14300') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='14300') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='14300') );
SELECT lisaa_toimenpidekoodi(
    'Korvausinvestointi, meri','15000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Vesiväylä (kunnossapitoruoppaukset)','15100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Ruoppaus- ja läjitystyöt','15101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15100') );
SELECT lisaa_toimenpidekoodi(
    'Läjitysaltaiden rakentaminen','15102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','15103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15100') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaite','15110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Materiaalit ja konepajatyöt','15111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15110') );
SELECT lisaa_toimenpidekoodi(
    'Laitteet, laitetyöt','15112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15110') );
SELECT lisaa_toimenpidekoodi(
    'Rakennus- ja asennustyöt','15113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','15114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15110') );
SELECT lisaa_toimenpidekoodi(
    'Kanava','15120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Maanrakennustyöt','15121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15120') );
SELECT lisaa_toimenpidekoodi(
    'Sulkurakenteet','15122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15120') );
SELECT lisaa_toimenpidekoodi(
    'Sulkutekniikka (kojeistot ja laitteet)','15123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15120') );
SELECT lisaa_toimenpidekoodi(
    'Muut kanavarakenteet','15124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','15125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15120') );
SELECT lisaa_toimenpidekoodi(
    'Muu vesirakennustyö','15130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Laiturien uusiminen','15131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15130') );
SELECT lisaa_toimenpidekoodi(
    'Johteiden uusiminen','15132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15130') );
SELECT lisaa_toimenpidekoodi(
    'Muut vesirakenteiden uusimiset','15133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','15134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15130') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laitteet','15140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Tutka','15141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'Kamera','15142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'AIS-laite','15143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'VHF-laite','15144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'Reititin','15145',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen tietokoneet','15146',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen palvelimet','15147',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen näytöt','15148',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','15149',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15140') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','15150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, meri','15151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','15152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Merenmittaustyöt','15153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Vesilupasuunnittelu','15154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Suunnitelmien tarkastaminen ja ohjaus','15155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Riskienhallinta','15156',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','15157',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Rakennuttamis-, suunnitteluttamis- ja hankintapalvelut','15158',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Viestintäpalvelut','15159',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15150') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönottomenettely (väyläpäätösasiakirjojejn valmistelu)','15160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Laadun seuranta','15161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15160') );
SELECT lisaa_toimenpidekoodi(
    'Muut avustustyöt','15162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15160') );
SELECT lisaa_toimenpidekoodi(
    'Laitesuunnittelu','15163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15160') );
SELECT lisaa_toimenpidekoodi(
    'Ympäristö- ja olosuhdeselvityksdet','15164',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','15165',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15160') );
SELECT lisaa_toimenpidekoodi(
    'Muut','15170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','15171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='15170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='15170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='15170') );
SELECT lisaa_toimenpidekoodi(
    'Purku, rautatie','16000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Ratalinja','16100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Raiteen purku','16101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16100') );
SELECT lisaa_toimenpidekoodi(
    'Alus- ja pohjarakenteen sekä tukikerroksen purku','16102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16100') );
SELECT lisaa_toimenpidekoodi(
    'Liikennettä palveleva alue','16110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Kohtauspaikan purku','16111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Kohtauspaikan alus- ja pohjarakenteen sekä tukikerroksen purku','16112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Raiteenvaihtopaikan purku','16113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Raiteenvaihtopaikan alus- ja pohjarakenteen sekä tukikerroksen purku','16114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Ratapihan purku','16115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Ratapihan alus- ja pohjarakenteen sekä tukikerroksen purku','16116',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Laiturialueen purku','16117',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Terminaalialueen purku, henkilö','16118',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Terminaalialueen purku, tavara','16119',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeys','16130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeyksen purku','16131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16130') );
SELECT lisaa_toimenpidekoodi(
    'Rautatiesilta','16140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Sillan purku','16141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16140') );
SELECT lisaa_toimenpidekoodi(
    'Tunneli ja kallioleikkaus','16150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Tunnelin varusteiden purku','16151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16150') );
SELECT lisaa_toimenpidekoodi(
    'Muut purku ja täyttötyöt','16152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16150') );
SELECT lisaa_toimenpidekoodi(
    'Melun ja tärinän torjunta','16160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Melun ja tärinän torjunnan purku','16161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16160') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojaus','16170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojauksen purku','16171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16170') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','16180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Opastin-, turvalaite- ja liikenneviestintäjärjestelmän purku','16181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16180') );
SELECT lisaa_toimenpidekoodi(
    'Muiden liikenteenhallinnan laitteiden purku','16182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16183',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16180') );
SELECT lisaa_toimenpidekoodi(
    'Sähkö','16190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Sähköjärjestelmän purku','16191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16190') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16190') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite ','16200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Varusteitten ja laitteiden purku','16201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16200') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16200') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','16210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, rata','16211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, rataturvalaite','16212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Ratasuunnittelu, sähkörata','16213',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, rata','16214',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, rataturvalaite','16215',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, sähkörata','16216',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Muu rakennussuunnittelu','16217',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','16218',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Tarkastuslaitoksen palvelut','16219',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16210') );
SELECT lisaa_toimenpidekoodi(
    'Suunnitelmien tarkastaminen ja ohjaus','16220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Riskienhallinta','16221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','16222',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Rakennuttamis-, suunnitteluttamis- ja hankintapalvelut','16223',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Viestintäpalvelut','16224',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönottomenettely','16225',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Laadun seuranta','16226',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Muut avustustyöt','16227',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Luvat','16228',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','16229',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16220') );
SELECT lisaa_toimenpidekoodi(
    'Muut','16230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','16231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='16230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='16230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='16230') );
SELECT lisaa_toimenpidekoodi(
    'Purku, tie','17000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Tieväylä','17100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Tieväylän purku','17101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17100') );
SELECT lisaa_toimenpidekoodi(
    'Liikennettä palveleva alue','17110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Liikennettä palvelevan alueen purku','17111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17110') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen järjestely','17120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen väylän purku','17121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17120') );
SELECT lisaa_toimenpidekoodi(
    'Tasoliittymä','17130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoliittymän purku','17131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17130') );
SELECT lisaa_toimenpidekoodi(
    'Eritasoliittymä','17140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Eritasoliittymän purku','17141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17140') );
SELECT lisaa_toimenpidekoodi(
    'Tiesilta','17150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Sillan purku','17151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17150') );
SELECT lisaa_toimenpidekoodi(
    'Tunneli','17160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Tunnelin purku','17161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17160') );
SELECT lisaa_toimenpidekoodi(
    'Meluntorjunta','17170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Meluntorjunnan purku','17171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17170') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojaus','17180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Pohjavedensuojauksen purku','17181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17180') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','17190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laitteiden purku','17191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17190') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17190') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite','17200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Varusteiden ja laitteiden purku','17201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17200') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17200') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','17210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Purkuun liittyvät suunnittelu- ja konsulttipalvelut','17211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','17212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17210') );
SELECT lisaa_toimenpidekoodi(
    'Muut','17220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','17221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='17220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='17220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='17220') );
SELECT lisaa_toimenpidekoodi(
    'Purku, meri','18000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Vesiväylä ','18100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18000') );
SELECT lisaa_toimenpidekoodi(
    'Väylien lakkauttaminen','18101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','18102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18100') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaite','18110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18000') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitteiden poisto','18111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','18112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18110') );
SELECT lisaa_toimenpidekoodi(
    'Kanava','18120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18000') );
SELECT lisaa_toimenpidekoodi(
    'Rakenteiden poisto','18121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','18122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18120') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','18130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laitteiden purku','18131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','18132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18130') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','18140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18000') );
SELECT lisaa_toimenpidekoodi(
    'Vesilupasuunnittelu','18141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18140') );
SELECT lisaa_toimenpidekoodi(
    'Suunnitelmien tarkastaminen ja ohjaus','18142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18140') );
SELECT lisaa_toimenpidekoodi(
    'Väyläpäätösasiakirjojen valmistelu','18143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18140') );
SELECT lisaa_toimenpidekoodi(
    'Muut avustustyöt','18144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','18145',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18140') );
SELECT lisaa_toimenpidekoodi(
    'Muut','18150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','18151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='18150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='18150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='18150') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpito, rautatie','19000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Päällysrakenne','19100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Tukikerros','19101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Kisko','19102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Pölkky','19103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Geometria','19104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Muu päällysrakenteeseen kohdistuva työ','19105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Päällysrakenteeseen kohdistuva erillistyö','19106',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Raide-eristin','19107',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Lumityö','19108',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19109',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19100') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite','19110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeyskansi','19111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Radan merkki ja merkintä','19112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Laskumäkilaite','19113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Suoja-aita ja portti','19114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Kaapelireitti','19115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Kiskonvoitelulaite','19116',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Raidepuskin','19117',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19118',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19110') );
SELECT lisaa_toimenpidekoodi(
    'Rautatiesilta','19120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Päällysrakenne','19121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19120') );
SELECT lisaa_toimenpidekoodi(
    'Varuste','19122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19120') );
SELECT lisaa_toimenpidekoodi(
    'Kantava rakenne','19123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19120') );
SELECT lisaa_toimenpidekoodi(
    'Puhtaanapito, rasvaus(laakerit)','19124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19120') );
SELECT lisaa_toimenpidekoodi(
    'Muu siltaan kohdistuva työ','19125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19126',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19120') );
SELECT lisaa_toimenpidekoodi(
    'Alus- ja pohjarakenne','19130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Kuivatus','19131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19130') );
SELECT lisaa_toimenpidekoodi(
    'Alusrakenne','19132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19130') );
SELECT lisaa_toimenpidekoodi(
    'Tunneli ja kalliorakenne','19133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19130') );
SELECT lisaa_toimenpidekoodi(
    'Rautatiealue','19140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeysnäkemä','19141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19140') );
SELECT lisaa_toimenpidekoodi(
    'Huoltotie','19142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19140') );
SELECT lisaa_toimenpidekoodi(
    'Alue','19143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19140') );
SELECT lisaa_toimenpidekoodi(
    'Sähkölaite','19150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Siltojen sähkötekniikka','19151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19150') );
SELECT lisaa_toimenpidekoodi(
    'Ratajohtojärjestelmä','19152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19150') );
SELECT lisaa_toimenpidekoodi(
    'Sähköradan syöttöasema','19153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19150') );
SELECT lisaa_toimenpidekoodi(
    '110 kV syöttöjohto','19154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19150') );
SELECT lisaa_toimenpidekoodi(
    'Vahvavirtalaite','19155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19150') );
SELECT lisaa_toimenpidekoodi(
    'Kaukokäyttö','19156',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19157',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19150') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','19160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Asetinlaite- ja suojastusjärjestelmä','19161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Kauko-ohjausjärjestelmä','19162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Kulunvalvonta','19163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Virransyöttö','19164',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Ulkolaite','19165',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeys','19166',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Laskumäkilaitteisto','19167',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitetila','19168',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Muun turvalaitteen kunnossapito','19169',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19160') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitteen erillistyö','19170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    ' Matkustajainformaatiojärjestelmä','19171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19170') );
SELECT lisaa_toimenpidekoodi(
    'Näyttölaitteet','19172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19170') );
SELECT lisaa_toimenpidekoodi(
    'Kuulutuslaitteet','19173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19170') );
SELECT lisaa_toimenpidekoodi(
    'Kamerat','19174',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19170') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteät opasteet','19175',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19170') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikenneverkon laitteet','19176',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19177',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19170') );
SELECT lisaa_toimenpidekoodi(
    'Vaihde','19180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Geometria','19181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19180') );
SELECT lisaa_toimenpidekoodi(
    'Vaihteen osa','19182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19180') );
SELECT lisaa_toimenpidekoodi(
    'Vaihdepölkky','19183',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19180') );
SELECT lisaa_toimenpidekoodi(
    'Vaihteen tukikerros','19184',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19180') );
SELECT lisaa_toimenpidekoodi(
    'Lumityö','19185',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19180') );
SELECT lisaa_toimenpidekoodi(
    'Muu vaihteeseen kohdistuva työ','19186',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19187',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19180') );
SELECT lisaa_toimenpidekoodi(
    'Laituri','19190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Katosrakenne','19191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19190') );
SELECT lisaa_toimenpidekoodi(
    'Opaste','19192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19190') );
SELECT lisaa_toimenpidekoodi(
    'Alusrakenne','19193',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19190') );
SELECT lisaa_toimenpidekoodi(
    'Valaistus','19194',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19190') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19195',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19190') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','19200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpidon suunnittelupalvelut','19201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19200') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpidon teettämispalvelut','19202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19200') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpidon valvontapalvelut','19203',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19200') );
SELECT lisaa_toimenpidekoodi(
    'Projektin johto','19204',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19200') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19205',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19200') );
SELECT lisaa_toimenpidekoodi(
    'Mittaus ja inventointi','19210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Siltatarkastus','19211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Vaihdetarkastus','19212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Kävelytarkastus','19213',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Liikennepaikkatarkastus','19214',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Tarkastus liikkuvasta kalustosta','19215',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Kiskojen ultraäänitarkastus','19216',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Kiskovikojen tarkastus','19217',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Tunnelitarkastus','19218',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Radantarkastuspalvelut (EMMA, ELLI yms.)','19219',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19210') );
SELECT lisaa_toimenpidekoodi(
    'Muut mittaukset ja inventoinnit','19220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19220') );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmät ja ICT infra','19230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteät kustannukset','19231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'Toimintakunto','19232',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'Pienimuotoinen kehittäminen','19233',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöympäristön ylläpito','19234',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Versiopäivitys','19235',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Kapasiteetin kasvattaminen','19236',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Alustanvaihto','19237',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Muut palvelut','19238',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','19239',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19230') );
SELECT lisaa_toimenpidekoodi(
    'Muut','19240',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','19241',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='19240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='19240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='19240') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpito, tie','20000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Päällyste','20100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Ajoradan päällysteen uusiminen, koko ajorata (ns. laatta)','20101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20100') );
SELECT lisaa_toimenpidekoodi(
    'Ajoradan päällysteen uusiminen, osa ajorataa','20102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20100') );
SELECT lisaa_toimenpidekoodi(
    'Kevyen liikenteen väylän päällysteen uusiminen ','20103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20100') );
SELECT lisaa_toimenpidekoodi(
    'Ramppien päällysteen uusiminen ','20104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20100') );
SELECT lisaa_toimenpidekoodi(
    'Erillis- ja liitännäisalueiden päällysteen uusiminen','20105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20106',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20100') );
SELECT lisaa_toimenpidekoodi(
    'Päällysteiden paikkaus (hoidon ylläpito)','20107',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20100') );
SELECT lisaa_toimenpidekoodi(
    'Päällystetyn tien tierakenne','20110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Kuivatusjärjestelmän korjaus','20111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20110') );
SELECT lisaa_toimenpidekoodi(
    'Tiemerkintä','20120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Tiemerkintä tielinjalla','20121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20120') );
SELECT lisaa_toimenpidekoodi(
    'Herätemerkintä','20122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20120') );
SELECT lisaa_toimenpidekoodi(
    'Tiesilta','20130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Siltojen ylläpitotoimet','20131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20130') );
SELECT lisaa_toimenpidekoodi(
    'Putkisiltojen korjaus','20132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20130') );
SELECT lisaa_toimenpidekoodi(
    'Laiturien ylläpito','20133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20130') );
SELECT lisaa_toimenpidekoodi(
    'Tunnelien ylläpito','20134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20135',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20130') );
SELECT lisaa_toimenpidekoodi(
    'Soratien rakenne','20140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Soratien kuivatusrakenteiden korjaus','20141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20140') );
SELECT lisaa_toimenpidekoodi(
    'Muut erilliset toimet','20142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20140') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','20150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Ajantasaisen muuttuvan ohjausjärjestelmän ylläpito','20151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Tiesääaseman ylläpito','20152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Keli- ja liikennekameran ylläpito','20153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen mittausaseman ylläpito','20154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen automaattivalvonnan ylläpito','20155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Joukkoliikenteen informaatiojärjestelmän ylläpito','20156',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Liikennevalojen ylläpito','20157',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Häiriönhallintajärjestelmän ylläpito','20158',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Muun ohjauksen (esim. sähköiset liik. ohjauspuomit) ylläpito','20159',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20150') );
SELECT lisaa_toimenpidekoodi(
    'Tieliikennekeskusten laitteiden ylläpito','20160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Muu sekalainen liikenteen hallinnan laitteiden ylläpito','20161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20160') );
SELECT lisaa_toimenpidekoodi(
    'Varuste ja laite (ohjelmoidut korjaukset)','20170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Kaiteiden korjaus','20171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Valaistuksen korjaus','20172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Pumppaamoiden ohjelmoitu korjaaminen','20173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Aitojen ja meluesteiden korjaus','20174',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Kivetysten ja vastaavien rakenteiden korjaus','20175',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Pysäkkikatosten ja levähdysalueiden varusteiden korjaus','20176',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Liikennemerkit ja porttaalit','20177',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Muut varusteet ja laitteet','20178',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20179',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20170') );
SELECT lisaa_toimenpidekoodi(
    'Liikenneympäristön parantaminen','20180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Taajamateiden pienet täydennykset ','20181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20180') );
SELECT lisaa_toimenpidekoodi(
    'Tielinjalla tehtävät pienet täydennykset','20182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20183',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20180') );
SELECT lisaa_toimenpidekoodi(
    'MHU Ylläpito','20190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'MHU Ylläpito','20191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20190') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpidon teettämispalvelut','20192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20190') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpidon valvontapalvelut','20193',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20190') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20194',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20190') );
SELECT lisaa_toimenpidekoodi(
    'Mittaus ja inventointi','20200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Tiestön kantavuusmittaus','20201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Vaurioinventointi (mm. päällyste)','20202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Tierekisterimittaukset','20203',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Tierekisterin päivittäminen','20204',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeysinventointi','20205',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Siltojen mittaus ja inventointi','20206',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Muut mittaukset ja inventoinnit','20207',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Yleinen liikennelaskenta (nimi muutettu)','20208',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'Paikkatiedot (digiroad, ym.)','20209',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20200') );
SELECT lisaa_toimenpidekoodi(
    'PTM-mittaukset','20210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Sorateiden runkokelirikkoinventoinnit','20211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20210') );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmät ja ICT infra','20220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteät kustannukset','20221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'Toimintakunto','20222',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'Pienimuotoinen kehittäminen','20223',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöympäristön ylläpito','20224',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Versiopäivitys','20225',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Kapasiteetin kasvattaminen','20226',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Alustanvaihto','20227',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Muut palvelut','20228',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','20229',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20220') );
SELECT lisaa_toimenpidekoodi(
    'Muut','20230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','20231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='20230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='20230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='20230') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpito, meri','21000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Kunnostushanke','21100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21000') );
SELECT lisaa_toimenpidekoodi(
    'Väylien kunnostusruoppaukset','21101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitteiden peruskorjaukset','21102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Kanavarakenteiden peruskorjaukset','21103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Väylien Navi-tarkistukset','21104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Kanavalaitteiden peruskorjaukset','21105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Johteiden peruskorjaukset','21106',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Muiden rakenteiden peruskorjaukset','21107',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','21108',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21100') );
SELECT lisaa_toimenpidekoodi(
    'Väylätietojen ylläpito ','21110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21000') );
SELECT lisaa_toimenpidekoodi(
    'Väylä- ja turvalaitetietojen ylläpito (VÄRE ja VATU)','21111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21110') );
SELECT lisaa_toimenpidekoodi(
    'Varmistettujen alueiden tietojen ylläpito (VARE)','21112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','21113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21110') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan laite','21120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21000') );
SELECT lisaa_toimenpidekoodi(
    'Tutka','21121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'Kamera','21122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'AIS-laite','21123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'VHF-laite','21124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'Reititin','21125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen tietokoneet','21126',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen palvelimet','21127',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan operatiivisen ohjauksen näytöt','21128',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','21129',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21120') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','21130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21000') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpidon suunnittelu- teettämis- ja hankintapalvelut','21131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Rakennussuunnittelu, meri','21132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Maasto- ja pohjatutkimukset ja mittaukset','21133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Merenmittaustyöt','21134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Vesilupasuunnittelu','21135',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Ympäristö- ja olosuhdeselvitykset','21136',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Valvonta','21137',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönottomenettely (väyläpäätösasiakirjojen valmistelu)','21138',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','21139',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21130') );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmät ja ICT infra','21140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21000') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteät kustannukset','21141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'Toimintakunto','21142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'Pienimuotoinen kehittäminen','21143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöympäristön ylläpito','21144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Versiopäivitys','21145',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Kapasiteetin kasvattaminen','21146',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Alustanvaihto','21147',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta: Muut palvelut','21148',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','21149',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21140') );
SELECT lisaa_toimenpidekoodi(
    'Muut','21150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','21151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='21150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='21150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='21150') );
SELECT lisaa_toimenpidekoodi(
    'Hoito, rautatie','22000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Hoito','22100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22000') );
SELECT lisaa_toimenpidekoodi(
    'Vuosisopimus','22101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22100') );
SELECT lisaa_toimenpidekoodi(
    'Vuosisopimus sähkö','22102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22100') );
SELECT lisaa_toimenpidekoodi(
    'Hoidon ylimääräiset työt','22103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','22104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22100') );
SELECT lisaa_toimenpidekoodi(
    'Laiturialue','22110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22000') );
SELECT lisaa_toimenpidekoodi(
    'Talvihoito','22111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22110') );
SELECT lisaa_toimenpidekoodi(
    'Kesähoito','22112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','22113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22110') );
SELECT lisaa_toimenpidekoodi(
    'Liityntäpysäköintialue','22120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22000') );
SELECT lisaa_toimenpidekoodi(
    'Talvihoito','22121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22120') );
SELECT lisaa_toimenpidekoodi(
    'Kesähoito','22122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','22123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22120') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','22130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22000') );
SELECT lisaa_toimenpidekoodi(
    'Hoidon suunnittelu-, teettämis- ja valvontapalvelu (isännöinti)','22131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','22132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22130') );
SELECT lisaa_toimenpidekoodi(
    'Mittaus ja inventointi (olosuhde yms.)','22140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22000') );
SELECT lisaa_toimenpidekoodi(
    'Hoitoon liittyvät mittaukset ja inventoinnit','22141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','22142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22140') );
SELECT lisaa_toimenpidekoodi(
    'Muut','22150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','22151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='22150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='22150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='22150') );
SELECT lisaa_toimenpidekoodi(
    'Hoito, tie','23000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Talvihoito','23100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23000') );
SELECT lisaa_toimenpidekoodi(
    'Alueurakan talvihoito','23101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23100') );
SELECT lisaa_toimenpidekoodi(
    'Alueurakan talvihoidon lisätyöt','23102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23100') );
SELECT lisaa_toimenpidekoodi(
    'Muut erikseen tilattavat talvihoidon toimenpiteet','23103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','23104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenneympäristön hoito','23110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23000') );
SELECT lisaa_toimenpidekoodi(
    'Alueurakan liikenneympäristön hoito','23111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23110') );
SELECT lisaa_toimenpidekoodi(
    'Alueurakan liikenneympäristön hoidon lisätyöt ','23112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23110') );
SELECT lisaa_toimenpidekoodi(
    'Pumppaamojen vuosihuoltoon liittyvät toimenpiteet','23113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23110') );
SELECT lisaa_toimenpidekoodi(
    'Tievalaistuksen vuosihuoltoon liittyvät toimenpiteet','23114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23110') );
SELECT lisaa_toimenpidekoodi(
    'Muut erikseen tilattavat liikenneympäristön hoitotoimenpiteet','23115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','23116',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23110') );
SELECT lisaa_toimenpidekoodi(
    'Soratien hoito','23120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23000') );
SELECT lisaa_toimenpidekoodi(
    'Alueurakan sorateiden hoito','23121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23120') );
SELECT lisaa_toimenpidekoodi(
    'Alueurakan sorateiden hoidon lisätyöt','23122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23120') );
SELECT lisaa_toimenpidekoodi(
    'Muut erikseen tilattavat sorateiden hoitotoimenpiteet','23123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','23124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23120') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','23130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23000') );
SELECT lisaa_toimenpidekoodi(
    'Hoidon suunnittelupalvelu','23131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23130') );
SELECT lisaa_toimenpidekoodi(
    'Hoidon teettämispalvelu','23132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23130') );
SELECT lisaa_toimenpidekoodi(
    'Hoidon valvontapalvelu','23133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','23134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23130') );
SELECT lisaa_toimenpidekoodi(
    'Mittaus ja inventointi (olosuhde yms.)','23140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23000') );
SELECT lisaa_toimenpidekoodi(
    'Hoitoon liittyvät mittaukset ja inventoinnit','23141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','23142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23140') );
SELECT lisaa_toimenpidekoodi(
    'Muut','23150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23000') );
SELECT lisaa_toimenpidekoodi(
    'MHU ja HJU Hoidon johto','23151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='23150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='23150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='23150') );







SELECT lisaa_toimenpidekoodi(
    'Hoito, meri','24000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Väylänhoito ','24100',2,  null,  201,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24000') );
SELECT lisaa_toimenpidekoodi(
    'Sopimuksen mukaiset työt','24101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24100') );
SELECT lisaa_toimenpidekoodi(
    'Sopimuksen lisätyöt','24102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24100') );
SELECT lisaa_toimenpidekoodi(
    'Erikseen tilatut työt','24103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','24104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24100') );
SELECT lisaa_toimenpidekoodi(
    'Projektiin liittyvä suunnittelu-, teettämis-, ja valvontapalvelu','24110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24000') );
SELECT lisaa_toimenpidekoodi(
    'Hoidon suunnittelu-, teettämis- ja valvontapalvelu','24111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24110') );
SELECT lisaa_toimenpidekoodi(
    'Viranomaisvalvonta (muut väylänpitäjät)','24112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24110') );
SELECT lisaa_toimenpidekoodi(
    'Väylänhoidon tietojärjestelmä Reimarin tietosisällön hallinta','24113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','24114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24110') );
SELECT lisaa_toimenpidekoodi(
    'Mittaus ja inventointi (olosuhde yms.)','24120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24000') );
SELECT lisaa_toimenpidekoodi(
    'Hoitoon liittyvät mittaukset ja inventoinnit','24121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','24122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24120') );
SELECT lisaa_toimenpidekoodi(
    'Muut','24130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','24131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='24130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='24130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='24130') );
SELECT lisaa_toimenpidekoodi(
    'Käyttö, rautatie','25000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Käyttö','25100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25000') );
SELECT lisaa_toimenpidekoodi(
    'Vuosisopimus','25101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25100') );
SELECT lisaa_toimenpidekoodi(
    'Vuosisopimus sähkö','25102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','25103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25100') );
SELECT lisaa_toimenpidekoodi(
    'Energia','25110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25000') );
SELECT lisaa_toimenpidekoodi(
    'Sähköradan sähkö','25111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25110') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistösähkö','25112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25110') );
SELECT lisaa_toimenpidekoodi(
    'Tasoristeyslaitosten sähkö','25113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25110') );
SELECT lisaa_toimenpidekoodi(
    'Laitetilojen sähkö','25114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','25115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25110') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitevaraosien varastonhallintatietojärjestelmäpalvelu (Rosa)','25120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25000') );
SELECT lisaa_toimenpidekoodi(
    'Vuosisopimus','25121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','25122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25120') );
SELECT lisaa_toimenpidekoodi(
    'Turvalaitejärjestelmien tukipalvelu','25130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25000') );
SELECT lisaa_toimenpidekoodi(
    'Järjestelmätukipalvelut','25131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','25132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25130') );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmät ja ICT infra','25140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25000') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikenne   ','25141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'Puhelinliikenne','25142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'ICT integraatioiden ylläpito','25143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'ICT integraatioiden valvontapalvelu','25144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'Käyttäjätuki','25145',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'Työasemapalvelut','25146',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'Mobiililaitepalvelut','25147',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'Järjestelmäpalvelut','25148',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'Kapasiteettipalvelut','25149',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25140') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikennepalvelut','25150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25000') );
SELECT lisaa_toimenpidekoodi(
    'Etäkäyttöpalvelut','25151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25150') );
SELECT lisaa_toimenpidekoodi(
    'Laitteiden elinkaaripalvelut','25152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25150') );
SELECT lisaa_toimenpidekoodi(
    'Lisenssit ohjelmisto','25153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25150') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut laitteet','25154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25150') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut muut','25155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25150') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','25156',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','25157',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25150') );
SELECT lisaa_toimenpidekoodi(
    'Muut','25160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','25161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='25160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='25160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='25160') );
SELECT lisaa_toimenpidekoodi(
    'Käyttö, tie','26000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Energia','26101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26100') );
SELECT lisaa_toimenpidekoodi(
    'Valaistuksen sähkö ja liittymäsopimukset','26102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26100') );
SELECT lisaa_toimenpidekoodi(
    'Pumppaamoiden sähkö ja teleliikenne','26103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26100') );
SELECT lisaa_toimenpidekoodi(
    'Muu sähköenergia','26104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','26105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26100') );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmät ja ICT infra','26110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26000') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikenne   ','26111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'Puhelinliikenne','26112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'ICT integraatioiden ylläpito','26113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'ICT integraatioiden valvontapalvelu','26114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'Käyttäjätuki','26115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'Työasemapalvelut','26116',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'Mobiililaitepalvelut','26117',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'Järjestelmäpalvelut','26118',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'Kapasiteettipalvelut','26119',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26110') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikennepalvelut','26120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26000') );
SELECT lisaa_toimenpidekoodi(
    'Etäkäyttöpalvelut','26121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26120') );
SELECT lisaa_toimenpidekoodi(
    'Laitteiden elinkaaripalvelut','26122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26120') );
SELECT lisaa_toimenpidekoodi(
    'Lisenssit ohjelmisto','26123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26120') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut laitteet','26124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26120') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut muut','26125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26120') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','26126',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','26127',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26120') );
SELECT lisaa_toimenpidekoodi(
    'Muut','26130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','26131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='26130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='26130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='26130') );
SELECT lisaa_toimenpidekoodi(
    'Käyttö, meri','27000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Vesiliikenteen käyttöpalvelut','27100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27000') );
SELECT lisaa_toimenpidekoodi(
    'Sopimuksen mukaiset työt','27101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27100') );
SELECT lisaa_toimenpidekoodi(
    'Sopimuksen lisätyöt','27102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27100') );
SELECT lisaa_toimenpidekoodi(
    'Erikseen tilatut työt','27103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27100') );
SELECT lisaa_toimenpidekoodi(
    'Erillisten avattavien siltojen käyttö','27104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','27105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27100') );
SELECT lisaa_toimenpidekoodi(
    'Energia','27110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27000') );
SELECT lisaa_toimenpidekoodi(
    'Energian hankinta','27111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','27112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27110') );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmät ja ICT infra','27120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27000') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikenne   ','27121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'Puhelinliikenne','27122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'ICT integraatioiden ylläpito','27123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'ICT integraatioiden valvontapalvelu','27124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'Käyttäjätuki','27125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'Työasemapalvelut','27126',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'Mobiililaitepalvelut','27127',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'Järjestelmäpalvelut','27128',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'Kapasiteettipalvelut','27129',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27120') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikennepalvelut','27130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27000') );
SELECT lisaa_toimenpidekoodi(
    'Etäkäyttöpalvelut','27131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27130') );
SELECT lisaa_toimenpidekoodi(
    'Laitteiden elinkaaripalvelut','27132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27130') );
SELECT lisaa_toimenpidekoodi(
    'Lisenssit ohjelmisto','27133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27130') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut laitteet','27134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27130') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut muut','27135',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27130') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','27136',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','27137',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27130') );
SELECT lisaa_toimenpidekoodi(
    'Muut','27140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','27141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='27140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='27140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='27140') );
SELECT lisaa_toimenpidekoodi(
    'Merikartoitus','28000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Merenmittausten hankinta ','28100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28000') );
SELECT lisaa_toimenpidekoodi(
    'Helcom','28101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28100') );
SELECT lisaa_toimenpidekoodi(
    'Aluemittaus','28102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28100') );
SELECT lisaa_toimenpidekoodi(
    'Väylämittaus','28103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28100') );
SELECT lisaa_toimenpidekoodi(
    'Muu mittaus','28104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','28105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28100') );
SELECT lisaa_toimenpidekoodi(
    'Merikartoitustiedot','28110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28000') );
SELECT lisaa_toimenpidekoodi(
    'Syvyystietojen uusiminen','28111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Rantaviivojen uusiminen','28112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Muut merikartoitustiedot','28113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Maastotiedot','28114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Kriittiset merenmittaustiedot','28115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Muutostietojen ylläpito','28116',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Merenmittaustietojen ylläpito','28117',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','28118',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28110') );
SELECT lisaa_toimenpidekoodi(
    'Karttojen valmistus','28120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28000') );
SELECT lisaa_toimenpidekoodi(
    'Yksilehtiset painetut','28121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'Merikarttasarjat','28122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'ENC perussolu','28123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'TM- lehti','28124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'TV- lehti','28125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'ENC päivitys','28126',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'Kartta 1 kirja','28127',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'Rannikon loistot','28128',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'Sisävesien loistot','28129',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','28130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28000') );
SELECT lisaa_toimenpidekoodi(
    'Aineistopalvelut','28140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28000') );
SELECT lisaa_toimenpidekoodi(
    'Toimitus','28141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28140') );
SELECT lisaa_toimenpidekoodi(
    'Kehitystehtävä','28142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28140') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpito ','28143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','28144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28140') );
SELECT lisaa_toimenpidekoodi(
    'Muut','28150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','28151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='28150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='28150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='28150') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen hallinta, rautatie','29000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen ohjaus','29100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen operatiivinen ohjaus','29101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen ohjauksen valvonta','29102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','29103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen palvelut','29110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen hallinnan toiminnan ja järjestelmien kehittäminen R','29111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29110') );
SELECT lisaa_toimenpidekoodi(
    'Matkustajainformaatiopalvelu','29112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29110') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen muut palvelut','29113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','29114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29110') );
SELECT lisaa_toimenpidekoodi(
    'Muut','29120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','29121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='29120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='29120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='29120') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen hallinta, tie','30000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen ohjaus','30100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen operatiivinen ohjaus','30101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen ohjauksen valvonta','30102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','30103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen palvelut','30110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen hallinnan toiminnan ja järjestelmien kehittäminen','30111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30110') );
SELECT lisaa_toimenpidekoodi(
    'Matkustajainformaatiopalvelu','30112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30110') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen muut palvelut','30113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','30114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30110') );
SELECT lisaa_toimenpidekoodi(
    'Muut','30120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','30121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='30120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='30120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='30120') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen hallinta, meri','31000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen ohjaus','31100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen operatiivinen ohjaus','31101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen ohjauksen valvonta','31102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','31103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31100') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen palvelut','31110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen hallinnan toiminnan ja järjestelmien kehittäminen','31111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31110') );
SELECT lisaa_toimenpidekoodi(
    'Matkustajainformaatiopalvelu','31112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31110') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen muut palvelut','31113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','31114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31110') );
SELECT lisaa_toimenpidekoodi(
    'Muut','31120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','31121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='31120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='31120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='31120') );
SELECT lisaa_toimenpidekoodi(
    'Talvimerenkulku, rannikko','32000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Hinaajapalvelut','32100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32000') );
SELECT lisaa_toimenpidekoodi(
    'Hinaajapalvelut','32101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','32102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32100') );
SELECT lisaa_toimenpidekoodi(
    'Jäänmurtopalvelut','32110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32000') );
SELECT lisaa_toimenpidekoodi(
    'Valmius','32111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32110') );
SELECT lisaa_toimenpidekoodi(
    'Operointi','32112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32110') );
SELECT lisaa_toimenpidekoodi(
    'P-aine','32113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','32114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32110') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','32120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32000') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','32121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','32122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='32120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='32120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='32120') );
SELECT lisaa_toimenpidekoodi(
    'Talvimerenkulku, sisävedet','33000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Hinaajapalvelut','33100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='33000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='33000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='33000') );
SELECT lisaa_toimenpidekoodi(
    'Hinaajapalvelut','33101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='33100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='33100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='33100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','33102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='33100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='33100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='33100') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','33110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='33000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='33000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='33000') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','33111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='33110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='33110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='33110') );
SELECT lisaa_toimenpidekoodi(
    'Julkinen liikenne ja merenkulun tuki','34000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Linja-autoliikenteen tuki','34100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34000') );
SELECT lisaa_toimenpidekoodi(
    'Osto','34101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34100') );
SELECT lisaa_toimenpidekoodi(
    'Lipputuki','34102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','34103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34100') );
SELECT lisaa_toimenpidekoodi(
    'Junaliikenteen tuki','34110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34000') );
SELECT lisaa_toimenpidekoodi(
    'Osto','34111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34110') );
SELECT lisaa_toimenpidekoodi(
    'Lipputuki','34112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','34113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34110') );
SELECT lisaa_toimenpidekoodi(
    'Lentoliikenteen tuki','34120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34000') );
SELECT lisaa_toimenpidekoodi(
    'Osto','34121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34120') );
SELECT lisaa_toimenpidekoodi(
    'Lipputuki','34122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','34123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34120') );
SELECT lisaa_toimenpidekoodi(
    'Saaristoliikenteen palvelut','34130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34000') );
SELECT lisaa_toimenpidekoodi(
    'Yhteysalus','34131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34130') );
SELECT lisaa_toimenpidekoodi(
    'Lautta- ja lossiliikennöinti','34132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','34133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34130') );
SELECT lisaa_toimenpidekoodi(
    'Kauppa-alustuki','34140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34000') );
SELECT lisaa_toimenpidekoodi(
    'Kuukausittain maksetut tuet matkustaja-aluksille ','34141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34140') );
SELECT lisaa_toimenpidekoodi(
    'Puolivuotistuet matkustaja-aluksille','34142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34140') );
SELECT lisaa_toimenpidekoodi(
    'Puolivuotistuet lastialuksille','34143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','34144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34140') );
SELECT lisaa_toimenpidekoodi(
    'Lästimaksuavustukset','34150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34000') );
SELECT lisaa_toimenpidekoodi(
    'Lästimaksuavustuspäätökset','34151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','34152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34150') );
SELECT lisaa_toimenpidekoodi(
    'Muut','34160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','34161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='34160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='34160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='34160') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteen suunnittelupalvelu','35000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Liikennejärjestelmäsuunnittelu','35100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35000') );
SELECT lisaa_toimenpidekoodi(
    'Esiselvitys','35101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35100') );
SELECT lisaa_toimenpidekoodi(
    'Projektityö','35102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35100') );
SELECT lisaa_toimenpidekoodi(
    'Osallistumisprojekti','35103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','35104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35100') );
SELECT lisaa_toimenpidekoodi(
    'Maankäyttöä palveleva suunnittelu','35110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35000') );
SELECT lisaa_toimenpidekoodi(
    'Esiselvitys','35111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35110') );
SELECT lisaa_toimenpidekoodi(
    'Projektityö','35112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35110') );
SELECT lisaa_toimenpidekoodi(
    'Osallistumisprojekti','35113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','35114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35110') );
SELECT lisaa_toimenpidekoodi(
    'Palvelutasosuunnittelu','35120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35000') );
SELECT lisaa_toimenpidekoodi(
    'Esiselvitys','35121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35120') );
SELECT lisaa_toimenpidekoodi(
    'Projektityö','35122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35120') );
SELECT lisaa_toimenpidekoodi(
    'Osallistumisprojekti','35123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','35124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35120') );
SELECT lisaa_toimenpidekoodi(
    'Muut','35130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','35131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='35130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='35130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='35130') );
SELECT lisaa_toimenpidekoodi(
    'Väylänpidon omaisuushallinta','36000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistötoimitukset ja -hankinnat','36100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Maksettavat korvaukset','36101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36100') );
SELECT lisaa_toimenpidekoodi(
    'Saatava kauppahinta tai muu vastike','36102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36100') );
SELECT lisaa_toimenpidekoodi(
    'Muun omaisuuden korvaukset','36110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Haitankorvausmenot','36111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36110') );
SELECT lisaa_toimenpidekoodi(
    'Vahingonkorvausmenot','36112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36110') );
SELECT lisaa_toimenpidekoodi(
    'Haitankorvaustulot','36113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36110') );
SELECT lisaa_toimenpidekoodi(
    'Vahingonkorvaustulot','36114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36110') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöjen menot ','36120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Maksetut tilavuokrat asunnoista ','36121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36120') );
SELECT lisaa_toimenpidekoodi(
    'Maksetut tilavuokrat toimitiloista ','36122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36120') );
SELECT lisaa_toimenpidekoodi(
    'Maksetut tilavuokrat muista tiloista','36123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36120') );
SELECT lisaa_toimenpidekoodi(
    'Maksetut maanvuokrat ','36124',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36125',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36120') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöjen tulot ','36130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Perityt tilavuokrat asunnoista ','36131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36130') );
SELECT lisaa_toimenpidekoodi(
    'Perityt tilavuokrat toimitiloista ','36132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36130') );
SELECT lisaa_toimenpidekoodi(
    'Perityt tilavuokrat muista tiloista','36133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36130') );
SELECT lisaa_toimenpidekoodi(
    'Perityt maanvuokrat ','36134',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36130') );
SELECT lisaa_toimenpidekoodi(
    'Paikoitusmaksut','36135',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36130') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöjen muut tulot','36136',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36137',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36130') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistönhoito','36140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Siivous','36141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36140') );
SELECT lisaa_toimenpidekoodi(
    'Lämmitys','36142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36140') );
SELECT lisaa_toimenpidekoodi(
    'Sähkö','36143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36140') );
SELECT lisaa_toimenpidekoodi(
    'Vesi ja jätevesi ','36144',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36140') );
SELECT lisaa_toimenpidekoodi(
    'Muut hoitokulut ','36145',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36146',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36140') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistön kunnossapito ','36150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Kunnossapitokulut','36151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36150') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöverot','36160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöverot','36161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36160') );
SELECT lisaa_toimenpidekoodi(
    'Tuloverot','36162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36160') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöinvestoinnit','36170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Uudisrakennusinvestoinnit ','36171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36170') );
SELECT lisaa_toimenpidekoodi(
    'Muut kiinteistöinvestoinnit ','36172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','36173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36170') );
SELECT lisaa_toimenpidekoodi(
    'Muut','36180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36000') );
SELECT lisaa_toimenpidekoodi(
    'Muut','36181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='36180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='36180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='36180') );
SELECT lisaa_toimenpidekoodi(
    'Hallinto','37000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Johtaminen','37100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Johtaminen, jatkuva','37101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37100') );
SELECT lisaa_toimenpidekoodi(
    'Johtaminen, kehittäminen','37102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37100') );
SELECT lisaa_toimenpidekoodi(
    'Henkilöstöhallinto','37110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Henkilöstöhallinto, jatkuva','37111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37110') );
SELECT lisaa_toimenpidekoodi(
    'Henkilöstöhallinto, kehittäminen','37112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37110') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37110') );
SELECT lisaa_toimenpidekoodi(
    'Koulutus ja osaamisen kehittäminen','37120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Koulutus ja osaamisen kehittäminen, jatkuva','37121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37120') );
SELECT lisaa_toimenpidekoodi(
    'Koulutus ja osaamisen kehittäminen, kehittäminen','37122',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37120') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37123',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37120') );
SELECT lisaa_toimenpidekoodi(
    'Viranomaispäätösten tulot','37130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Viranomaispäätösten tulot, jatkuva','37131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37130') );
SELECT lisaa_toimenpidekoodi(
    'Viranomaispäätösten tulot, kehittäminen','37132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37130') );
SELECT lisaa_toimenpidekoodi(
    'Koulutus- ja seminaarimaksut','37140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Koulutus- ja seminaarimaksut, jatkuva','37141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37140') );
SELECT lisaa_toimenpidekoodi(
    'Koulutus- ja seminaarimaksut, kehittäminen','37142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37140') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöhallinto','37150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöhallinto, jatkuva','37151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37150') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteistöhallinto, kehittäminen','37152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37150') );
SELECT lisaa_toimenpidekoodi(
    'Hallintopalvelut','37160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Hallintopalvelut, jatkuva','37161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37160') );
SELECT lisaa_toimenpidekoodi(
    'Hallintopalvelut, kehittäminen','37162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37163',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37160') );
SELECT lisaa_toimenpidekoodi(
    'Taloushallinto','37170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Taloushallinto, jatkuva','37171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37170') );
SELECT lisaa_toimenpidekoodi(
    'Taloushallinto, kehittäminen','37172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37170') );
SELECT lisaa_toimenpidekoodi(
    'Asiakirja-, julkaisu- ja arkistointi','37180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Asiakirja-, julkaisu- ja arkistointi, jatkuva','37181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37180') );
SELECT lisaa_toimenpidekoodi(
    'Asiakirja-, julkaisu- ja arkistointi, kehittäminen','37182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37180') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37183',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37180') );
SELECT lisaa_toimenpidekoodi(
    'Viestintä','37190',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Viestintä, jatkuva','37191',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37190') );
SELECT lisaa_toimenpidekoodi(
    'Viestintä, kehittäminen','37192',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37190') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37193',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37190') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37190') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37190') );
SELECT lisaa_toimenpidekoodi(
    'Oikeuspalvelut','37200',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Oikeuspalvelut, jatkuva','37201',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37200') );
SELECT lisaa_toimenpidekoodi(
    'Oikeuspalvelut, kehittäminen','37202',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37200') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37203',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37200') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37200') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37200') );
SELECT lisaa_toimenpidekoodi(
    'Sisäinen tarkastus','37210',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Sisäinen tarkastus, jatkuva','37211',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37210') );
SELECT lisaa_toimenpidekoodi(
    'Sisäinen tarkastus, kehittäminen','37212',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37210') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37213',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37210') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37210') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37210') );
SELECT lisaa_toimenpidekoodi(
    'Muu hallinto','37220',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Muu hallinto, jatkuva','37221',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37220') );
SELECT lisaa_toimenpidekoodi(
    'Muu hallinto, kehittäminen','37222',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37220') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37223',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37220') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37220') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37220') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan ohjaus','37230',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan ohjaus, jatkuva','37231',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37230') );
SELECT lisaa_toimenpidekoodi(
    'Liikenteenhallinnan ohjaus, kehittäminen','37232',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37230') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37233',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37230') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37230') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37230') );
SELECT lisaa_toimenpidekoodi(
    'Talvimerenkulun ohjaus','37240',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Talvimerenkulun ohjaus, jatkuva','37241',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37240') );
SELECT lisaa_toimenpidekoodi(
    'Talvimerenkulun ohjaus, kehittäminen','37242',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37240') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37243',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37240') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37240') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37240') );
SELECT lisaa_toimenpidekoodi(
    'Kunnossapidon ohjaus','37250',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Kunnossapidon ohjaus, jatkuva','37251',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37250') );
SELECT lisaa_toimenpidekoodi(
    'Kunnossapidon ohjaus, kehittäminen','37252',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37250') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37253',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37250') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37250') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37250') );
SELECT lisaa_toimenpidekoodi(
    'Investointien ohjaus','37260',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Investointien ohjaus, jatkuva','37261',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37260') );
SELECT lisaa_toimenpidekoodi(
    'Investointien ohjaus, kehittäminen','37262',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37260') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','37263',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37260') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37260') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37260') );
SELECT lisaa_toimenpidekoodi(
    'Liikennejärjestelmätoimialan ohjaus','37270',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37000') );
SELECT lisaa_toimenpidekoodi(
    'Liikennejärjestelmätoimialan ohjaus, jatkuva','37271',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37270') );
SELECT lisaa_toimenpidekoodi(
    'Liikennejärjestelmätoimialan, kehittäminen','37272',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='37270') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='37270') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='37270') );
SELECT lisaa_toimenpidekoodi(
    'T&K','38000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'T&K','38100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='38000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='38000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='38000') );
SELECT lisaa_toimenpidekoodi(
    'Projektityö','38101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='38100') );
SELECT lisaa_toimenpidekoodi(
    'Osallistumisprojekti, kotimainen','38102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='38100') );
SELECT lisaa_toimenpidekoodi(
    'Osallistumisprojekti, kansainvälinen','38103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='38100') );
SELECT lisaa_toimenpidekoodi(
    'Hanke- ja projektihallinta','38104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='38100') );
SELECT lisaa_toimenpidekoodi(
    'Erittelemätön','38105',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='38100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','38106',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='38100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='38100') );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmät ja -infrastruktuuri','39000',1,  null,  null,  NULL ,  NULL ,  NULL );
SELECT lisaa_toimenpidekoodi(
    'Tietojärjestelmien ylläpito','39100',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Kiinteät kustannukset','39101',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39100') );
SELECT lisaa_toimenpidekoodi(
    'Toimintakunto','39102',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39100') );
SELECT lisaa_toimenpidekoodi(
    'Pienimuotoinen kehittäminen','39103',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39100') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','39104',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39100') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39100') );
SELECT lisaa_toimenpidekoodi(
    'ICT palvelusopimus','39110',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Käyttäjätuki','39111',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Työasemapalvelut','39112',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Mobiililaitepalvelut','39113',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Järjestelmäpalvelut','39114',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Kapasiteettipalvelut','39115',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikennepalvelut','39116',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Etäkäyttöpalvelut','39117',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöympäristön ylläpito','39118',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Laitteiden elinkaaripalvelut','39119',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39110') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39110') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','39120',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','39121',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39120') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39120') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39120') );
SELECT lisaa_toimenpidekoodi(
    'Tieto- ja puhelinliikenne   ','39130',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Tietoliikenne   ','39131',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39130') );
SELECT lisaa_toimenpidekoodi(
    'Puhelinliikenne','39132',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39130') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','39133',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39130') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39130') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39130') );
SELECT lisaa_toimenpidekoodi(
    'ICT integraatiopalvelut      ','39140',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Ylläpito','39141',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39140') );
SELECT lisaa_toimenpidekoodi(
    'Valvontapalvelu','39142',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39140') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','39143',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39140') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39140') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39140') );
SELECT lisaa_toimenpidekoodi(
    'ICT Infran elinkaaren hallinta      ','39150',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Versiopäivitys','39151',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39150') );
SELECT lisaa_toimenpidekoodi(
    'Kapasiteetin kasvattaminen','39152',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39150') );
SELECT lisaa_toimenpidekoodi(
    'Alustanvaihto','39153',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39150') );
SELECT lisaa_toimenpidekoodi(
    'Muut palvelut','39154',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39150') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','39155',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39150') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39150') );
SELECT lisaa_toimenpidekoodi(
    'ICT asiantuntijatyö  ','39160',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Asiantuntijatyö','39161',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39160') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','39162',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39160') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39160') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39160') );
SELECT lisaa_toimenpidekoodi(
    'ICT lisenssit ja leasingmaksut        ','39170',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Lisenssit ohjelmisto','39171',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39170') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut laitteet','39172',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39170') );
SELECT lisaa_toimenpidekoodi(
    'Leasingmaksut muut','39173',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39170') );
SELECT lisaa_toimenpidekoodi(
    'Laaja toimenpide','39174',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39170') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39170') );
SELECT lisaa_toimenpidekoodi(
    'Tiedonhallinnan kehittäminen','39180',2,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39000') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39000') );
SELECT lisaa_toimenpidekoodi(
    'Esiselvitys','39181',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39180') );
SELECT lisaa_toimenpidekoodi(
    'Vaatimusmäärittely','39182',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39180') );
SELECT lisaa_toimenpidekoodi(
    'Toteutus','39183',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39180') );
SELECT lisaa_toimenpidekoodi(
    'Käyttöönotto','39184',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39180') );
SELECT lisaa_toimenpidekoodi(
    'Erittelemättömät','39185',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39180') );
SELECT lisaa_toimenpidekoodi(
    'Projektinhallinta','39186',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39180') );
SELECT lisaa_toimenpidekoodi(
    'Laadun varmistus','39187',3,  null,  null,  (SELECT nimi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT koodi FROM toimenpidekoodi WHERE koodi='39180') ,  (SELECT taso FROM toimenpidekoodi WHERE koodi='39180') );
SELECT lisaa_toimenpidekoodi(
    'Asfaltointi', NULL, 4, 'NULL', NULL, (SELECT nimi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT koodi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT taso FROM toimenpidekoodi WHERE koodi='20106'));
SELECT lisaa_toimenpidekoodi(
    'Kuumennus', NULL, 4, 'NULL', NULL, (SELECT nimi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT koodi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT taso FROM toimenpidekoodi WHERE koodi='20106'));
SELECT lisaa_toimenpidekoodi(
    'Sekoitus tai stabilointi', NULL, 4, 'NULL', NULL, (SELECT nimi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT koodi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT taso FROM toimenpidekoodi WHERE koodi='20106'));
SELECT lisaa_toimenpidekoodi(
    'Turvalaite', NULL, 4, 'NULL', NULL, (SELECT nimi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT koodi FROM toimenpidekoodi WHERE koodi='20106'), (SELECT taso FROM toimenpidekoodi WHERE koodi='20106'));
SELECT lisaa_toimenpidekoodi(
    'Tiemerkintä', NULL, 4, 'NULL', NULL, (SELECT nimi FROM toimenpidekoodi WHERE koodi='20123'), (SELECT koodi FROM toimenpidekoodi WHERE koodi='20123'), (SELECT taso FROM toimenpidekoodi WHERE koodi='20123'));


UPDATE toimenpidekoodi SET suoritettavatehtava = 'asfaltointi' :: suoritettavatehtava WHERE nimi = 'Asfaltointi';
UPDATE toimenpidekoodi SET suoritettavatehtava = 'tiemerkinta' :: suoritettavatehtava WHERE nimi = 'Tiemerkintä';
UPDATE toimenpidekoodi SET suoritettavatehtava = 'kuumennus' :: suoritettavatehtava WHERE nimi = 'Kuumennus';
UPDATE toimenpidekoodi SET suoritettavatehtava = 'sekoitus tai stabilointi' :: suoritettavatehtava WHERE nimi = 'Sekoitus tai stabilointi';
UPDATE toimenpidekoodi SET suoritettavatehtava = 'turvalaite' :: suoritettavatehtava WHERE nimi = 'Turvalaite';


-- Luodaan 'Ei yksilöity' tehtävä kaikille 3. tason 'Laaja toimenpide' -toimenpiteiden alle HAR-2465
-- params (p_nimi        VARCHAR(255),
-- p_koodi       VARCHAR(16),
-- p_taso        INTEGER,
-- p_yksikko     VARCHAR(32),
-- p_tuotenumero INTEGER,
-- p_emo_nimi    VARCHAR(255),
-- p_emo_koodi   VARCHAR(16),
-- p_emo_taso    INTEGER)

SELECT lisaa_toimenpidekoodi('Ei yksilöity', NULL, 4, '-', NULL, t.nimi, t.koodi, t.taso)
FROM toimenpidekoodi t WHERE taso = 3 AND nimi = 'Laaja toimenpide' AND piilota IS NOT TRUE;


UPDATE toimenpidekoodi
SET hinnoittelu = ARRAY['kokonaishintainen'::hinnoittelutyyppi],
  api_seuranta = TRUE
WHERE taso = 4 AND
      emo in (SELECT id from toimenpidekoodi t3 WHERE t3.nimi = 'Laaja toimenpide')
      AND nimi = 'Ei yksilöity';

UPDATE toimenpidekoodi
SET tuotenumero = 201
WHERE koodi = '24100';