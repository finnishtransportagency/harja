(ns harja.ui.modal
  "Modaali näyttökomponentti. Näitä yksi kappale päätasolle."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :as yleiset]))

(def modal-sisalto (atom {:otsikko nil
                          :sisalto nil
                          :footer nil
                          :nakyvissa? false
                          }))

(defn piilota! []
  (swap! modal-sisalto assoc :nakyvissa? false))

;;(def ctg (r/adapt-react-class (-> js/React  (aget "addons") (aget "CSSTransitionGroup"))))


(defn modal-container
  "Tämä komponentti sisältää modaalin ja on tarkoitus laittaa päätason sivuun"
  []
  (let [{:keys [otsikko sisalto footer nakyvissa?]} @modal-sisalto]
    ;;[ctg {:transitionName "modal-fade"
    ;;      }
     
     (if nakyvissa?
       ^{:key "modaali"}
       [:div.modal.fade.in {:style {:display "block"}}
        [:div.modal-backdrop.fade.in {:style {:height @yleiset/korkeus :z-index -1}}]
        [:div.modal-dialog.modal-sm
         [:div.modal-content
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




(defn modal [{:keys [sulje otsikko footer]} sisalto]
  (.log js/console "NÄYTETÄÄN: " sisalto)
  (reset! modal-sisalto {:otsikko otsikko
                         :footer footer
                         :sisalto sisalto
                         :nakyvissa? true})
  (fn [{:keys [sulje]} komponentti]
    (if-not (:nakyvissa? @modal-sisalto)
      (do (sulje)
          [:span.modaali-ei-nakyvissa])
      [:span.modaali-nakyvissa])))

(defn nayta! [{:keys [sulje otsikko footer]} sisalto]
  (reset! modal-sisalto {:otsikko otsikko
                         :footer footer
                         :sisalto sisalto
                         :nakyvissa? true}))