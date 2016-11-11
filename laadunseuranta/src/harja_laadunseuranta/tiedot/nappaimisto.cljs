(ns harja-laadunseuranta.tiedot.nappaimisto
  (:require [reagent.core :as reagent :refer [atom]]
            [harja-laadunseuranta.utils :refer [timestamp ipad?]]
            [harja-laadunseuranta.tiedot.sovellus :as s]
            [cljs-time.local :as lt]
            [cljs-time.coerce :as tc]
            [harja-laadunseuranta.tiedot.math :as math]
            [harja-laadunseuranta.tiedot.fmt :as fmt]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]))

(def mittaustyypin-lahtoarvo {:kitkamittaus "0,"
                              :lumisuus ""
                              :talvihoito-tasaisuus ""})

(def syoton-max-merkkimaara {:kitkamittaus 4
                             :lumisuus 3
                             :talvihoito-tasaisuus 3})

(def syoton-rajat {:kitkamittaus [0 0.999]
                   :lumisuus [0 100]
                   :talvihoito-tasaisuus [0 100]})

(defn numeronappain-painettu! [numero mittaustyyppi syotto-atom]
  (.log js/console "Numero syötetty: " (pr-str numero))
  (let [nykyinen-syotto (:nykyinen-syotto @syotto-atom)
        suurin-sallittu-tarkkuus (mittaustyyppi syoton-max-merkkimaara)
        salli-syotto? (and (< (count nykyinen-syotto) suurin-sallittu-tarkkuus)
                           (>= numero (first (mittaustyyppi syoton-rajat)))
                           (<= numero (second (mittaustyyppi syoton-rajat))))
        uusi-syotto (if salli-syotto?
                      (str nykyinen-syotto numero)
                      nykyinen-syotto)]
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

(defn kirjaa-lumisuus! [arvo]
  (.log js/console "Kirjataan uusi lumisuus: " (pr-str arvo))
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
     :aikaleima (tc/to-long (lt/local-now))
     :tarkastusajo @s/tarkastusajo-id
     :havainnot @s/jatkuvat-havainnot
     :mittaukset {:lumisuus arvo}}))

(defn kirjaa-talvihoito-tasaisuus! [arvo]
  (.log js/console "Kirjataan uusi talvihoidon tasaisuus: " (pr-str arvo))
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
     :aikaleima (tc/to-long (lt/local-now))
     :tarkastusajo @s/tarkastusajo-id
     :havainnot @s/jatkuvat-havainnot
     :mittaukset {:talvihoito-tasaisuus arvo}}))