(ns harja.tyokalut.predikaatti
  (:require #?@(:clj  [[clojure.core.async.impl.channels :as chanels-impl]
                       [clojure.core.async.impl.protocols :as protocols-impl]]

                :cljs [[cljs.core.async.impl.channels :as channels-impl]
                       [cljs.core.async.impl.protocols :as protocols-impl]])))

(defn chan?
  "Tarkastaa onko annettu parametry async kirjaston chan. Tämän funktion toimiminen
   riippuu async kirjaston sisäisestä toiminnasta, joten tuon kirjaston päivittäminen
   mahdollisesti hajoittaa tämän funktion."
  [c]
  (instance? channels-impl/ManyToManyChannel
             c))

(defn chan-closed?
  "Tarkastaa onko annettu kanava sammutettu. Tämän funktion toimiminen riippuu async kirjaston
   sisäisestä toiminnasta, joten tuon kirjaston päivittäminen mahdollisesti hajoittaa tämän funktion."
  [c]
  (protocols-impl/closed? c))
