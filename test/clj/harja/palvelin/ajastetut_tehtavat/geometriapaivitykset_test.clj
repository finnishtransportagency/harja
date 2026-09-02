(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.ava :as alk]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.valaistusurakat :as valaistusurakoiden-tuonti]
            [harja.kyselyt.geometriapaivitykset :as gp-kyselyt]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.testi :refer [i u jarjestelma tietokantakomponentti-fixture]]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet :as pohjavesialueen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat :as siltojen-tuonti]
            [clojure.java.io :as io]
            [org.httpkit.fake :refer [with-fake-http]])
  (:import (org.apache.commons.io IOUtils)))

(use-fixtures :once tietokantakomponentti-fixture)

(def kayttaja "jvh")

(defn aja-tieverkon-paivitys
  "REPL-testiajofunktio"
  []
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (alk/kaynnista-paivitys
      integraatioloki
      testitietokanta
      "tieverkko"
      "http://185.26.50.104/Tieosoiteverkko.zip"
      "/Users/mikkoro/Desktop/Tieverkko-testi/"
      "file:///Users/mikkoro/Desktop/Tieverkko-testi/Tieosoiteverkko.shp"
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/mikkoro/Desktop/Tieverkko-testi/Tieosoiteverkko.shp"))
      nil
      nil)))

(defn aja-pohjavesialueen-paivitys
  "REPL-testiajofunktio"
  []
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (alk/kaynnista-paivitys
      integraatioloki
      testitietokanta
      "pohjavesialue"
      "http://185.26.50.104/Pohjavesialue.zip"
      "/Users/jarihan/Desktop/Pohjavesialue-testi/"
      "file:///Users/jarihan/Desktop/Pohjavesialue-testi/Pohjavesialue.shp"
      (fn []
        (pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan
          testitietokanta
          "file:///Users/jarihan/Desktop/Pohjavesialue-testi/Pohjavesialue.shp"))
      nil
      nil)))

(defn aja-siltojen-paivitys
  "REPL-testiajofunktio"
  []
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (alk/kaynnista-paivitys
      integraatioloki
      testitietokanta
      "sillat"
      "http://185.26.50.104/Sillat.zip"
      "/Users/jarihan/Desktop/Sillat-testi/"
      "file:///Users/jarihan/Desktop/Pohjavesialue-testi/Sillat.shp"
      (fn []
        (siltojen-tuonti/vie-sillat-kantaan
          testitietokanta
          "file:///Users/jarihan/Desktop/Pohjavesialue-testi/Sillat.shp"))
      nil
      nil)))

(defn aja-soratien-hoitoluokkien-paivitys
  "REPL-testiajofunktio"
  []
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (alk/kaynnista-paivitys
      integraatioloki
      testitietokanta
      "urakat"
      "http://185.26.50.104/tl132.tgz"
      "/Users/mikkoro/Desktop/Soratiehoitoluokat-testi/"
      "file:///Users/mikkoro/Desktop/Soratiehoitoluokat-testi/Sorateiden-hoitoluokat.shp"
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/mikkoro/Desktop/Soratiehoitoluokat-testi/Sorateiden-hoitoluokat.shp"))
      nil
      nil)))


(defn aja-turvalaitteiden-paivitys
  "REPL-testiajofunktio"
  []
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)]
    (component/start integraatioloki)
    (alk/kaynnista-paivitys
      integraatioloki
      testitietokanta
      "turvalaitteet"
      "http://185.26.50.104/Turvalaitteet.tgz"
      "/Users/maaritla/Downloads/Turvalaite-testi/"
      "file:///Users/maaritla/Downloads/Turvalaite-testi/Turvalaitteet.shp"
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/maaritla/Downloads/Turvalaite-testi/Turvalaitteet.shp"))
      nil
      nil)))

