(ns harja.tiedot.sillat
  "Sillat karttatason vaatimat tiedot. Sillat on jaettu geometrisesti hoidon alueurakoiden alueille."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.atom :refer-macros [reaction<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def karttataso-sillat (atom false))

(def listaus (atom :kaikki))

(def sillat
  (reaction<! [paalla? @karttataso-sillat
               urakka @nav/valittu-urakka
               listaus @listaus]
              {:nil-kun-haku-kaynnissa? true}
              (when (and paalla? urakka)
                (log "Siltataso päällä, haetaan sillat urakalle: "
                     (:nimi urakka) " (id: " (:id urakka) ")")
                (go (mapv #(assoc % :type :silta)
                          (<! (k/post! :hae-urakan-sillat
                                       {:urakka-id (:id urakka)
                                        :listaus listaus})))))))

(defn paivita-silta! [id funktio & args]
  (swap! sillat (fn [sillat]
                  (mapv (fn [silta]
                          (if (= id (:id silta))
                            (apply funktio silta args)
                            silta)) sillat))))
