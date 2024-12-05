(ns harja.domain.skeema
  "Jaettavia skeemamäärityksiä"
  (:require [schema.core :as s])
  #?(:cljs (:import (goog.date DateTime))
     :clj (:import (java.util Date))))

(def pvm-tyyppi #?(:clj Date :cljs DateTime))

(def +tyotyypit+ [:yksikkohintainen :kokonaishintainen :akillinen-hoitotyo :lisatyo :muutostyo :vahinkojen-korjaukset])

(def Toteuma
  "Määrittelee yhden toteuman skeeman. Ei sisällä reittipisteitä."
  {:urakka-id s/Int
   :sopimus-id s/Int
   :alkanut pvm-tyyppi
   :paattynyt pvm-tyyppi
   :tyyppi (apply s/enum +tyotyypit+)
   :suorittajan-nimi s/Str
   :lisatieto s/Str
   :tehtavat [{:toimenpidekoodi s/Int
               :maara s/Num}]
   :materiaalit [{:materiaalikoodi s/Int
                  :maara s/Num}]})


(defn tarkista
  "Tarkistaa täyttääkö annettu data annetun skeeman vaatimukset. Jos täyttää, palauttaa nil.
  Jos ei täytä, palauttaa kuvauksen skeeman rikkovista osista."
  [skeema data]
  (s/check skeema data))

(defn validoi
  "Validoi että annettu data täyttää annetun skeeman vaatimukset. Jos ei täytä, heittää
poikkeuksen. Jos täyttää, palauttaa datan."
  [skeema data]
  (s/validate skeema data))
