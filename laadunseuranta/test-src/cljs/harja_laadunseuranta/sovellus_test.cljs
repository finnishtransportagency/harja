(ns harja-laadunseuranta.sovellus-test
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja-laadunseuranta.sovellus :as s]
            [reagent.core :as reagent :refer [atom]]))

(deftest sovellus-test
  (is (= 1 1)))
