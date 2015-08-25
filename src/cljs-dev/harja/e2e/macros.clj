(ns harja.e2e.macros
  (:require [reagent.ratom :refer [run!]]))

(defmacro wait-reactions
  "Odottaa että lista reaktioita on non-nil ja suorittaa bodyn asynkronisena testinä"
  [reaktiot & body]
  (assert (vector? reaktiot) "reaktiot täytyy olla vektori")
  `(let [already-run# (atom false)]
     (run!
      (when (not @already-run#)
        (when (every? #(not (nil? (deref %))) ~reaktiot)
          (reagent.core/flush)
          (reset! already-run# true)
          ~@body
          (cemerick.cljs.test/done))))))


