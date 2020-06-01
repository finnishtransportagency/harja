-- Luodaan Liikennevirasto
INSERT INTO organisaatio (tyyppi, nimi, lyhenne, ytunnus) VALUES ('liikennevirasto','Liikennevirasto','Livi', '1010547-1');

-- Luodaan hallintayksikot (ELY-keskukset)
\i testidata/elyt.sql

-- Luodaan urakoitsijat
\i testidata/urakoitsijat.sql

-- Testikäyttäjiä
\i testidata/kayttajat.sql

-- Ladataan alueurakoiden geometriat
\i testidata/alueurakat.sql

-- Luodaan hoidon alueurakoita ja ylläpitourakoita
\i testidata/urakat.sql

-- Luodaan sopimuksia urakoille, kaikilla urakoilla on oltava ainakin yksi sopimus
\i testidata/sopimukset.sql

-- Vesiväylien ja kanavien urakat
\i testidata/vesivaylat/vesivaylien_urakat.sql
\i testidata/kanavat/kanavien_urakat.sql

-- Liitä käyttäjät urakoihin
\i testidata/kayttajaroolit.sql

-- Luodaan sanktiotyypit
\i testidata/sanktiotyypit.sql

-- Luodaan yhteyshenkilöpooliin henkilöitä
\i testidata/yhteyshenkilot.sql

-- Luodaan hankkeet
\i testidata/hankkeet.sql

-- Lisätään ELY numerot hallintayksiköille

UPDATE organisaatio SET elynumero=1 WHERE lyhenne='UUD';
UPDATE organisaatio SET elynumero=2 WHERE lyhenne='VAR';
UPDATE organisaatio SET elynumero=3 WHERE lyhenne='KAS';
UPDATE organisaatio SET elynumero=4 WHERE lyhenne='PIR';
UPDATE organisaatio SET elynumero=8 WHERE lyhenne='POS';
UPDATE organisaatio SET elynumero=9 WHERE lyhenne='KES';
UPDATE organisaatio SET elynumero=10 WHERE lyhenne='EPO';
UPDATE organisaatio SET elynumero=12 WHERE lyhenne='POP';
UPDATE organisaatio SET elynumero=14 WHERE lyhenne='LAP';

-- Lisätään indeksejä
\i testidata/indeksit.sql

-- Suunnitellut työt
\i testidata/suunnitellut_tyot.sql
\i testidata/tehtavamaarat.sql

\i testidata/pohjavesialueet.sql

SELECT paivita_pohjavesialue_kooste();
SELECT paivita_pohjavesialueet();

-- Materiaalin käytöt
\i testidata/materiaalin_kaytto.sql

-- Toteumat
\i testidata/toteumat.sql

-- Kustannussuunnitelma
\i testidata/kustannussuunnittelu.sql

-- Tieverkko
\i testidata/tierekisteri.sql

-- Sillat
\i testidata/sillat.sql

-- Maksuerät
\i testidata/maksuerat.sql

-- Erilliskustannukset
\i testidata/erilliskustannukset.sql

-- Muutoshintaiset työt
\i testidata/muutoshintaiset_tyot.sql

-- Päällystyskohteet & -ilmoitukset
\i testidata/yllapito/paallystys.sql

-- Ylläpidon toteumat
\i testidata/yllapito/yllapito_toteumat.sql

-- Päivitä päällystys & paikkausurakoiden geometriat kohdeluetteloiden perusteella
SELECT paivita_paallystys_ja_paikkausurakoiden_geometriat();

-- Ilmoitukset ja kuittaukset
\i testidata/ilmoitukset.sql

-- Turvallisuuspoikkeamat
\i testidata/turvallisuuspoikkeamat.sql

-- Laatupoikkeamat

\i testidata/laatupoikkeamat.sql

-- Sanktiot
\i testidata/sanktiot.sql

-- Tarkastukset
\i testidata/tarkastukset.sql
\i testidata/tarkastusajot.sql

-- Tyokoneseurannan havainnot
\i testidata/tyokonehavainnot.sql

