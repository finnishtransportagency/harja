(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti :as alk]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.ajastetut-tehtavat.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :refer :all]
            [harja.palvelin.tyokalut.arkisto :as arkisto]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.tyokalut.kansio :as kansio]
            [clojure.java.io :as io]))


(defn aja-tieverkon-paivitys []
  "REPL-testiajofunktio"
  (let [testitietokanta (apply tietokanta/luo-tietokanta testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)]
    (component/start integraatioloki)
    (component/start alk)
    (geometriapaivitykset/tarkista-paivitys
      alk
      testitietokanta
      "tieverkko"
      "http://185.26.50.104/Tieosoiteverkko.zip"
      "/Users/mikkoro/Desktop/Tieverkko-testi/"
      "Tieosoiteverkko.zip"
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/mikkoro/Desktop/Tieverkko-testi/Tieosoiteverkko.shp")))))


(defn aja-soratien-hoitoluokkien-paivitys []
  "REPL-testiajofunktio"
  (let [testitietokanta (apply tietokanta/luo-tietokanta testitietokanta)
        integraatioloki (assoc (integraatioloki/->Integraatioloki nil) :db testitietokanta)
        alk (assoc (alk/->Alk) :db testitietokanta :integraatioloki integraatioloki)]
    (component/start integraatioloki)
    (component/start alk)
    (geometriapaivitykset/tarkista-paivitys
      alk
      testitietokanta
      "tieverkko"
      "http://185.26.50.104/tl132.tgz"
      "/Users/mikkoro/Desktop/Soratiehoitoluokat-testi/"
      "Sorateiden-hoitoluokat.tgz"
      (fn []
        (tieverkon-tuonti/vie-tieverkko-kantaan
          testitietokanta
          "file:///Users/mikkoro/Desktop/Soratiehoitoluokat-testi/Sorateiden-hoitoluokat.shp")))))

(def +arkistot-polku+ "test/resurssit/arkistot/")
(def +arkistot-target-polku+ "test/resurssit/arkistot/target/")

(defn testaa-tiedoston-purku [tiedosto-nimi]
  (arkisto/pura-paketti (str +arkistot-polku+ tiedosto-nimi))
  ; Tarkista, että tiedostot purkautuivat
  (is (true? (.exists (clojure.java.io/file (str +arkistot-polku+ "teksti.txt")))))
  (is (true? (.exists (clojure.java.io/file (str +arkistot-polku+ "kuva.png")))))
  ; Siirrä puretut tiedostot target-kansioon
  (io/copy (io/file (str +arkistot-polku+ "teksti.txt")) (io/file (str +arkistot-target-polku+ "teksti.txt")))
  (io/copy (io/file (str +arkistot-polku+ "kuva.png")) (io/file (str +arkistot-target-polku+ "kuva.png")))
  (clojure.java.io/delete-file (str +arkistot-polku+ "kuva.png"))
  (clojure.java.io/delete-file (str +arkistot-polku+ "teksti.txt"))
  ; Tyhjennä target-kansio
  (kansio/poista-tiedostot +arkistot-target-polku+)
  (is (= 0 (count (.listFiles (clojure.java.io/file +arkistot-target-polku+))))))

(deftest testaa-pura-macissa-tehty-zip
  (testaa-tiedoston-purku"test_zip_mac.zip"))

(deftest testaa-pura-macissa-tehty-gzip
  (testaa-tiedoston-purku"test_gzip_mac.tgz"))

(deftest testaa-pura-zip
  (testaa-tiedoston-purku "test_zip.zip"))

(deftest testaa-pura-gzip
  (testaa-tiedoston-purku "test_gzip.tgz"))