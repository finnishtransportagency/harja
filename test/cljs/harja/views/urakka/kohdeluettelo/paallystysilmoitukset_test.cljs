(ns harja.views.urakka.kohdeluettelo.paallystysilmoitukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]

    [harja.pvm :refer [->pvm] :as pvm]
    [harja.views.urakka.kohdeluettelo.paallystysilmoitukset :as paallystysilmoitukset]
    [harja.loki :refer [log]]
    [harja.domain.paallystys.pot :as pot]))

(deftest tien-pituus-laskettu-oikein
  (let [tie1 {:aet 3 :let 5}
        tie2 {:aet 5 :let 5}
        tie3 {:aet 3 :let -100}
        tie4 {:aet 1 :let 2}
        tie5 {:aet 0 :let 1}
        tie6 {:aet 1}]
    (is (= (paallystysilmoitukset/laske-tien-pituus tie1) 2))
    (is (= (paallystysilmoitukset/laske-tien-pituus tie2) 0))
    (is (= (paallystysilmoitukset/laske-tien-pituus tie3) 0))
    (is (= (paallystysilmoitukset/laske-tien-pituus tie4) 1))
    (is (= (paallystysilmoitukset/laske-tien-pituus tie5) 1))
    (is (= (paallystysilmoitukset/laske-tien-pituus tie6) 0))))

(deftest muutos-kokonaishintaan-laskettu-oikein
  (let [tyot [{:tilattu-maara 10 :toteutunut-maara 15 :yksikkohinta 1}
              {:tilattu-maara 15 :toteutunut-maara 15  :yksikkohinta 666}
              {:tilattu-maara 4 :toteutunut-maara 5 :yksikkohinta 8}]
    tyot2 [{:tilattu-maara 4 :toteutunut-maara 2 :yksikkohinta 15}]]
    (is (= (pot/laske-muutokset-kokonaishintaan tyot) 13))
    (is (= (pot/laske-muutokset-kokonaishintaan tyot2) -30))))