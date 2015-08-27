(ns harja.views.urakka.kohdeluettelo.paallystysilmoitukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]

    [harja.pvm :refer [->pvm] :as pvm]
    [harja.views.urakka.kohdeluettelo.paallystysilmoitukset :as paallystysilmoitukset]
    [harja.loki :refer [log]]
    [harja.domain.paallystys.pot :as pot]))

(deftest tien-pituus-laskettu-oikein
  (let [tie {:aet 3 :let 5}]
    (is (= (paallystysilmoitukset/laske-tien-pituus tie) 2))))

(deftest muutos-kokonaishintaan-laskettu-oikein
  (let [tyot [{:tilattu-maara 10 :toteutunut-maara 15 :yksikkohinta 1}
              {:tilattu-maara 15 :toteutunut-maara 15  :yksikkohinta 666}
              {:tilattu-maara 4 :toteutunut-maara 5 :yksikkohinta 8}]
    tyot2 [{:tilattu-maara 4 :toteutunut-maara 2 :yksikkohinta 15}]]
    (is (= (pot/laske-muutokset-kokonaishintaan tyot) 13))
    (is (= (pot/laske-muutokset-kokonaishintaan tyot2) -30))))