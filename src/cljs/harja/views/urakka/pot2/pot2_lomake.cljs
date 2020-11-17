(ns harja.views.urakka.pot2.pot2-lomake

  (:require
    [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
    [harja.ui.komponentti :as komp]))


(defn- alusta [e! app]
  [:div "Alustatiedot"])

(defn- kulutuskerros [e! app]
  [:div "Kulutuskerroksen tiedot"])


(defn- perustiedot [e! app]
  [:div "Perustiedot"])

(defn pot2-lomake [e! app]
  (komp/luo
    (komp/lippu pot2-tiedot/pot2-nakymassa?)
    (komp/piirretty (fn [this]
                      (println "component did mount")))
    (fn [e! app]
      [:div "Tämä on POT2 lomakkeen pohja"])))
