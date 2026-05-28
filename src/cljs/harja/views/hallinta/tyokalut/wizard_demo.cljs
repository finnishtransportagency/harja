(ns harja.views.hallinta.tyokalut.wizard-demo
  (:require [clojure.string]
            [tuck.core :as tuck]

            [harja.ui.komponentti :as komp]
            [harja.ui.wizard.wizard :as wizard]
            [harja.tiedot.urakka.urakka :as urakka-tila]))


(def nakymassa? (atom false))
(defrecord HaeTiedot [])


(extend-protocol tuck/Event
  HaeTiedot
  (process-event [_ app]
    (assoc app :haku-kaynnissa? false)))


(defn demo* [e! _app]
  (komp/luo
    (komp/lippu nakymassa?)
    (komp/sisaan #(do
                    (e! (->HaeTiedot))
                    (e! (wizard/->WizardValmis :nakyma-3))))

    (fn [e! app]
      (let [wizard-osiot [{:id :nakyma-1
                           :title "Näkymä 1"
                           :view (fn [_e! _app] [:div "Content 1"])}

                          {:id :nakyma-2
                           :title "Näkymä 2"
                           :view (fn [_e! _app] [:div "Content 2"])}

                          {:id :nakyma-3
                           :title "Näkymä 3 completed"
                           :view (fn [_e! _app] [:div "Content 3"])}]]

        [:div.valilehti {:style {:padding "20px 0"}}
         [wizard/wizard e! app wizard-osiot]]))))


(defn wizard-demo []
  [tuck/tuck urakka-tila/hallinta-wizard demo*])
