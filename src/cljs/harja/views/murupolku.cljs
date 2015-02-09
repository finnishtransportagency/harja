(ns harja.views.murupolku
  "Murupolku on sovelluksenlaajuinen navigaatiokomponentti.
  Sen avulla voidaan vaikuttaa sovelluksen tilaan muun muassa
  seuraavia parametrejä käyttäen: väylämuoto, hallintayksikkö,
  urakka, urakan tyyppi, urakoitsija."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? alasvetovalinta radiovalinta]]

            [harja.loki :refer [log]]
            [harja.tiedot.urakoitsijat :as urakoitsijat]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav])
            )


(comment urakkatyyppi-kontrollit []
  (let [ut @nav/valittu-urakkatyyppi 
        vaihda-tyyppi (fn [e] (nav/vaihda-urakkatyyppi! (case (.-value (.-target e))
                                                       "hoito" :hoito 
                                                       "yllapito" :yllapito)))]
       
       [:div.btn-group.pull-right.murupolku-suodatus
        [:span.pull-left "Urakkatyyppi "]
         [:label.btn.btn-primary
          [:input {:type "radio" :value "hoito" :on-change vaihda-tyyppi 
                   :checked (if (= ut :hoito) true false)} " Hoito"]]
         [:label.btn.btn-primary
          [:input {:type "radio" :value "yllapito" :on-change vaihda-tyyppi 
                   :checked (if (= ut :yllapito) true false)} " Ylläpito"]]]))

(comment kartan-koko-kontrollit []
  (let [koko @nav/kartan-koko 
        vaihda-koko (fn [e] (nav/vaihda-kartan-koko! (case (.-value (.-target e))
                                                       "hidden" :hidden 
                                                       "S" :S 
                                                       "M" :M 
                                                       "L" :L)))]
       
       [:div.btn-group.pull-right.murupolku-suodatus
        [:span.pull-left "Kartan koko "]
         [:label.btn.btn-primary
          [:input {:type "radio" :value "hidden" :on-change vaihda-koko :checked (if (= koko :hidden) true false)} " Piilota"]]
         [:label.btn.btn-primary
          [:input {:type "radio" :value "S" :on-change vaihda-koko :checked (if (= koko :S) true false)} " S"]]
         [:label.btn.btn-primary
          [:input {:type "radio" :value "M" :on-change vaihda-koko :checked (if (= koko :M) true false)} " M"]]
         [:label.btn.btn-primary
          [:input {:type "radio" :value "L" :on-change vaihda-koko :checked (if (= koko :L) true false) } " L"]]]))

(defn murupolku
  "Itse murupolkukomponentti joka sisältää html:n"
  []
  (kuuntelija
   {:valinta-auki (atom nil) ;; nil | :hallintayksikko | :urakka
    }
   

   (fn [this]
     (let [valinta-auki (:valinta-auki (reagent/state this))
           urakoitsija @nav/valittu-urakoitsija
           urakoitsijat @urakoitsijat/urakoitsijat]
       [:span
        [:ol.breadcrumb
        [:li [linkki "Koko maa" #(nav/valitse-hallintayksikko nil)]]
        (when-let [valittu @nav/valittu-hallintayksikko]
          [:li.dropdown {:class (when (= :hallintayksikko @valinta-auki) "open")}

           (let [vu @nav/valittu-urakka
                 va @valinta-auki]
             (if (or (not (nil? vu))
                     (= va :hallintayksikko))
               [linkki (str (:nimi valittu) " ") #(nav/valitse-hallintayksikko valittu)
                ]
               [:span.valittu-hallintayksikko (:nimi valittu) " "]))
           
           [:button.btn.btn-default.btn-xs.dropdown-toggle {:on-click #(swap! valinta-auki
                                                                                        (fn [v]
                                                                                          (if (= v :hallintayksikko)
                                                                                            nil
                                                                                            :hallintayksikko)))}
            [:span.caret]]
                      
           ;; Alasvetovalikko yksikön nopeaa vaihtamista varten
           [:ul.dropdown-menu {:role "menu"}
            (for [muu-yksikko (filter #(not= % valittu) @hal/hallintayksikot)]
              ^{:key (str "hy-" (:id muu-yksikko))}
              [:li [linkki (:nimi muu-yksikko) #(do (reset! valinta-auki nil)
                                                 (nav/valitse-hallintayksikko muu-yksikko)) ]])]])
        (when-let [valittu @nav/valittu-urakka]
          [:li.dropdown {:class (when (= :urakka @valinta-auki) "open")}
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
              [:li [linkki (:nimi muu-urakka) #(nav/valitse-urakka muu-urakka)]])]])
        
        [:span.pull-right
         ;;(log "urakoitsijat " (pr-str urakoitsijat) nil)
         [alasvetovalinta urakoitsija
          #(do (log "%" % )(if % (:nimi %) "Kaikki"))
          nav/valitse-urakoitsija!
          urakoitsijat]
         
         [radiovalinta "Kartan koko" @nav/kartan-koko nav/vaihda-kartan-koko!
          "Piilota" :hidden "S" :S "M" :M "L" :L]
         [radiovalinta "Urakkatyyppi" @nav/valittu-urakkatyyppi nav/vaihda-urakkatyyppi!
          "Hoito" :hoito "Ylläpito" :yllapito]]]
        ]))

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