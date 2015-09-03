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

(def sillat (reaction<! [paalla? @karttataso-sillat
                         urakka @nav/valittu-urakka
                         listaus @listaus]
                        
                        (if (and paalla? urakka)
                          (do (log "Siltataso päällä, haetaan sillat urakalle: " (:nimi urakka) " (id: " (:id urakka) ")")
                              (go (let [sillat (<! (k/post! :hae-urakan-sillat {:urakka-id (:id urakka)
                                                                                :listaus listaus}))]
                                    (map #(assoc % :type :silta) sillat))))
                          
                          ;; Jos siltataso ei päällä tai urakkaa ei valittu, asetetaan nil dataksi
                          nil)))

(defn paivita-silta! [id funktio & args]
  (swap! sillat (fn [sillat]
                  (mapv (fn [silta]
                          (if (= id (:id silta))
                            (apply funktio silta args)
                            silta)) sillat))))
