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
            [harja.domain.organisaatio :as o]
            [harja.loki :refer [log]]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.grid :as grid]))

(defn alukset* [e! app]
  (komp/luo
    (komp/sisaan (fn []
                   (e! (tiedot/->HaeAlukset))
                   (e! (tiedot/->HaeUrakoitsijat))))
    (fn [e! {:keys [alukset urakoitsijat] :as app}]
      (if (or (nil? (:alukset app))
              (nil? (:urakoitsijat app)))
        [yleiset/ajax-loader]
        [grid/grid
         {:otsikko "Alukset"
          :tyhja "Ei aluksia"
          :tunniste ::alus/mmsi
          :tallenna (fn [alukset]
                      (e! (tiedot/->TallennaAlukset alukset)))}
         [{:otsikko "MMSI"
           :nimi ::alus/mmsi
           :tyyppi :numero
           :leveys 1
           :validoi [[:ei-tyhja "Anna MMSI"]
                     [:uniikki "MMSI on jo olemassa"]]}
          {:otsikko "Nimi"
           :nimi ::alus/nimi
           :tyyppi :numero
           :leveys 3
           :validoi [[:ei-tyhja "Anna nimi"]]}
          {:otsikko "Urakoitsija"
           :nimi ::alus/urakoitsija-id
           :tyyppi :valinta
           :valinnat urakoitsijat
           :valinta-arvo ::o/id
           :fmt #(let [urakoitsija (o/organisaatio-idlla % urakoitsijat)]
                   (log "FMT ARG " (pr-str %) " JA UR: " (pr-str urakoitsija))
                   (::o/nimi urakoitsija))
           :valinta-nayta #(if % (::o/nimi %) "- Valitse urakoitsija -")
           :validoi [[:ei-tyhja "Anna urakoitsija"]]
           :leveys 2}
          {:otsikko "Lisätiedot"
           :nimi ::alus/lisatiedot
           :tyyppi :numero
           :leveys 5}]
         alukset]))))

(defn alukset []
  [tuck tiedot/tila alukset*])