(defn aja-valaistusurakoiden-paivitys
  "REPL-testiajofunktio - Tämän saa toimimaan, kunhan valaistusurakoiden tiedot
  on ensin haettu 'valaistusurakoiden_osoite' muuttujaan tallennetusta urlista esim selaimella.
  Pura saapuva zip ja kutsu sitten tätä."
  []
  (valaistusurakoiden-tuonti/vie-urakat-kantaan
    (:db jarjestelma)
    ;(:db harja.palvelin.main/harja-jarjestelma) Jos ajat tätä funktiota REPListä, niin anna kahva tietokantaan näin ja kommentoi pois tuo ylempi
    ;; Anna <kayttajatunnus> kohtaan ja muuallekin oikea polku tiedostoon
    "file:///Users/<kayttajatunnus>/Downloads/palvelusopimus/palvelusopimusLine.shp"))


(deftest testaa-pitaako-paivittaa
         (let [testitietokanta (:db jarjestelma)]
              ;; Poista mahdolliset vanhat testidatat
              (u (str "DELETE FROM geometriapaivitys WHERE nimi IN ('palvelimelta-paivitetaan', 'paikallinen-null-paivitetaan', 'palvelimelta-ei-paiviteta', 'paikallinen-ei-paiviteta', 'palvelimelta-ei-kaytossa', 'paikallinen-ei-kaytossa')"))
              (i (str "INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, paikallinen) values ('palvelimelta-paivitetaan', '2022-09-05 07:50:24.479550', '2022-10-05 08:55:24.479550', false)"))
              (i (str "INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, paikallinen) values ('paikallinen-null-paivitetaan', '2022-09-05 07:50:24.479550', null, true)"))
              (i (str "INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, paikallinen) values ('palvelimelta-ei-paiviteta', '2022-09-05 07:50:24.479550', '2034-11-05 08:55:24.479550', false)"))
              (i (str "INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, paikallinen) values ('paikallinen-ei-paiviteta', '2022-09-05 07:50:24.479550', '2034-11-05 08:55:24.479550', true)"))
              (i (str "INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, paikallinen, kaytossa) values ('palvelimelta-ei-kaytossa', '2020-09-05 07:50:24.479550', '2021-11-05 08:55:24.479550', false, false)"))
              (i (str "INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, paikallinen, kaytossa) values ('paikallinen-ei-kaytossa', '2020-09-05 07:50:24.479550', '2021-11-05 08:55:24.479550', true, false)"))
              (is (= :palvelimelta (gp-kyselyt/pitaako-paivittaa? testitietokanta "palvelimelta-paivitetaan")) "Geometria-aineisto, jonka seuraava päivitysajankohta on mennyt, pitää päivittää.")
              (is (= :paikallinen (gp-kyselyt/pitaako-paivittaa? testitietokanta "paikallinen-null-paivitetaan")) "Geometria-aineisto, jonka seuraavaa päivitysajankohtaa ei ole määritelty, pitää päivittää.")
              (is (= :ei-paivitystarvetta (gp-kyselyt/pitaako-paivittaa? testitietokanta "palvelimelta-ei-paiviteta")) "Geometria-aineistoa, jonka seuraava päivitysajankohta on vasta tulossa, ei päivitetä.")
              (is (= :ei-paivitystarvetta (gp-kyselyt/pitaako-paivittaa? testitietokanta "paikallinen-ei-paiviteta")) "Geometria-aineistoa, jonka seuraava päivitysajankohta on vasta tulossa, ei päivitetä.")
              (is (= :ei-kaytossa (gp-kyselyt/pitaako-paivittaa? testitietokanta "palvelimelta-ei-kaytossa")) "Jos geometria-aineiston tiedot puuttuvat tietokannasta, tehdään päivitys aineistopalvelimelta.")
              (is (= :ei-kaytossa (gp-kyselyt/pitaako-paivittaa? testitietokanta "paikallinen-ei-kaytossa")) "Jos geometria-aineiston tiedot puuttuvat tietokannasta, tehdään päivitys aineistopalvelimelta.")
              (is (= :palvelimelta (gp-kyselyt/pitaako-paivittaa? testitietokanta "uusi")) "Jos geometria-aineiston tiedot puuttuvat tietokannasta, tehdään päivitys aineistopalvelimelta.")))

