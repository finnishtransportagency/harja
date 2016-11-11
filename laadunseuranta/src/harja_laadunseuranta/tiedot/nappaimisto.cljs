(ns harja-laadunseuranta.tiedot.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [cljs-time.local :as lt]
            [cljs-time.coerce :as tc]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]))

(def mittaustyypin-lahtoarvo {:kitkamittaus "0,"})

(defn numeronappain-painettu! [numero syotto-atom]
  (.log js/console "Numero syötetty: " (pr-str numero))
  (let [nykyinen-syotto (:nykyinen-syotto @syotto-atom)
        uusi-syotto (str nykyinen-syotto numero)]
    (swap! syotto-atom assoc :nykyinen-syotto uusi-syotto)))

(defn alusta-mittaussyotto! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :nykyinen-syotto (mittaustyyppi mittaustyypin-lahtoarvo)))

(defn tyhjennyspainike-painettu! [mittaustyyppi syotto-atom]
  (alusta-mittaussyotto! mittaustyyppi syotto-atom))

(defn syotto-valmis! [mittaustyyppi syotto-atom]
  (swap! syotto-atom assoc :syotot (conj (:syotot @syotto-atom) (:nykyinen-syotto @syotto-atom)))
  (swap! syotto-atom assoc :nykyinen-syotto (mittaustyyppi mittaustyypin-lahtoarvo))
  (.log js/console "Syötöt nyt: " (pr-str (:syotot @syotto-atom))))

(defn kirjaa-kitkamittaus! [arvo]
  (.log js/console "Kirjataan uusi kitkamittaus: " (pr-str arvo))
  (reitintallennus/kirjaa-kertakirjaus
      @s/idxdb
      {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
       :aikaleima (tc/to-long (lt/local-now))
       :tarkastusajo @s/tarkastusajo-id
       :havainnot @s/jatkuvat-havainnot
       :mittaukset {:kitkamittaus arvo}}))