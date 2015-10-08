(ns harja.e2e.macros
  (:refer-clojure :exclude [run!])
  (:require [reagent.ratom :refer [run!]]))


(defmacro wait-reactions
  "Odottaa että lista reaktioita muuttuu arvoltaan ja suorittaa bodyn asynkronisena testinä"
  [reaktiot & body]
  (assert (vector? reaktiot) "reaktiot täytyy olla vektori")
  `(let [already-run# (atom false)
         reactions# ~reaktiot
         orig-values# (map deref reactions#)]
     (run!
      (when (not @already-run#)
        (when (every? true? (map (fn [a# b#] (not= a# (deref b#))) orig-values# reactions#))
          (reagent.core/force-update-all)
          (reset! already-run# true)
          ~@body
          (cemerick.cljs.test/done))))))

