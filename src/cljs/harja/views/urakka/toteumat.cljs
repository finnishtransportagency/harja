(ns harja.views.urakka.toteumat
  "Urakan 'Toteumat' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.istunto :as istunto]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.toteumat.lampotilat :refer [lampotilat]]
            
            [harja.ui.visualisointi :as vis]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))


;; Tällä hetkellä valittu toteuma
(defonce valittu-toteuma (atom nil))

(defn tyot-ja-materiaalit-paasivu [ur]
  (let [toteumat (atom nil)
        urakka (atom nil)
        toteuma-paivat (atom nil)
        valittu-aikavali (atom nil)
        hover-aikavali (atom nil)
        aseta-urakka (fn [ur]
                       (reset! urakka ur))
        paivia 7 ;; päivä slicen koko, tällä on suora vaikutus serveriltä haettavan datan määrään
        ]
    (aseta-urakka ur)

    ;; Haetaan sopimuksen/hoitokauden päivät, joille on toteumia, aikajanaa varten
    (run! (let [urakka-id (:id @urakka)
                [sopimus-id _] @s/valittu-sopimusnumero
                hk @s/valittu-hoitokausi]
            (when (and urakka-id sopimus-id hk)
              (go (reset! toteuma-paivat (<! (toteumat/hae-urakan-toteuma-paivat urakka-id sopimus-id hk)))))))

    ;; Kun aikajanasta valitaan aika, haetaan sille välille toteumat
    (run! (let [urakka-id (:id @urakka)
                [sopimus-id _] @s/valittu-sopimusnumero
                aikavali @valittu-aikavali]
            (when (and urakka-id sopimus-id aikavali)
              (go (reset! toteumat (<! (toteumat/hae-urakan-toteumat urakka-id sopimus-id aikavali)))))))
    
    (komp/luo
      {:component-will-receive-props
       (fn [_ & [_ ur]]
         (log "UUSI URAKKA: " (pr-str (dissoc ur :alue)))
         (aseta-urakka ur))}

      (fn [ur]
          [:div.toteumat
           [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide ur]
           [:button.nappi-ensisijainen {:on-click #(reset! valittu-toteuma {:olio "on"})}
            (ikonit/plus-sign) " Lisää toteuma"]

           [:div.aikajana
            (when @s/valittu-hoitokausi
              [vis/timeline {:width 1000 :height 60
                             :slice @valittu-aikavali
                             :hover @hover-aikavali
                             :range @s/valittu-hoitokausi
                             :on-hover (fn [date]
                                         (reset! hover-aikavali
                                                 (when date
                                                   [date (t/plus date (t/days paivia))])))
                             :on-click (fn [date]
                                         (log "klikattiin: " date)
                                         (let [plus-minus (/ (- paivia 1) 2)
                                               alku (t/minus date (t/days plus-minus))
                                               [hk-alku hk-loppu] @s/valittu-hoitokausi

                                               ;; Tarkista ettei alku mene hoitokauden alkua ennen
                                               alku (if (t/before? alku hk-alku)
                                                      hk-alku
                                                      alku)
                                               loppu (t/plus alku (t/days paivia))

                                               ;; Tarkista ettei loppu mene hoitokauden lopun jälkeen
                                               [alku loppu] (if (t/after? loppu hk-loppu)
                                                              [(t/minus hk-loppu (t/days paivia)) hk-loppu]
                                                              [alku loppu])]
                                           (reset! valittu-aikavali [alku loppu])))}
               (or @toteuma-paivat #{})
               ])]

           (when @valittu-aikavali
             (let [[alkupvm loppupvm] @valittu-aikavali]
               [:div.toteumat-aikavalilla
                [grid/grid
                 {:otsikko (str "Toteumat aikavälillä " (pvm/pvm alkupvm) " \u2014 " (pvm/pvm loppupvm))
                  :tyhja (if (nil? @toteumat)
                           [ajax-loader "Toteumatietoja haetaan..."]
                           "Ei toteumatietoja aikavälille")}
                 [{:otsikko "Alkanut" :fmt pvm/pvm-aika :nimi :alkanut :leveys "15%"}
                  {:otsikko "Päättynyt" :fmt pvm/pvm-aika :nimi :paattynyt :leveys "15%"}
                  {:otsikko "Tehtävä" :fmt #(str/join ", " %) :nimi :tehtavat :leveys "25%"}
                  {:otsikko "Materiaali" :fmt #(str/join ", " %) :nimi :materiaalit :leveys "25%"}
                  {:otsikko "Tyyppi" :nimi :tyyppi :leveys "20%"}]

                 @toteumat]]))


         ]))))

(defn tyot-ja-materiaalit-tiedot
  "Valitun toteuman tietojen näkymä"
  [ur vt]
  (komp/luo
    {:component-will-receive-props
     (fn [_ & [_ ur vt]]
       (log "Toteuma valittu: " (pr-str @valittu-toteuma))
       )}

    (fn [ur]
      [:div.toteuman-tiedot
       [:button.nappi-toissijainen {:on-click #(reset! valittu-toteuma nil)}
        (ikonit/chevron-left) " Takaisin käyttäjäluetteloon"]
       (if-not (:id @valittu-toteuma)
         [:h3 "Luo uusi toteuma"])
       ])))

(defn tyot-ja-materiaalit [ur]
  (if-let [vt @valittu-toteuma]
    [tyot-ja-materiaalit-tiedot ur]
    [tyot-ja-materiaalit-paasivu ur]))




(defonce toteumat-valilehti (atom 0))


(defn toteumat
  "Toteumien pääkomponentti"
  [ur]
  [bs/tabs {:active toteumat-valilehti}

   "Työt ja materiaalit"
   [tyot-ja-materiaalit ur] ;; FIXME: siirrä työt ja materiaalit omaan namespaceen

   "Hinnantarkistukset"
   [:div "hinnantarkistukset tänne"]
   
   "Lämpötilat"
   [lampotilat ur]])

