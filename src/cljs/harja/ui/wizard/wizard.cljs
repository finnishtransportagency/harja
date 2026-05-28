(ns harja.ui.wizard.wizard
  "wizzard :-D"
  (:require [tuck.core :as tuck]
            [harja.ui.ikonit :refer [status-completed-svg]]))


(defrecord WizardAskel [id])
(defrecord WizardValmis [id])


(defn wizard-content [e! app steps aktiivinen]
  (let [aktiivinen (or aktiivinen (:id (first steps)))
        View (:view (some #(when (= (:id %) aktiivinen) %) steps))]
    [:div.wizard-content (when View [View e! app])]))


(defn wizard-sidebar [e! steps {:keys [active completed]}]
  [:div.sidebar
   (for [[idx {:keys [id title]}] (map-indexed vector steps)]
     (let [active? (= id active)
           done? (contains? completed id)]
       ^{:key id}
       [:div.step {:class (str
                            (when active? " active")
                            (when done? " done"))
                   ;; Vaihda näkymää kun käyttäjä painaa hiirellä
                   :on-click #(e! (->WizardAskel id))}

        ;; Näytä joko askeleen numero keskellä, tai completed ikoni
        [:div.step-card. (if done? (status-completed-svg) (inc idx))]

        ;; Otsikko joka on kortin oikealla puolella
        [:div.step-title title]]))])


(defn wizard [e! app steps]
  (let [wizard-state (:wizard app)
        active (or (:active wizard-state) (:id (first steps)))]
    [:div.wizard
     [wizard-sidebar e! steps (assoc wizard-state :active active)]
     [wizard-content e! app steps active]]))


(extend-protocol tuck/Event
  WizardAskel
  (process-event [{:keys [id]} app]
    (assoc-in app [:wizard :active] id))

  WizardValmis
  (process-event [{:keys [id]} app]
    (update-in app [:wizard :completed] (fnil conj #{}) id)))
