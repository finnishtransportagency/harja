(ns harja.tiedot.urakka.toteumat.tiemerkinta-muut-kustannukset-test
  (:require
    [harja.tiedot.urakka.toteumat.tiemerkinta-muut-kustannukset :as muut-tyot]
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.loki :refer [log]]
    [harja.pvm :refer [->pvm] :as pvm]))

(def laskentakohteet
  [{:id 1 :nimi "kohde 1" :urakka 4}
   {:id 2 :nimi "kohde 2" :urakka 4}
   {:id 3 :nimi "kohde 3" :urakka 4}])



(deftest kohteiden-muuntaminen []
  (let [muunnetut-kohteet (muut-tyot/muunna-laskentakohteet laskentakohteet)
        odotettu-tulos {nil "Ei laskentakohdetta"
                        1 "kohde 1"
                        2 "kohde 2"
                        3 "kohde 3"}]
    (is (= muunnetut-kohteet odotettu-tulos))))


