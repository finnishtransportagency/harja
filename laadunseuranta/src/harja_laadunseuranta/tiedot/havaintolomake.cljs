(ns harja-laadunseuranta.tiedot.havaintolomake
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.ratom :as ratom]))


(defn tallenna-lomake! []
  (.log js/console "Tallenna lomake!")
  #_(peruuta-pikavalinta)
  #_(reitintallennus/kirjaa-kertakirjaus @s/idxdb % @s/tarkastusajo-id)
  #_(kartta/lisaa-kirjausikoni "!")
  #_(tallennettu-fn))

(defn peruuta-lomake! []
  (.log js/console "Peru lomake!")
  #_(peruuta-pikavalinta)
  #_(peruutettu-fn))
