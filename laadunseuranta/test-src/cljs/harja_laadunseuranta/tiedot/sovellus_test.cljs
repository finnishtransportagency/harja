(ns harja-laadunseuranta.tiedot.sovellus-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [reagent.core :as reagent :refer [atom]]))

(deftest sovellus-test
  (is (= 1 1)))
