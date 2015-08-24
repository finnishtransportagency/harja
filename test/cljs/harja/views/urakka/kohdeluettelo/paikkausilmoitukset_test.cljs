(ns harja.views.urakka.kohdeluettelo.paikkausilmoitukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]

    [harja.pvm :refer [->pvm] :as pvm]
    [harja.domain.paikkaus.minipot :as minipot]
    [harja.loki :refer [log]]
    [harja.domain.paallystys.pot :as pot]
    [harja.views.urakka.kohdeluettelo.paikkausilmoitukset :as paikkausilmoitukset]))


(deftest hinta-alv-laskettu-oikein
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 10 24) 2) "12.40"))
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 6 10) 2) "6.60"))
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 3.2 50) 2) "4.80"))
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 4.5 100) 2) "9.00")))

(deftest laskee-minipotin-kokonaishinnan-oikein
  (let [tyot [{:yks_hint_alv_0 10 :maara 15}
              {:yks_hint_alv_0 7 :maara 4}
              {:yks_hint_alv_0 2 :maara 2}]
        tyot2 [{:yks_hint_alv_0 2.4 :maara 6.5}
               {:yks_hint_alv_0 3.3 :maara 2.5}]]
    (is (= (minipot/laske-kokonaishinta tyot) 182))
    (is (= (.toFixed (minipot/laske-kokonaishinta tyot2) 2) "23.85"))))