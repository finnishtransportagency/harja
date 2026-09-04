-- Luodaan Liikennevirasto
INSERT INTO organisaatio (tyyppi, nimi, lyhenne, ytunnus)
SELECT 'liikennevirasto', 'Liikennevirasto', 'Livi', '1010547-1'
WHERE NOT EXISTS (
    SELECT 1
      FROM organisaatio
     WHERE ytunnus = '1010547-1'
);

-- Tuotoannosta otettu data dumppi jotta tehtävien tietomallin testidata täsmää
-- Nov 20 2025, Marraskuun klooni (data 1.11.2025 ->)
\i testidata/__Toimenpide_Kopio_01.sql
\i testidata/__Materiaaliluokka_Kopio_01.sql
\i testidata/__Tehtavaryhmaotsikko_Kopio_01.sql
\i testidata/__Tehtavaryhma_Kopio_01.sql
\i testidata/__Materiaalikoodi_Kopio_01.sql
\i testidata/__Tehtava_Kopio_01.sql
\i testidata/__Rahavaraus_Kopio_01.sql

-- Synkkaa sequenssit, koska ladattiin datat prodin id:illä
SELECT setval(
  pg_get_serial_sequence('toimenpide', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM toimenpide)
);

SELECT setval(
  pg_get_serial_sequence('tehtava', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM tehtava)
);

SELECT setval(
  pg_get_serial_sequence('tehtavaryhma', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM tehtavaryhma)
);

SELECT setval(
  pg_get_serial_sequence('tehtavaryhmaotsikko', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM tehtavaryhmaotsikko)
);

SELECT setval(
  pg_get_serial_sequence('materiaaliluokka', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM materiaaliluokka)
);

SELECT setval(
  pg_get_serial_sequence('materiaalikoodi', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM materiaalikoodi)
);

SELECT setval(
  pg_get_serial_sequence('rahavaraus', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM rahavaraus)
);

SELECT setval(
  pg_get_serial_sequence('rahavaraus_tehtava', 'id'),
  (SELECT COALESCE(MAX(id), 1) FROM rahavaraus_tehtava)
);

-- Luodaan apufunktiot testidatalle
\i testidata/apufunktiot.sql

-- Luodaan hallintayksikot (ELY-keskukset)
\i testidata/elyt.sql
-- Lisätään elinvoimakeskuksille geometriat
\i testidata/elinvoimakeskukset.sql

-- Luodaan urakoitsijat
\i testidata/urakoitsijat.sql

-- Testikäyttäjiä
\i testidata/kayttajat.sql

-- Ladataan alueurakoiden geometriat
\i testidata/alueurakat.sql

-- Luodaan hoidon alueurakoita ja ylläpitourakoita
\i testidata/urakat.sql
\i testidata/urakat2.sql
-- Luodaan päällystysurakoita
\i testidata/paallystysurakat.sql

-- Luodaan sopimuksia urakoille, kaikilla urakoilla on oltava ainakin yksi sopimus
\i testidata/sopimukset.sql

-- Vesiväylien ja kanavien urakat
\i testidata/vesivaylat/vesivaylien_urakat.sql
\i testidata/kanavat/kanavien_urakat.sql

-- Liitä käyttäjät urakoihin
\i testidata/kayttajaroolit.sql

-- Luodaan yhteyshenkilöpooliin henkilöitä
\i testidata/yhteyshenkilot.sql

-- Luodaan hankkeet
\i testidata/hankkeet.sql

-- Tieverkko
\i testidata/tierekisteri.sql

-- Lisätään ELY numerot hallintayksiköille

UPDATE organisaatio SET elynumero=1 WHERE lyhenne='UUD' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=2 WHERE lyhenne='VAR' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=3 WHERE lyhenne='KAS' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=4 WHERE lyhenne='PIR' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=8 WHERE lyhenne='POS' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=9 WHERE lyhenne='KES' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=10 WHERE lyhenne='EPO' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=12 WHERE lyhenne='POP' and tyyppi = 'hallintayksikko';
UPDATE organisaatio SET elynumero=14 WHERE lyhenne='LAP' and tyyppi = 'hallintayksikko';

-- Lisätään indeksejä
\i testidata/indeksit.sql

-- Suunnitellut työt
\i testidata/suunnitellut_tyot.sql
\i testidata/tehtavamaarat.sql

\i testidata/pohjavesialueet.sql

SELECT paivita_pohjavesialue_kooste();
SELECT paivita_pohjavesialueet();

\i testidata/rajoitusalueet.sql

\i testidata/hoitoluokat.sql

