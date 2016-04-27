(ns harja.ui.modal
  "Modaali näyttökomponentti. Näitä yksi kappale päätasolle."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.dom :as dom]))

(def modal-sisalto (atom {:otsikko nil
                          :sisalto nil
                          :footer nil
                          :luokka nil
                          :sulje nil
                          :nakyvissa? false}))

(defn piilota! []
  (when (:sulje @modal-sisalto) ((:sulje @modal-sisalto)))
  (swap! modal-sisalto assoc :nakyvissa? false))

;;(def ctg (r/adapt-react-class (-> js/React  (aget "addons") (aget "CSSTransitionGroup"))))


(defn modal-container
  "Tämä komponentti sisältää modaalin ja on tarkoitus laittaa päätason sivuun"
  []
  (let [{:keys [otsikko sisalto footer nakyvissa? luokka leveys]} @modal-sisalto]
    (if nakyvissa?
      ^{:key "modaali"}
      [:div.modal.fade.in.harja-modal {:style {:display "block"}
                                       :on-click piilota!}
       [:div.modal-backdrop.fade.in {:style {:height @dom/korkeus :z-index -1}}]
       [:div {:class (str "modal-dialog modal-sm " (or luokka ""))}
        [:div.modal-content {:on-click #(do
                                         (.preventDefault %)
                                         (.stopPropagation %)
                                         piilota!)}
         (when otsikko
           [:div.modal-header
            [:button.close {:on-click piilota!
                            :type "button" :data-dismiss "modal" :aria-label "Sulje"}
             [:span {:aria-hidden "true"} "×"]]
            [:h4.modal-title otsikko]])
         [:div.modal-body sisalto]
         (when footer [:div.modal-footer footer])]]]

      ^{:key "ei-modaalia"}
      [:span.modaali-ei-nakyvissa])))




(defn modal [{:keys [sulje otsikko footer luokka]} sisalto]
  (.log js/console "NÄYTETÄÄN: " sisalto)
  (reset! modal-sisalto {:otsikko otsikko
                         :footer footer
                         :sisalto sisalto
                         :luokka luokka
                         :sulje sulje
                         :nakyvissa? true})
  (fn [sulje]
    (if-not (:nakyvissa? @modal-sisalto)
      (do (sulje)
          [:span.modaali-ei-nakyvissa])
      [:span.modaali-nakyvissa])))

(defn nayta! [{:keys [sulje otsikko footer luokka leveys]} sisalto]
  (reset! modal-sisalto {:otsikko otsikko
                         :footer footer
                         :sisalto sisalto
                         :luokka luokka
                         :sulje sulje
                         :nakyvissa? true
                         :leveys leveys}))