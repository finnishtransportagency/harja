(ns harja.views.urakka.kohdeluettelo.paikkausilmoitukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]

    [harja.pvm :refer [->pvm] :as pvm]
    [harja.views.urakka.kohdeluettelo.paikkausilmoitukset :as paikkausilmoitukset]
    [harja.loki :refer [log]]
    [harja.domain.paallystys.pot :as pot]))


(deftest hinta-alv-laskettu-oikein
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 10 24) 2) "12.40"))
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 6 10) 2) "6.60"))
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 3.2 50) 2) "4.80"))
  (is (= (.toFixed (paikkausilmoitukset/laske-tyon-alv 4.5 100) 2) "9.00")))