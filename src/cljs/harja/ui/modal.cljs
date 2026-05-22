(ns harja.ui.modal
  "Modaali näyttökomponentti. Näitä yksi kappale päätasolle."
  (:require [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [harja.ui.dom :as dom]
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

(defn- erottele-otsikon-muotoilut [otsikko-muotoilut]
  {:otsikon-tyylit (not-empty (dissoc otsikko-muotoilut :margin-bottom))
   :otsakerivin-tyylit (not-empty (select-keys otsikko-muotoilut [:margin-bottom]))})

(defn- modal-container* [{:keys [modaalin-fokus-elementti]}]
  (let [modal-ref (r/atom nil)
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
       (fn [{:keys [otsikko otsikon-alle-komp otsikko-tyyli otsikko-muotoilut footer footer-tyyli nakyvissa? luokka
                    leveys content-tyyli body-tyyli modal-luokka sulje-fn sulje-ruksista-fn ruksi-tyyli]} sisalto]
         (let [sulje!  #(do
                          (.stopPropagation %)
                          (when sulje-fn
                            (sulje-fn))
                          (piilota!))
               sulje-ruksista-fn-fokuksella (fn []
                                              (when @edellinen-fokusoitu-elementti
                                                (.focus @edellinen-fokusoitu-elementti))
                                              (when sulje-ruksista-fn
                                                (sulje-ruksista-fn))) 
               sulje-ruksista! (or (when sulje-ruksista-fn sulje-ruksista-fn-fokuksella) sulje!)
               {:keys [otsikon-tyylit otsakerivin-tyylit]} (erottele-otsikon-muotoilut otsikko-muotoilut)]
           (if nakyvissa?
             ^{:key "modaali"}
             [:div {:class (str/join " " (remove nil? ["harja-modal" modal-luokka]))
                    :data-cy "harja-modal"
                    :on-click sulje!}
              [:div.harja-modal-tausta {:style {:height @dom/korkeus}}]
              [:div (merge {:class (str/join " " (remove nil? ["harja-modal-dialogi" luokka]))}
                           (when leveys
                             {:style {:max-width leveys}}))
               [:div.harja-modal-sisalto {:on-click #(.stopPropagation %)
                                           :data-cy "harja-modal-sisalto"
                                           :style content-tyyli
                                           :tab-index "0"
                                           :role "dialog"
                                           :aria-modal "true"
                                           :aria-labelledby (when otsikko "modal-otsikko")
                                           :ref #(reset! modal-ref %)}
                (when otsikko
                  [:div.harja-modal-otsake
                   [:div.harja-modal-otsakerivi {:style otsakerivin-tyylit}
                    [:h2.harja-modal-otsikko {:id "modal-otsikko"
                                               :class (when (= otsikko-tyyli :virhe)
                                                        "harja-modal-otsikko-virhe")
                                               :style otsikon-tyylit}
                     otsikko]
                    [ikonit/sulje-ruksi sulje-ruksista! {:luokka "harja-modal-sulje"
                                                        :nayta-bootstrap-luokka? false
                                                        :style (if ruksi-tyyli (merge {:margin 0} ruksi-tyyli) {:margin 0})}]]
                   (when otsikon-alle-komp
                     [otsikon-alle-komp])])
                [:div.harja-modal-runko {:style body-tyyli} sisalto]
                (when footer [:div.harja-modal-alatunniste {:style footer-tyyli} footer])]]]
             ^{:key "ei-modaalia"}
             [:span.modaali-ei-nakyvissa])))})))

(defn modal-container
  "Tämä komponentti sisältää modaalin ja on tarkoitus laittaa päätason sivuun"
  []
  (let [optiot-ja-sisalto @modal-sisalto]
    [modal-container* optiot-ja-sisalto (:sisalto optiot-ja-sisalto)]))

(defn nayta! [{:keys [sulje otsikko otsikon-alle-komp sulje-ruksista-fn ruksi-tyyli sulje-fn otsikko-tyyli otsikko-muotoilut
                      footer footer-tyyli luokka leveys content-tyyli body-tyyli modal-luokka modaalin-fokus-elementti]} sisalto]
  (reset! modal-sisalto {:otsikko otsikko
                         :otsikon-alle-komp otsikon-alle-komp
                         :otsikko-tyyli otsikko-tyyli
                         :otsikko-muotoilut otsikko-muotoilut
                         :footer footer
                         :footer-tyyli footer-tyyli
                         :sisalto sisalto
                         :luokka luokka
                         :sulje-ruksista-fn sulje-ruksista-fn
                         :ruksi-tyyli ruksi-tyyli
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
