(ns harja.views.murupolku
  "Murupolku on sovelluksenlaajuinen navigaatiokomponentti.
  Sen avulla voidaan vaikuttaa sovelluksen tilaan muun muassa
  seuraavia parametrejä käyttäen: väylämuoto, hallintayksikkö,
  urakka, urakan tyyppi, urakoitsija."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija sisalla?]]

            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav])
            )

(defn murupolku
  "Itse murupolkukomponentti joka sisältää html:n"
  []
  (kuuntelija
   {:valinta-auki (atom nil) ;; nil | :hallintayksikko | :urakka
    }
   

   (fn [this]
     (let [valinta-auki (:valinta-auki (reagent/state this))]
       [:ol.breadcrumb
        [:li [:a {:href "#" :on-click #(nav/valitse-hallintayksikko nil)}
              "Koko maa"]]
        (when-let [valittu @nav/valittu-hallintayksikko]
          [:li.dropdown {:class (when (= :hallintayksikko @valinta-auki) "open")}

           (let [vu @nav/valittu-urakka
                 va @valinta-auki]
             (if (or (not (nil? vu))
                     (= va :hallintayksikko))
               [:a {:href "#" 
                    :on-click #(nav/valitse-hallintayksikko valittu)}
                (:nimi valittu) " "]
               [:span.valittu-hallintayksikko (:nimi valittu) " "]))
           
           [:button.btn.btn-default.btn-xs.dropdown-toggle {:href "#" :on-click #(swap! valinta-auki
                                                                                        (fn [v]
                                                                                          (if (= v :hallintayksikko)
                                                                                            nil
                                                                                            :hallintayksikko)))}
            [:span.caret]]
                      
           ;; Alasvetovalikko yksikön nopeaa vaihtamista varten
           [:ul.dropdown-menu {:role "menu"}
            (for [muu-yksikko (filter #(not= % valittu) @hal/hallintayksikot)]
              ^{:key (str "hy-" (:id muu-yksikko))}
              [:li [:a {:href "#" :on-click #(do (reset! valinta-auki nil)
                                                 (nav/valitse-hallintayksikko muu-yksikko))} (:nimi muu-yksikko)]])]])
        (when-let [valittu @nav/valittu-urakka]
          [:li.dropdown {:class (when (= :urakka @valinta-auki) "open")}
           
           ;;[:a {:href "#"
           ;;       :on-click #(valitse-urakka valittu)}
           ;;   (:nimi valittu) " "]
           [:span.valittu-urakka (:nimi valittu) " "]
           
           [:button.btn.btn-default.btn-xs.dropdown-toggle {:on-click #(swap! valinta-auki
                                                                              (fn [v]
                                                                                (if (= v :urakka)
                                                                                  nil
                                                                                  :urakka)))}
            [:span.caret]]

           ;; Alasvetovalikko urakan nopeaa vaihtamista varten
           [:ul.dropdown-menu {:role "menu"}
            (for [muu-urakka (filter #(not= % valittu) @nav/urakkalista)]
              ^{:key (str "ur-" (:id muu-urakka))}
              [:li [:a {:href "#" :on-click #(nav/valitse-urakka muu-urakka)} (:nimi muu-urakka)]])]])]))

   ;; Jos hallintayksikkö tai urakka valitaan, piilota  dropdown
   [:hallintayksikko-valittu :hallintayksikkovalinta-poistettu :urakka-valittu :urakkavalinta-poistettu]
   #(reset! (-> % reagent/state :valinta-auki) nil)

   ;; Jos klikataan komponentin ulkopuolelle, vaihdetaan piilotetaan valintalistat
   :body-klikkaus
   (fn [this {klikkaus :tapahtuma}]
     (when-not (sisalla? this klikkaus)
       (let [valinta-auki (:valinta-auki (reagent/state this))]
         (reset! valinta-auki false))))
   
   ))