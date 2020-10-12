(ns harja.tyokalut.predikaatti
  (:require #?(:clj [clojure.core.async.impl.channels]
               :cljs [cljs.core.async.impl.channels])))

(defn chan?
  "Tarkastaa onko annettu parametry async kirjaston chan. Tämän funktion toimiminen
   riippuu async kirjaston sisäisestä toiminnasta, joten tuon kirjaston päivittäminen
   mahdollisesti hajoittaa tämän funktion."
  [c]
  (instance? #?(:clj clojure.core.async.impl.channels.ManyToManyChannel
                :cljs cljs.core.async.impl.channels.ManyToManyChannel)
             c))