-- hieman hoitoluokkadataa testausta varten
INSERT INTO hoitoluokka (ajorata, aosa, tie, piirinro, let, losa, aet, osa, hoitoluokka, geometria, tietolajitunniste) VALUES (0,801,70816,12,1710,801,0,801,7,ST_GeomFromText('MULTILINESTRING((429451.2124 7199520.6102,429449.505 7199521.6673,429440.5079 7199523.6547,429425.8351 7199523.5332,429414.6011 7199519.5185,429408.1148 7199516.9618,429402.1896 7199514.6903,429391.0467 7199514.8601,429378.936 7199515.034,429367.9027 7199511.4445,429352.9893 7199509.8717,429340.7607 7199509.7674,429325.0809 7199509.6519,429297.0533 7199509.4357,429245.0896 7199508.9075,429203.4416 7199510.4529,429185.9626 7199511.0908,429175.1097 7199511.46,429164.186 7199511.8495,429163.9722 7199511.8543,429127.7205 7199513.124,429097.0326 7199516.1685,429067.7311 7199519.7788,429028.7161 7199523.937,429002.5032 7199526.988,428986.7864 7199529.3238,428972.0898 7199531.8776,428959.8278 7199535.383,428958.5837 7199535.7868,428935.3939 7199544.4855,428935.2753 7199544.526,428916.3783 7199549.9087,428894.5153 7199554.0353,428873.7905 7199561.3118,428862.4296 7199566.3104,428848.2196 7199572.3112,428803.3681 7199591.2262,428767.5476 7199605.5726,428756.8978 7199609.7879,428733.0934 7199619.6432,428710.3663 7199629.7534,428703.1707 7199632.7658,428690.4651 7199638.2455,428667.7141 7199649.3473,428655.186 7199655.9628,428646.3949 7199660.2657,428641.724 7199662.6568,428614.7053 7199677.4118,428588.9015 7199691.2222,428562.1818 7199705.5031,428550.6238 7199710.5445,428540.1979 7199714.1393,428533.4514 7199717.3279,428521.3205 7199724.9439,428505.6115 7199732.8696,428481.9851 7199739.4898,428465.1564 7199744.695,428448.6528 7199752.5844,428428.1823 7199763.7452,428410.7128 7199772.0312,428405.4195 7199775.2603,428399.2614 7199778.1737,428397.2174 7199778.9515,428395.9721 7199781.9532,428393.0872 7199784.3771,428388.8629 7199787.8796,428384.3772 7199791.0521,428380.144 7199794.1436,428376.2853 7199797.0017,428372.3219 7199799.7878,428371.4012 7199800.4268,428368.256 7199802.6143,428364.2134 7199805.383,428359.5495 7199808.7467,428354.9167 7199812.0723,428351.5375 7199814.3962,428349.8128 7199815.5772,428344.2205 7199818.2715,428336.5372 7199818.7545,428328.7401 7199819.147,428321.1527 7199819.6651,428313.5891 7199820.8556,428306.2119 7199822.8829,428298.9807 7199825.3551,428292.1841 7199828.078,428285.6526 7199830.2905,428269.2754 7199839.1119,428258.1283 7199847.3407,428247.9163 7199857.4908,428240.2336 7199869.5991,428231.3359 7199890.2215,428224.6186 7199899.5777,428216.7191 7199908.906,428211.1292 7199916.628,428190.7778 7199924.0874,428179.4979 7199929.741,428165.9323 7199935.3369,428155.9323 7199941.427,428137.8815 7199955.1481,428121.6299 7199971.7797,428113.4761 7199979.4142,428099.1654 7199990.6541,428088.3816 7200000.0896,428078.069 7200006.6729,428060.0235 7200016.3019,428051.5398 7200021.4403,428041.4784 7200029.9056,428033.5629 7200039.833,428026.683 7200047.979,428019.4779 7200057.1232,428013.3812 7200069.4739,428005.1958 7200085.6505,428000.2825 7200094.7583,427991.0495 7200112.5858,427985.4578 7200128.4193,427984.0392 7200133.425,427967.0366 7200140.8427,427958.7166 7200147.4718,427953.0702 7200154.7847))'), 'soratie'::hoitoluokan_tietolajitunniste);

-- Lämpötilat
\i testidata/lampotilat.sql

-- Välitavoitteet
\i testidata/valitavoitteet.sql

-- Refreshaa Viewit. Nämä kannattanee pitää viimeisenä just in case

SELECT paivita_urakoiden_alueet();
SELECT paivita_pohjavesialueet();

-- Luodaan testidataa laskutusyhteenvetoraporttia varten
\i testidata/laskutusyhteenveto.sql
\i testidata/laskutusyhteenveto_kajaani.sql
\i testidata/laskutusyhteenveto_vantaa.sql
\i testidata/laskutusyhteenveto_espoo.sql

-- Testidata MHU laskutusta varten
\i testidata/laskut.sql

-- Suolabonustestausta varten
\i testidata/vantaa_suolabonusta_varten.sql

\i testidata/tietyomaat.sql

-- Tietyöilmoitukset
\i testidata/tietyoilmoitukset.sql

-- Hoitoluokittaiset materiaalin käytöt (cachetaulut)
\i testidata/hoitoluokittaiset_materiaalit.sql

SELECT paivita_kaikki_sopimuksen_kaytetty_materiaali();

-- Vesiväylät & kanavat

\i testidata/vesivaylat/vaylat_ja_turvalaitteet.sql
\i testidata/vesivaylat/vesivaylien_turvalaiteryhmat.sql
\i testidata/vesivaylat/vesivaylien_turvalaitteet.sql
\i testidata/vesivaylat/vesivaylien_toimenpiteet.sql
\i testidata/vesivaylat/vesivaylien_materiaalit.sql
\i testidata/vesivaylat/kalusto.sql

\i testidata/kanavat/kohteet.sql
\i testidata/kanavat/kanavien_toimenpiteet.sql
\i testidata/kanavat/liikennetapahtumat.sql
\i testidata/kanavat/hairiotilanteet.sql
\i testidata/kanavat/kanavien_laskutusyhteenveto.sql
\i testidata/kanavat/kanavien_materiaalit.sql
\i testidata/kanavat/kanavien_maksuerat.sql

-- Tieluvat
\i testidata/tieluvat.sql

-- Paikkaukset
\i testidata/paikkaukset.sql

SELECT paivita_raportti_cachet();