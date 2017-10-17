(ns harja.views.vesivaylat.hallinta.alukset
  "Näkymä vesiväyläurakoiden alusten hallitsemiseen."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.vesivaylat.hallinta.alukset :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.debug :as debug]
            [harja.domain.vesivaylat.alus :as alus]
            [harja.loki :refer [log]]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.grid :as grid]))

(defn alukset* [e! app]
  (komp/luo
    (komp/sisaan (constantly true)) ;; TODO Hae alukset
    (fn [e! {:keys [alukset] :as app}]
      (if (nil? (:alukset app))
        [yleiset/ajax-loader]
        [grid/grid
         {:otsikko "Alukset"
          :tyhja "Ei aluksia"
          :tunniste ::alus/mmsi
          :tallenna (fn [alukset]
                      (log "TALLENNA"))} ;; TODO
         [{:otsikko "MMSI"
           :nimi ::alus/mmsi
           :tyyppi :numero}
          {:otsikko "Nimi"
           :nimi ::alus/nimi
           :tyyppi :numero}
          {:otsikko "Lisätiedot"
           :nimi ::alus/lisatiedot
           :tyyppi :numero}
          {:otsikko "Urakoitsija"
           :nimi ::alus/urakoitsija-id
           :tyyppi :numero}]
         alukset]))))

(defn alukset []
  [tuck tiedot/tila alukset*])


