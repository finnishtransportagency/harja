(ns harja.views.murupolku
  "Murupolku on sovelluksenlaajuinen navigaatiokomponentti.
  Sen avulla voidaan vaikuttaa sovelluksen tilaan muun muassa
  seuraavia parametrejä käyttäen: väylämuoto, hallintayksikkö,
  urakka, urakan tyyppi, urakoitsija."
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? alasveto-ei-loydoksia alasvetovalinta]]

            [harja.loki :refer [log]]
            [harja.tiedot.urakoitsijat :as urakoitsijat]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.tiedot.navigaatio :as nav])
            )

(defn murupolku
  "Itse murupolkukomponentti joka sisältää html:n"
  []
  (kuuntelija
   {:valinta-auki (atom nil) ;; nil | :hallintayksikko | :urakka
    }
   
   (fn []
     (let [valinta-auki (:valinta-auki (reagent/state (reagent/current-component)))
           urakkatyyppi @nav/valittu-urakkatyyppi
           urakoitsija @nav/valittu-urakoitsija]
       [:span {:class (when (empty? @nav/tarvitsen-karttaa)
                        (cond 
                         (= @nav/sivu :hallinta) "hide"
                         (= @nav/sivu :about) "hide"
                         :default ""))}
        [:ol.breadcrumb.murupolku
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
          
             (let [muut-urakat (filter #(not= % valittu) @nav/suodatettu-urakkalista)]
            
               (if (empty? muut-urakat)
                 [alasveto-ei-loydoksia "Tästä hallintayksiköstä ei löydy muita urakoita valituilla hakukriteereillä."]
                 (for [muu-urakka muut-urakat]
                   ^{:key (str "ur-" (:id muu-urakka))}
                   [:li [linkki (:nimi muu-urakka) #(nav/valitse-urakka muu-urakka)]])))]])
      
         [:span.pull-right.murupolku-suotimet
          [:div [:span.urakoitsija-otsikko "Urakoitsija"]
           [alasvetovalinta {:valinta urakoitsija
                             :format-fn #(if % (:nimi %) "Kaikki")
                             :valitse-fn nav/valitse-urakoitsija!
                             :class "alasveto-urakoitsija"
                             :disabled (boolean @nav/valittu-urakka)}
            (vec (conj (into [] (case (:arvo urakkatyyppi)
                                 :hoito @urakoitsijat/urakoitsijat-hoito
                                 :paallystys @urakoitsijat/urakoitsijat-paallystys
                                 :tiemerkinta @urakoitsijat/urakoitsijat-tiemerkinta
                                 :valaistus @urakoitsijat/urakoitsijat-valaistus

                                 @urakoitsijat/urakoitsijat-hoito) ;;defaulttina hoito
                               ) nil))
            ]]
          [:div [:span.urakoitsija-otsikko "Urakkatyyppi"]
           [alasvetovalinta {:valinta urakkatyyppi
                             :format-fn #(if % (:nimi %) "Kaikki")
                             :valitse-fn nav/vaihda-urakkatyyppi!
                             :class "alasveto-urakkatyyppi"
                             :disabled (boolean @nav/valittu-urakka)}
            nav/+urakkatyypit+
            ]]]]]))

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
