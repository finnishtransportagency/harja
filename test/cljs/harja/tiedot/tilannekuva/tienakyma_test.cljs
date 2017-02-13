(ns harja.tiedot.tilannekuva.tienakyma-test
  (:require [harja.tiedot.tilannekuva.tienakyma :as tienakyma]
            [clojure.test :refer-macros [deftest is]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]))

(deftest alkupvm-asetetaan-oikein
  (let [nyt (pvm/nyt)
        app (tuck/process-event (tienakyma/->Nakymassa true) {:valinnat {:alku nil :loppu nil}})]
    (is (pvm/sama-pvm? nyt (get-in app [:valinnat :alku])) "alku asetettu tähän päivään")
    (is (pvm/sama-pvm? nyt (get-in app [:valinnat :loppu])) "loppu asetettu tähän päivään")
    (is (pvm/ennen? (get-in app [:valinnat :alku]) (get-in app [:valinnat :loppu]))
        "alku ennen loppua")))

(deftest alkupvm-ei-aseteta-jos-olemassa
  (let [alku (pvm/luo-pvm 1970 0 1)
        app-ennen {:valinnat {:alku alku :loppu nil}}
        app (tuck/process-event (tienakyma/->Nakymassa true) app-ennen)]
    (is (= (:valinnat app-ennen) (:valinnat app)) "Valinnat eivät muuttuneet")))
