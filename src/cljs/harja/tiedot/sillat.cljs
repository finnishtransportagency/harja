(ns harja.tiedot.sillat
  "Sillat karttatason vaatimat tiedot. Sillat on jaettu geometrisesti hoidon alueurakoiden alueille."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]))

(def taso-sillat (atom false))

(def sillat (reaction<! (let [paalla? @taso-sillat
                              urakka @nav/valittu-urakka]
                          (if (and paalla? urakka)
                            (do (log "Siltataso päällä, haetaan sillat urakalle: " (:nimi urakka) " (id: " (:id urakka) ")")
                                (k/post! :hae-urakan-sillat (:id urakka)))

                            ;; Jos siltataso ei päällä tai urakkaa ei valittu, asetetaan nil dataksi
                            nil))
                        (fn [sillat]
                          (map #(assoc % :type :silta) sillat))
                        ))
