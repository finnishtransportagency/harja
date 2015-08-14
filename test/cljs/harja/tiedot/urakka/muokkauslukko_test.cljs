(ns harja.tiedot.muokkauslukko-test
  (:require [harja.tiedot.muokkauslukko :as lukko]
            [cljs-time.core :as t]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.loki :refer [log]]
            [harja.pvm :refer [->pvm] :as pvm]))

(deftest muokkauslukon-idn-muodostus-toimii
  (is (= (lukko/muodosta-lukon-id "paallystysilmoitus" 1234) "paallystysilmoitus_1234")))
