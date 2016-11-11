(ns harja-laadunseuranta.tiedot.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]))

(def mittaustyypin-lahtoarvo {:kitkamittaus "0,"})

(defn numeronappain-painettu! [numero syotto-atom]
  (.log js/console "Numero syötetty: " (pr-str numero))
  (let [nykyinen-syotto (:nykyinen-syotto @syotto-atom)
        uusi-syotto (str nykyinen-syotto numero)]
    (swap! syotto-atom assoc :nykyinen-syotto uusi-syotto)))

(defn tyhjennyspainike-painettu! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :nykyinen-syotto (mittaustyyppi mittaustyypin-lahtoarvo)))

(defn syotto-valmis! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :syotot (conj (:syotot @syotto-atom) (:nykyinen-syotto @syotto-atom)))
  (swap! syotto-atom assoc :nykyinen-syotto (mittaustyyppi mittaustyypin-lahtoarvo))
  (.log js/console "Syötöt nyt: " (pr-str (:syotot @syotto-atom))))