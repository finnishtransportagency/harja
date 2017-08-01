(ns harja.domain.reittipiste
  "Toteuman reittipisteiden spec määrittelyt"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.muokkaustiedot :as m]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]])
            #?(:cljs [specql.impl.registry]))
  #?(:cljs (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["reittipiste_materiaali" ::reittipiste-materiaali]
  ["reittipiste_tehtava" ::reittipiste-tehtava]
  ["reittipistedata" ::reittipiste]
  ["toteuman_reittipisteet" ::toteuman-reittipisteet
   {"toteuma" ::toteuma-id}])

(defn reittipiste
  "Apuri reittipisteen luomiseksi"
  ([aika koordinaatit hoitoluokat]
   (reittipiste aika koordinaatit hoitoluokat [] []))
  ([aika
    {:keys [x y] :as koordinaaatit}
    {:keys [talvihoitoluokka soratiehoitoluokka] :as hoitoluokat}
    tehtavat materiaalit]
   {::aika aika
    ::sijainti [x y]
    ::talvihoitoluokka talvihoitoluokka
    ::soratiehoitoluokka soratiehoitoluokka
    ::tehtavat tehtavat
    ::materiaalit materiaalit}))
