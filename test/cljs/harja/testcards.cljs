(ns harja.testcards
  (:require [reagent.core :as r]
            [devcards.core :refer [start-devcard-ui!]]
            [harja.ui.lomake-test :as lomake-test])
  (:require-macros [devcards.core :refer [defcard reagent]]))

(defn ^:export main []
  (start-devcard-ui!))
