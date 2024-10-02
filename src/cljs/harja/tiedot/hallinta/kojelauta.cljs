(ns harja.tiedot.hallinta.kojelauta
  (:require [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.ui.protokollat :as protokollat]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))


(defn tee-urakkahaku [urakat]
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [itemit (if (< (count teksti) 1)
                         urakat
                         (filter #(and
                                    (:nimi %)
                                    (not= (.indexOf (.toLowerCase (:nimi %))
                                            (.toLowerCase teksti)) -1))
                           urakat))]
            (vec (sort-by :nimi itemit)))))))

(def tila (atom {:urakkavuodet (range 2016 2025)
                 :kriteerit [{:nimi "Jotain"}
                             {:nimi "Jotain muuta"}]
                 :urakat []
                 :valinnat {:urakkavuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))}}))

(defrecord Valitse [avain valinta])
(defrecord HaeUrakat [])
(defrecord HaeUrakatOnnistui [vastaus])
(defrecord HaeUrakatEpaonnistui [vastaus])

(extend-protocol tuck/Event
  Valitse
  (process-event [{:keys [avain valinta]} app]
    (assoc-in app [:valinnat avain] valinta))

  HaeUrakat
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakat-kojelautaan
      {:hoitokauden-alkuvuosi (get-in app [:valinnat :urakkavuosi])
       :urakka-idt (map :id (get-in app [:valinnat :urakat]))}
      {:onnistui ->HaeUrakatOnnistui
       :epaonnistui ->HaeUrakatEpaonnistui}))

  HaeUrakatOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app
      :urakat vastaus
      :urakkahaku (tee-urakkahaku vastaus)))

  HaeUrakatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (js/console.error "Virhe urakoiden haussa!" vastaus)
    (viesti/nayta-toast! "Virhe urakoiden haussa" :varoitus)
    (assoc app :urakat [])))
