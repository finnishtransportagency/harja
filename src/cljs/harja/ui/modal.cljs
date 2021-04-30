(ns harja.ui.modal
  "Modaali näyttökomponentti. Näitä yksi kappale päätasolle."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.dom :as dom]
            [harja.loki :refer [log logt]]
            [harja.asiakas.tapahtumat :as t]
            [harja.ui.ikonit :as ikonit]))

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
                          :otsikon-alle-komp nil
                          :sisalto nil
                          :footer nil
                          :luokka nil
                          :sulje nil
                          :nakyvissa? false}))

(defn nakyvissa? []
  (boolean (:nakyvissa? @modal-sisalto)))

(defn piilota! []
  (when (:sulje @modal-sisalto) ((:sulje @modal-sisalto)))
  (swap! modal-sisalto assoc :nakyvissa? false))

(defn- modal-container* [optiot sisalto]
  (let [{:keys [otsikko otsikon-alle-komp otsikko-tyyli footer nakyvissa? luokka
                leveys sulje-fn sulje-ruksista-fn content-tyyli body-tyyli modal-luokka]} optiot
        sulje!  #(do
                  ;; estää file-open dialogin poistamisen
                  #_(.preventDefault %)
                  (.stopPropagation %)
                  (when sulje-fn
                    (sulje-fn))
                  (piilota!))
        sulje-ruksista! (or sulje-ruksista-fn sulje!)]
    (if nakyvissa?
      ^{:key "modaali"}
      [:div.modal.fade.in.harja-modal {:class modal-luokka
                                       :on-click sulje!}
       [:div.modal-backdrop.fade.in {:style {:height @dom/korkeus :z-index -1}}]
       [:div (merge {:class (str "modal-dialog modal-sm " (or luokka ""))}
                    (when leveys
                      {:style {:max-width leveys}}))
        [:div.modal-content {:on-click #(.stopPropagation %)
                             :style content-tyyli}
         (when otsikko
           [:div.modal-header
            [ikonit/sulje-ruksi sulje-ruksista! {:style {:margin 0}}]
            [:h1.modal-title {:class (when (= otsikko-tyyli :virhe)
                                       "modal-otsikko-virhe")}
             otsikko]
            (when otsikon-alle-komp
              [otsikon-alle-komp])])
         [:hr.modal-header-body-space]
         [:div.modal-body {:style body-tyyli} sisalto]
         (when footer [:div.modal-footer footer])]]]

      ^{:key "ei-modaalia"}
      [:span.modaali-ei-nakyvissa])))

(defn modal-container
  "Tämä komponentti sisältää modaalin ja on tarkoitus laittaa päätason sivuun"
  []
  (let [optiot-ja-sisalto @modal-sisalto]
    [modal-container* optiot-ja-sisalto (:sisalto optiot-ja-sisalto)]))

(defn nayta! [{:keys [sulje otsikko otsikon-alle-komp sulje-ruksista-fn sulje-fn otsikko-tyyli
                      footer luokka leveys content-tyyli body-tyyli modal-luokka]} sisalto]
  (reset! modal-sisalto {:otsikko otsikko
                         :otsikon-alle-komp otsikon-alle-komp
                         :otsikko-tyyli otsikko-tyyli
                         :footer footer
                         :sisalto sisalto
                         :luokka luokka
                         :sulje-ruksista-fn sulje-ruksista-fn
                         :sulje-fn sulje-fn
                         :sulje sulje
                         :nakyvissa? true
                         :leveys leveys
                         :content-tyyli content-tyyli
                         :body-tyyli body-tyyli
                         :modal-luokka modal-luokka}))

(defn aloita-urln-kuuntelu []
  (t/kuuntele! :url-muuttui
               (fn [_]
                 (piilota!))))

(defn modal [optiot sisalto]
  [modal-container* optiot sisalto])