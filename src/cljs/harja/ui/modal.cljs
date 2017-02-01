(ns harja.ui.modal
  "Modaali näyttökomponentti. Näitä yksi kappale päätasolle."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.dom :as dom]
            [harja.asiakas.tapahtumat :as t]))

(defn- avaa-modal-linkki
  "Jostain merkillisestä syystä modalissa esiintyvä <a> linkki ei toimi oikein, joten
   avataan linkki ohjelmallisesti uuteen ikkunaan / välilehteen."
  [linkki target]
  (.open js/window linkki target))

(defn modal-linkki
  "Jostain merkillisestä syystä modalissa esiintyvä <a> linkki ei toimi oikein, joten
 avataan linkki ohjelmallisesti uuteen ikkunaan / välilehteen."
  ([teksti osoite] (modal-linkki teksti osoite "_self"))
  ([teksti osoite target]
   [:a {:href osoite
        :target target
        :on-click #(avaa-modal-linkki osoite target)}
    teksti]))

(def modal-sisalto (atom {:otsikko nil
                          :sisalto nil
                          :footer nil
                          :luokka nil
                          :sulje nil
                          :nakyvissa? false}))

(defn piilota! []
  (when (:sulje @modal-sisalto) ((:sulje @modal-sisalto)))
  (swap! modal-sisalto assoc :nakyvissa? false))

(defn- modal-container* [optiot sisalto]
  (let [{:keys [otsikko footer nakyvissa? luokka leveys]} optiot]
    (if nakyvissa?
      ^{:key "modaali"}
      [:div.modal.fade.in.harja-modal {:style {:display "block"}
                                       :on-click piilota!}
       [:div.modal-backdrop.fade.in {:style {:height @dom/korkeus :z-index -1}}]
       [:div (merge {:class (str "modal-dialog modal-sm " (or luokka ""))}
                    (when leveys
                      {:style {:max-width leveys}}))
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

(defn modal-container
  "Tämä komponentti sisältää modaalin ja on tarkoitus laittaa päätason sivuun"
  []
  (let [optiot-ja-sisalto @modal-sisalto]
    [modal-container* optiot-ja-sisalto (:sisalto optiot-ja-sisalto)]))

(defn nayta! [{:keys [sulje otsikko footer luokka leveys]} sisalto]
  (reset! modal-sisalto {:otsikko otsikko
                         :footer footer
                         :sisalto sisalto
                         :luokka luokka
                         :sulje sulje
                         :nakyvissa? true
                         :leveys leveys}))

(defn aloita-urln-kuuntelu []
  (t/kuuntele! :url-muuttui
               (fn [_]
                 (piilota!))))

(defn modal [optiot sisalto]
  [modal-container* optiot sisalto])
