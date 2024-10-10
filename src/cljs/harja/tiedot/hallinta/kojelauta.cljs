(ns harja.tiedot.hallinta.kojelauta
  (:require [clojure.string :as clj-str]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
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

(def tila (atom {:urakat []
                 :valinnat {:ely nil
                            :urakat nil
                            :urakkavuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))}}))

(defn ks-tilojen-yhteenveto
  "Palauttaa käyttöliittymän koosteriville kustannussuunnitelman tilojen yhteenvedon"
  [urakat]
  (let [kaikkien-urakoiden-lkm (count urakat)
        urakat-joissa-ks-aloittamatta (count (keep (fn [rivi]
                                                     (when (= "aloittamatta" (get-in rivi [:ks_tila :suunnitelman_tila])) true))
                                               urakat))
        urakat-joissa-ks-aloitettu (count (keep (fn [rivi]
                                                  (when (= "aloitettu" (get-in rivi [:ks_tila :suunnitelman_tila])) true))
                                            urakat))
        urakat-joissa-ks-valmiina (count (keep (fn [rivi]
                                                 (when (= "vahvistettu" (get-in rivi [:ks_tila :suunnitelman_tila])) true))
                                           urakat))
        ks-tilojen-yhteenveto (when-not (empty? urakat)
                                (clj-str/join ", "
                                  [(str "Aloittamatta: " (fmt/prosentti-opt (* 100 (/ urakat-joissa-ks-aloittamatta kaikkien-urakoiden-lkm))))
                                   (str "aloitettu: " (fmt/prosentti-opt (* 100 (/ urakat-joissa-ks-aloitettu kaikkien-urakoiden-lkm))))
                                   (str "valmiina: " (fmt/prosentti-opt (* 100 (/ urakat-joissa-ks-valmiina kaikkien-urakoiden-lkm))))]))]
    ks-tilojen-yhteenveto))

(defrecord AsetaSuodatin [avain valinta])
(defrecord HaeUrakat [])
(defrecord HaeUrakatOnnistui [vastaus])
(defrecord HaeUrakatEpaonnistui [vastaus])

(extend-protocol tuck/Event
  AsetaSuodatin
  (process-event [{:keys [avain valinta]} app]
    (assoc-in app [:valinnat avain] valinta))

  HaeUrakat
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakat-kojelautaan
      {:hoitokauden-alkuvuosi (get-in app [:valinnat :urakkavuosi])
       :urakka-idt (map :id (get-in app [:valinnat :urakat]))
       :ely-id (get-in app [:valinnat :ely :id])}
      {:onnistui ->HaeUrakatOnnistui
       :epaonnistui ->HaeUrakatEpaonnistui})
    (assoc app :haku-kaynnissa? true))

  HaeUrakatOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app
      :haku-kaynnissa? false
      :urakat vastaus
      :urakkahaku (tee-urakkahaku vastaus)))

  HaeUrakatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Virhe urakoiden haussa" :varoitus)
    (assoc app
      :urakat []
      :haku-kaynnissa? false)))
