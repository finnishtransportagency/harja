(ns harja.views.urakka.kohdeluettelo.paallystysilmoitukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]

    [harja.pvm :refer [->pvm] :as pvm]
    [harja.views.urakka.kohdeluettelo.paallystysilmoitukset :as pot]
    [harja.loki :refer [log]]))

(deftest tien-pituus-laskettu-oikein
  (let [tie {:let 5 :losa 3}]
    (is (= (pot/laske-tien-pituus tie) 2))))