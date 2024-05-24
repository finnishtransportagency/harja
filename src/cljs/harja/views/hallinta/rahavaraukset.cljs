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

         [:div.urakan-rahavaraukset
          (for* [rahavaraus rahavaraukset]
            (let [id (gensym "rahavaraus")
                  valittu? (some #(= (:id %) (:id rahavaraus)) valitun-urakan-rahavaraukset)]
              [:span.rahavaraus-valinta
               [:input.vayla-checkbox
                {:type :checkbox
                 :id id
                 :checked (boolean valittu?)
                 :on-change #(do
                               (.preventDefault %)
                               (.stopPropagation %)
                               (e! (tiedot/->ValitseUrakanRahavaraus
                                     valittu-urakka
                                     rahavaraus
                                     (-> % .-target .-checked))))}]
               [:label {:for id} (:nimi rahavaraus)]]))

          (when-not (empty? tallennukset-kesken)
            [:div.tallennus-tila.margin-top-16
             (if (some true? (vals tallennukset-kesken))
               [:span "Tallennetaan..." [yleiset/ajax-loader-pieni]]
               [:span "Muutokset tallennettu " [ikonit/harja-icon-status-completed]])])]]))))

(defn rahavaraukset []
  [tuck tiedot/tila rahavaraukset*])
