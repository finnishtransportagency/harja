(ns harja.tiedot.tilannekuva.tienakyma
  "Tien 'supernäkymän' tiedot."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log]]))

(defonce valinnat (atom {}))
(defonce sijainti (r/cursor valinnat [:sijainti]))

(defn- paivita-valinnat* [vanha uusi]
  (log "VANHA: " (pr-str vanha) " => UUSI: " (pr-str uusi))
  (let [alku-muuttunut? (not= (:alku vanha) (:alku uusi))]
    (as-> uusi v

      ;; Jos alku muuttunut ja vanhassa alku ja loppu olivat samat,
      ;; päivitä myös loppukenttä
      (if (and alku-muuttunut?
               (= (:alku vanha) (:loppu vanha)))
        (assoc v :loppu (:alku uusi))
        v))))

(defn paivita-valinnat
  "Päivittää lomakkeelta tulevat valinnat"
  [uudet-valinnat]
  (swap! valinnat paivita-valinnat* uudet-valinnat))
