(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.ava :as alk]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.ajastetut-tehtavat.geometriapaivitykset :as geometriapaivitykset]
            [harja.kyselyt.geometriapaivitykset :as gp-kyselyt]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet :as pohjavesialueen-tuonti]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat :as siltojen-tuonti]
            [clj-time.coerce :as time-coerce]
            [clojure.java.io :as io])
  (:use org.httpkit.fake)
  (:import (java.util Date)
           (org.apache.commons.io IOUtils)))

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
      (fn []
        (pohjavesialueen-tuonti/vie-pohjavesialueet-kantaan
          testitietokanta
          "file:///Users/jarihan/Desktop/Pohjavesialue-testi/Pohjavesialue.shp")))))

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
      (fn []
        (siltojen-tuonti/vie-sillat-kantaan
          testitietokanta
          "file:///Users/jarihan/Desktop/Pohjavesialue-testi/Sillat.shp")))))

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
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/mikkoro/Desktop/Soratiehoitoluokat-testi/Sorateiden-hoitoluokat.shp"))
      ""
      "")))


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
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/maaritla/Downloads/Turvalaite-testi/Turvalaitteet.shp")))))


(deftest testaa-pitaako-paivittaa
         (let [testitietokanta (:db jarjestelma)]
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
              (is (= nil (gp-kyselyt/pitaako-paivittaa? testitietokanta "palvelimelta-ei-kaytossa")) "Jos geometria-aineiston tiedot puuttuvat tietokannasta, tehdään päivitys aineistopalvelimelta.")
              (is (= nil (gp-kyselyt/pitaako-paivittaa? testitietokanta "paikallinen-ei-kaytossa")) "Jos geometria-aineiston tiedot puuttuvat tietokannasta, tehdään päivitys aineistopalvelimelta.")
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

