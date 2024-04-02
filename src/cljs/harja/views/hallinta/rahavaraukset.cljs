(ns harja.views.hallinta.rahavaraukset
  (:require [clojure.string :as str]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.yleiset :as yleiset]
            [reagent.core :as r]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.hallinta.rahavaraukset :as tiedot])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn rahavaraukset* [e! _app]
  (komp/luo
    (komp/sisaan #(do (e! (tiedot/->HaeRahavaraukset))
                    (e! (tiedot/->HaeUrakoidenRahavaraukset))))
    (fn [e! {:keys [valittu-urakka urakat urakoiden-rahavaraukset rahavaraukset tallennukset-kesken] :as app}]
      (let [valitun-urakan-rahavaraukset (filter #(= (:urakka-id %) (:urakka-id valittu-urakka))
                                           urakoiden-rahavaraukset)]
        [:div.rahavaraukset-hallinta
         [harja.ui.debug/debug app]
         [:h1 "Rahavaraukset"]
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
               ;; FIXME: Joskus valinnan poistaminen epäonnistuu
               [:input.vayla-checkbox
                {:type :checkbox
                 :id id
                 :checked valittu?
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
               [:span "Tallennetaan..."] ;; todo: lisää ajax loader pieni
               [:span "Muutokset tallennettu " (ikonit/harja-icon-status-completed)])])]]))))

(defn rahavaraukset []
  [tuck tiedot/tila rahavaraukset*])