-- Tehtävämigraatiot testidataan
\i testidata/tehtavamuutokset.sql

-- Materiaalin käytöt
\i testidata/materiaalin_kaytto.sql

-- Toteumat
\i testidata/toteumat.sql

-- Kustannussuunnitelma
\i testidata/kustannussuunnittelu.sql

-- Sillat
\i testidata/sillat.sql

-- Maksuerät
\i testidata/maksuerat.sql

-- Erilliskustannukset
\i testidata/erilliskustannukset.sql

-- Muutoshintaiset työt
\i testidata/muutoshintaiset_tyot.sql

-- Päällystyskohteet & -ilmoitukset (POT1 ja POT2), materiaalikirjasto
\i testidata/yllapito/paallystys.sql
\i testidata/yllapito/paallystysmassat.sql
\i testidata/yllapito/pot2.sql

-- Reikäpaikkaukset
\i testidata/yllapito/reikapaikkaukset.sql

-- MPU kustannukset
\i testidata/yllapito/paikkauskustannukset.sql

-- Ylläpidon toteumat
\i testidata/yllapito/yllapito_toteumat.sql

-- Tiemerkinnät
\i testidata/yllapito/tiemerkinta.sql

-- Päivitä päällystys & paikkausurakoiden geometriat kohdeluetteloiden perusteella
SELECT paivita_paallystys_ja_paikkausurakoiden_geometriat();

\i testidata/palauteluokitukset.sql

-- Ilmoitukset ja kuittaukset
\i testidata/ilmoitukset.sql

-- Turvallisuuspoikkeamat
\i testidata/turvallisuuspoikkeamat.sql

--== Laadunseuranta ==--
\i testidata/laadunseuranta/talvihoitoreitit_testidata.sql
-- Laatupoikkeamat

\i testidata/laatupoikkeamat.sql

-- Sanktiot
\i testidata/sanktiot.sql
\i testidata/sanktioraportti_testidata.sql

-- Tarkastukset
\i testidata/tarkastukset.sql
\i testidata/tarkastusajot.sql

-- Tieturvallisuusverkko geometriat
\i testidata/tieturvallisuusverkko.sql

-- Tyokoneseurannan havainnot
\i testidata/tyokonehavainnot.sql

-- Lämpötilat
\i testidata/lampotilat.sql

-- Lupaukset
\i testidata/lupaus_testidata.sql

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
\i testidata/laskutusyhteenveto_mhu.sql

-- Testidata MHU laskutusta varten
\i testidata/laskut.sql

-- Suolabonustestausta varten
\i testidata/vantaa_suolabonusta_varten.sql

\i testidata/tietyomaat.sql

-- Tietyöilmoitukset
\i testidata/tietyoilmoitukset.sql

-- Hoitoluokittaiset materiaalin käytöt (cachetaulut)
\i testidata/hoitoluokittaiset_materiaalit.sql

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

-- Toteutuneet kustannukset
\i testidata/toteutuneet_kustannukset.sql

-- Välikatselmusten tiedot
\i testidata/kulut/valikatselmus.sql

-- Tilaajan-konsultti organisaatio
\i testidata/tilaajan-konsultit.sql

-- Suunnitellut kulut Kittilän urakan 1. vuodelle vuoden päätöstä varten.
\i testidata/vuodenpaatos.sql

-- MHU muutokset
\i testidata/muutos_testidata.sql

\i testidata/analytiikka-paallystyskohteet.sql

-- Siirretään urakat elinvoimakeskuksiin - analytiikan päällystyskohteissa vielä lisätään urakoita, niin tämän on oltava täällä lopussa
\i testidata/elinvoimakeskusten_urakat.sql

-- Populoidaan rahavaraukset
SELECT populoi_rahavaraus_idt();

-- Lisätään urakkakohtaiset rahavaraukset
\i testidata/rahavaraukset.sql

SELECT paivita_kaikki_sopimuksen_kaytetty_materiaali();
select paivita_materiaalin_kaytto_hoitoluokittain_aikavalille('0001-01-01'::DATE,'2100-12-31'::DATE);
SELECT paivita_raportti_toteutuneet_materiaalit();
SELECT paivita_raportti_pohjavesialueiden_suolatoteumat();
SELECT paivita_raportti_toteuma_maarat();

\i testidata/analytiikka_toteumat.sql


-- Päivitetään toimenkuvat -25 urakoille
SELECT lisaa_toimenkuvat_urakalle('2025-10-01'::DATE);

-- Tarjoukset
\i testidata/tarjous.sql
