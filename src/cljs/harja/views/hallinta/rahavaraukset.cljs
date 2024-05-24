(ns harja.views.hallinta.rahavaraukset
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.napit :as napit]
            [harja.ui.grid :as grid]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.rahavaraukset :as tiedot]))

(defn rahavaraukset* [e! _app]
  (komp/luo
    (komp/sisaan #(do (e! (tiedot/->HaeRahavaraukset))))
    (fn [e! {:keys [valittu-urakka urakat urakoiden-rahavaraukset rahavaraukset tallennukset-kesken] :as app}]
      (let [valitun-urakan-rahavaraukset (filter #(= (:urakka-id %) (:urakka-id valittu-urakka))
                                           urakoiden-rahavaraukset)
            ;; Merkitään, onko valittu
            muokatut-rahavaraukset (map
                                     (fn [rahavaraus]
                                       (-> rahavaraus
                                         (assoc :valittu? (some #(= (:id %) (:id rahavaraus)) valitun-urakan-rahavaraukset))
                                         (assoc :urakkakohtainen-nimi (:urakkakohtainen-nimi (first (filter #(= (:id %) (:id rahavaraus)) valitun-urakan-rahavaraukset))))))
                                     rahavaraukset)
            rahavaraukset-atom (r/atom (zipmap (range) muokatut-rahavaraukset))]

        [:div.rahavaraukset-hallinta
         [:h1 "Rahavaraukset"]
         [debug/debug app]
         [yleiset/pudotusvalikko
          "Urakka"
          {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
           :valinta valittu-urakka
           :format-fn :urakka-nimi}
          urakat]
         (when (and muokatut-rahavaraukset (> (count muokatut-rahavaraukset) 0))
           [:div
            [yleiset/info-laatikko :vahva-ilmoitus "Rahavarauksen poistaminen poistaa sen kaikki tehtävät sekä se
            poistetaan kaikilta urakoilta. Poisto ei ole mahdollinen, jos rahavaraus on käytössä.
            Älä siis yritäkään poistaa niitä, ellet ole täysin varma, että mitä olet tekemässä."]
            [grid/muokkaus-grid
             {:tyhja "Ei rahavarauksia."
              :tunniste :id
              :voi-lisata? true
              :voi-kumota? false
              :voi-poistaa? (constantly true)
              :voi-muokata? true
              :on-rivi-blur (fn [rivi _]
                              (e! (tiedot/->MuokkaaRahavaraus valittu-urakka rivi)))
              :uusi-rivi (fn [rivi]
                           (js/console.log "uusi rivi :: rivi:" (pr-str rivi))
                           (assoc rivi :id -1 :valittu? nil :nimi "" :urakkakohtainen-nimi ""))
              ; Roskakorinappula rivin päässä
              :toimintonappi-fn (fn [rivi _muokkaa! id]
                                  [napit/poista "Poista"
                                   #(do
                                      (e! (tiedot/->PoistaRahavaraus rivi)))
                                   {:luokka "napiton-nappi"}])}
             [
              ;; Muokkausgridi ei toimi default checkboxin kanssa. Se ei saa on-rivi-blur toimintaan checkboxin oikeaan arvoa
              ;; Joten tehdään oma komponentti, jossa ohitetaan on-rivi-blur toiminta ihan erillisellä kutsulla
              {:otsikko "" :nimi :valittu? :tyyppi :komponentti :leveys 1
               :komponentti (fn [rivi]
                              (let [id (gensym "rahavaraus")]
                                [:span.rahavaraus-valinta
                                 [:input.vayla-checkbox
                                  {:type :checkbox
                                   :id id
                                   :checked (boolean (:valittu? rivi))
                                   :on-change #(do
                                                 (.preventDefault %)
                                                 (.stopPropagation %)
                                                 (e! (tiedot/->ValitseUrakanRahavaraus valittu-urakka rivi
                                                       (-> % .-target .-checked))))}]
                                 [:label {:for id} "" #_(:nimi rivi)]]))}
              {:otsikko "Rahavaraus" :nimi :nimi :tyyppi :string :leveys 10}
              {:otsikko "Urakkakohtainen nimi" :nimi :urakkakohtainen-nimi :tyyppi :string :leveys 10}]
             rahavaraukset-atom]])]))))

(defn rahavaraukset []
  [tuck tiedot/tila rahavaraukset*])
