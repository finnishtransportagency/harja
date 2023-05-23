-- Luodaan Liikennevirasto
INSERT INTO organisaatio (tyyppi, nimi, lyhenne, ytunnus) VALUES ('liikennevirasto','Liikennevirasto','Livi', '1010547-1');

-- Luodaan apufunktiot testidatalle
\i testidata/apufunktiot.sql

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

\i testidata/rajoitusalueet.sql

-- Materiaalin käytöt
\i testidata/materiaalin_kaytto.sql

-- Toteumat
\i testidata/toteumat.sql

-- Varustetoteumat ulkoisista järjestelmistä
\i testidata/varustetoteumat_ulkoiset.sql

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

-- Päällystyskohteet & -ilmoitukset (POT1 ja POT2), materiaalikirjasto
\i testidata/yllapito/paallystys.sql
\i testidata/yllapito/paallystysmassat.sql
\i testidata/yllapito/pot2.sql

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

SELECT paivita_kaikki_sopimuksen_kaytetty_materiaali();
select paivita_materiaalin_kaytto_hoitoluokittain_aikavalille('0001-01-01'::DATE,'2100-12-31'::DATE);
SELECT paivita_raportti_toteutuneet_materiaalit();
SELECT paivita_raportti_pohjavesialueiden_suolatoteumat();
SELECT paivita_raportti_toteuma_maarat();

-- Siirrä kaikki toteumat analytiikka_toteumat tauluun
-- Siirrertään uudet toteumat
INSERT INTO analytiikka_toteumat (
    SELECT t.id                                                                       as toteuma_tunniste_id,
           t.sopimus                                                                  as toteuma_sopimus_id,
           t.alkanut                                                                  as toteuma_alkanut,
           t.paattynyt                                                                as toteuma_paattynyt,
           u.urakkanro                                                                AS toteuma_alueurakkanumero,
           t.suorittajan_ytunnus                                                      as toteuma_suorittaja_ytunnus,
           t.suorittajan_nimi                                                         as toteuma_suorittaja_nimi,
           t.tyyppi::toteumatyyppi                                                    as toteuma_toteumatyyppi, -- "yksikkohintainen","kokonaishintainen","akillinen-hoitotyo","lisatyo", "muutostyo","vahinkojen-korjaukset"
           t.lisatieto                                                                as toteuma_lisatieto,
           json_agg(row_to_json(row (tt.id, tt.maara, tkoodi.yksikko, tt.lisatieto))) AS toteumatehtavat,
           json_agg(row_to_json(row (mk.nimi, tm.maara, mk.yksikko)))                 AS toteumamateriaalit,
           t.tr_numero                                                                as toteuma_tiesijainti_numero,
           t.tr_alkuosa                                                               as toteuma_tiesijainti_aosa,
           t.tr_alkuetaisyys                                                          as toteuma_tiesijainti_aet,
           t.tr_loppuosa                                                              as toteuma_tiesijainti_losa,
           t.tr_loppuetaisyys                                                         as toteuma_tiesijainti_let,
           t.luotu                                                                    as toteuma_muutostiedot_luotu,
           t.luoja                                                                    as toteuma_muutostiedot_luoja,
           t.muokattu                                                                 as toteuma_muutostiedot_muokattu,
           t.muokkaaja                                                                as toteuma_muutostiedot_muokkaaja,
           t.tyokonetyyppi                                                            as tyokone_tyokonetyyppi,
           t.tyokonetunniste                                                          as tyokone_tunnus,
           t.urakka                                                                   as urakkaid,
           t.poistettu                                                                as poistettu
    FROM toteuma t
             LEFT JOIN toteuma_tehtava tt ON tt.toteuma = t.id
             LEFT JOIN tehtava tkoodi ON tkoodi.id = tt.toimenpidekoodi
             LEFT JOIN toteuma_materiaali tm ON tm.toteuma = t.id
             LEFT JOIN materiaalikoodi mk ON tm.materiaalikoodi = mk.id
             JOIN urakka u on t.urakka = u.id
    WHERE (t.alkanut BETWEEN '2000-01-01T00:00:00' AND '2100-01-01T00:00:00')
    GROUP BY t.id, t.alkanut, u.id
    ORDER BY t.alkanut ASC
)
ON CONFLICT DO NOTHING;
