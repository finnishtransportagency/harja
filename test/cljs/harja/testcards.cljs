(ns harja.testcards
  (:require [reagent.core :as r]
            [harja.ui.lomake-test :as lomake-test]))

(defn testcards []
  [:div "testikortteja"
   [lomake-test/testilomake ]])

(defn ^:export main []
  (r/render [#'testcards] (.getElementById js/document "app")))
