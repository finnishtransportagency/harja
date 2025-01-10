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

(defonce edellinen-fokusoitu-elementti (r/atom nil)) 

(def modal-sisalto (atom {:otsikko nil
                          :otsikon-alle-komp nil
                          :sisalto nil
                          :footer nil
                          :luokka nil
                          :sulje nil
                          :nakyvissa? false
                          :modaalin-fokus-elementti nil}))

(defn nakyvissa? []
  (boolean (:nakyvissa? @modal-sisalto)))

(defn piilota! []
  (when (:sulje @modal-sisalto) ((:sulje @modal-sisalto)))
  (swap! modal-sisalto assoc :nakyvissa? false))

(defn- modal-container* [{:keys [modaalin-fokus-elementti]}]
  (let [modal-ref (r/atom nil)
        focusable-elementit (r/atom nil)
        siirra-fokus (fn [modal-nakyma fokus-elementti]
                        (when (and modal-nakyma fokus-elementti)
                          (dom/siirra-fokus-nakymaan modal-nakyma fokus-elementti)))]
    (r/create-class
      {:component-did-update (fn [this old-argv]
                               ;; Saavutettavuuden kannalta on tärkeää, että fokus pysyy modaalin sisällä ja palautetaan suljettaessa
                               ;; Saavutettava modaali toimii nykyisellään kun nakyvissa? atomiin tulee muutos mutta ei jos komponentti piilotettaan domista
                               (let [new-argv (rest (r/argv this))
                                     modal-nakyma @modal-ref
                                     nakyvissa-paivittyi (not= (:nakyvissa? (first new-argv)) (:nakyvissa? (second old-argv)))
                                     modaali-suljettu? (and (false? (:nakyvissa? (first new-argv))) (true? (:nakyvissa? (second old-argv))))] 
                                 (when (and nakyvissa-paivittyi modal-nakyma (not modaali-suljettu?)) ;; Varmistetaan että esim. input kenttään kirjoittaminen ei aiheuta fokuksen siirtymistä
                                   (reset! edellinen-fokusoitu-elementti (.-activeElement js/document)) ;; Otetaan talteen ennen modaalia fokusoitu elementti
                                   (if modaalin-fokus-elementti
                                     (siirra-fokus modal-nakyma modaalin-fokus-elementti)
                                     (siirra-fokus modal-nakyma :first)))
                                 (when modaali-suljettu? ;; Jos modaali suljettu, palautetaan fokus edelliseen elementtiin
                                   (when @edellinen-fokusoitu-elementti
                                     (.focus @edellinen-fokusoitu-elementti)))
                                 (dom/tee-fokus-ansa modal-ref)))
       :reagent-render
       (fn [{:keys [otsikko otsikon-alle-komp otsikko-tyyli footer nakyvissa? luokka
                    leveys content-tyyli body-tyyli modal-luokka sulje-fn sulje-ruksista-fn modaalin-fokus-elementti]} sisalto]
         (let [sulje!  #(do
                          ;; estää file-open dialogin poistamisen
                          #_(.preventDefault %)
                          (.stopPropagation %)
                          (when sulje-fn
                            (sulje-fn))
                          (piilota!))
               sulje-ruksista-fn-fokuksella (fn []
                                              (when @edellinen-fokusoitu-elementti
                                                (.focus @edellinen-fokusoitu-elementti))
                                              (when sulje-ruksista-fn
                                                (sulje-ruksista-fn))) 
               sulje-ruksista! (or (when sulje-ruksista-fn sulje-ruksista-fn-fokuksella) sulje!)]
           (if nakyvissa?
             ^{:key "modaali"}
             [:div.modal.fade.in.harja-modal {:class modal-luokka
                                              :on-click sulje!}
              [:div.modal-backdrop.fade.in {:style {:height @dom/korkeus :z-index -1}}]
              [:div (merge {:class (str "modal-dialog modal-sm " (or luokka ""))}
                      (when leveys
                        {:style {:max-width leveys}}))
               [:div.modal-content {:on-click #(.stopPropagation %)
                                    :style content-tyyli
                                    :tab-index "0"
                                    :ref #(reset! modal-ref %)}
                (when otsikko
                  [:div.modal-header
                   [ikonit/sulje-ruksi sulje-ruksista! {:style {:margin 0}}]
                   [:h2.modal-title {:id "modal-otsikko"
                                     :class (when (= otsikko-tyyli :virhe)
                                              "modal-otsikko-virhe")}
                    otsikko]
                   (when otsikon-alle-komp
                     [otsikon-alle-komp])])
                [:div.modal-body {:style body-tyyli} sisalto]
                (when footer [:div.modal-footer footer])]]]
             ^{:key "ei-modaalia"}
             [:span.modaali-ei-nakyvissa])))})))

(defn modal-container
  "Tämä komponentti sisältää modaalin ja on tarkoitus laittaa päätason sivuun"
  []
  (let [optiot-ja-sisalto @modal-sisalto]
    [modal-container* optiot-ja-sisalto (:sisalto optiot-ja-sisalto)]))

(defn nayta! [{:keys [sulje otsikko otsikon-alle-komp sulje-ruksista-fn sulje-fn otsikko-tyyli
                      footer luokka leveys content-tyyli body-tyyli modal-luokka modaalin-fokus-elementti]} sisalto] 
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
                         :modal-luokka modal-luokka
                         :modaalin-fokus-elementti modaalin-fokus-elementti}))

(defn aloita-urln-kuuntelu []
  (t/kuuntele! :url-muuttui
    (fn [_]
      (piilota!))))

(defn modal [optiot sisalto]
  [modal-container* optiot sisalto])