(deftest testaa-tiedoston-lataus-ava-alustalla
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        fake-tiedosto-url "http://www.example.com/test_file.zip"
        kohdetiedosto "test/resurssit/download_test.zip"
        fake-vastaus {:status 200 :body (IOUtils/toByteArray (io/input-stream "test/resurssit/arkistot/test_zip.zip"))}]
    (component/start integraatioloki)
    (with-fake-http
      [{:url fake-tiedosto-url :method :get} fake-vastaus]
      (alk/hae-tiedosto integraatioloki testitietokanta "tieverkko-haku" fake-tiedosto-url kohdetiedosto)
      (is (true? (.exists (clojure.java.io/file kohdetiedosto))))
      (clojure.java.io/delete-file kohdetiedosto))))

(defn- valmistele-geometriapaivitys!
  [paivitystunnus]
  (u (format "DELETE FROM geometriapaivitys WHERE nimi = '%s'" paivitystunnus))
  (i (format (str "INSERT INTO geometriapaivitys (nimi, viimeisin_paivitys, seuraava_paivitys, paikallinen) "
                 "VALUES ('%s', '2022-01-01 00:00:00', '2022-01-02 00:00:00', true)")
            paivitystunnus)))

(defn- kaynnista-paivitys-stubattuna!
  [integraatioloki testitietokanta paivitystunnus tiedosto-url kohdetiedosto shapefile-polku paivitys-fn]
  (with-redefs [alk/aja-paivitys (fn [_ _ _ _ _ _ _ paivitys _ _] (paivitys))
                lukko/yrita-ajaa-lukon-kanssa (fn [_ _ f] (f) true)]
    (alk/kaynnista-paivitys
      integraatioloki
      testitietokanta
      paivitystunnus
      tiedosto-url
      kohdetiedosto
      shapefile-polku
      paivitys-fn
      nil
      nil)))

(defn- hae-viimeisin-lahde
  [testitietokanta paivitystunnus]
  (-> (gp-kyselyt/hae-paivitys testitietokanta paivitystunnus)
      first
      :viimeisin_lahde))

(deftest testaa-viimeisin-lahde-paivittyy-onnistuessa
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        paivitystunnus "testi-onnistuminen"
        shapefile-polku "file:///test/polku/testi.shp"
        tiedosto-url "http://example.com/testi.zip"
        paivitys-fn (fn [] :onnistui)]
    (component/start integraatioloki)
    (valmistele-geometriapaivitys! paivitystunnus)
    (kaynnista-paivitys-stubattuna!
      integraatioloki testitietokanta paivitystunnus tiedosto-url "/tmp/testi/kohde.zip" shapefile-polku paivitys-fn)
    (is (= shapefile-polku (hae-viimeisin-lahde testitietokanta paivitystunnus))
        "Viimeisin lähde pitää päivittyä shapefile-polkuun onnistumisen yhteydessä")))

(deftest testaa-viimeisin-lahde-paivittyy-epaonnistuessa
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        paivitystunnus "testi-epaonnistuminen"
        shapefile-polku "file:///test/polku/testi-error.shp"
        tiedosto-url "http://example.com/testi-error.zip"
        paivitys-fn (fn [] (throw (Exception. "Simuloitu virhe")))]
    (component/start integraatioloki)
    (valmistele-geometriapaivitys! paivitystunnus)
    (kaynnista-paivitys-stubattuna!
      integraatioloki testitietokanta paivitystunnus tiedosto-url "/tmp/testi/kohde-error.zip" shapefile-polku paivitys-fn)
    (is (= shapefile-polku (hae-viimeisin-lahde testitietokanta paivitystunnus))
        "Viimeisin lähde pitää päivittyä shapefile-polkuun myös epäonnistumisen yhteydessä")))

(deftest testaa-viimeisin-lahde-fallback-urliin
  (let [testitietokanta (:db jarjestelma)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        paivitystunnus "testi-fallback"
        tiedosto-url "http://example.com/testi-fallback.zip"
        paivitys-fn (fn [] :onnistui)]
    (component/start integraatioloki)
    (valmistele-geometriapaivitys! paivitystunnus)
    (kaynnista-paivitys-stubattuna!
      integraatioloki testitietokanta paivitystunnus tiedosto-url "/tmp/testi/kohde-fallback.zip" nil paivitys-fn)
    (is (= tiedosto-url (hae-viimeisin-lahde testitietokanta paivitystunnus))
        "Viimeisin lähde pitää fallbackata tiedosto-urliin jos shapefile puuttuu")))
