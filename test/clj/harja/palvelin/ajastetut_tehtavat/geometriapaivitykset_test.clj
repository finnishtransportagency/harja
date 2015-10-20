(ns harja.palvelin.ajastetut-tehtavat.geometriapaivitykset-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [com.stuartsierra.component :as component]
            [clj-time.periodic :refer [periodic-seq]]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.alk-komponentti :as alk]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieverkko :as tieverkon-tuonti]
            [harja.palvelin.ajastetut-tehtavat.geometriapaivitykset :as geometriapaivitykset]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
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