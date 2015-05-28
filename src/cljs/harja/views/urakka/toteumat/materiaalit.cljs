(ns harja.views.urakka.toteumat.materiaalit
  (:require [harja.views.urakka.valinnat :as valinnat]
            [reagent.core :refer [atom]]
            [harja.loki :refer [log]]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as u]
            [harja.ui.grid :as grid]

            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.materiaalit :as materiaali-tiedot]

            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce valittu-materiaalin-kaytto (atom nil))

(def urakan-materiaalin-kaytot
  (reaction<! (when-let [ur @nav/valittu-urakka]
                (toteumat/hae-urakassa-kaytetyt-materiaalit (:id ur)))))

(defn tallenna-toteuma-ja-toteumamateriaalit!
  [tm m]
  (log "RAAKA TM: "(pr-str tm))
  (log "RAAKA T: "(pr-str m))
  (let [toteumamateriaalit (into []
                                 (comp
                                   (map #(assoc % :materiaalikoodi (:id (:materiaali %))))
                                   (map #(dissoc % :materiaali))
                                   (map #(assoc % :maara (if (string? (:maara %))
                                                           (js/parseInt (:maara %) 10)
                                                           (:maara %))))
                                   (map #(assoc % :id (:tmid %)))
                                   (map #(dissoc % :tmid))
                                   (map #(assoc % :toteuma (:id m))))
                                 tm)
        toteuma {:id (:id m) :urakka (:id @nav/valittu-urakka)
                 :alkanut (:alkanut m) :paattynyt (:paattynyt m)
                 :sopimus (first @u/valittu-sopimusnumero) :tyyppi nil}] ;fixme hard-koodattu tyyppi
    (log "KÄSITELTY TM: " (pr-str toteumamateriaalit))
    (log "KÄSITELTY T: " (pr-str toteuma))
    (toteumat/tallenna-toteuma-ja-toteumamateriaalit! toteuma toteumamateriaalit)))

(def materiaalikoodit (reaction (into []
                            (comp
                              (map #(dissoc % :urakkatyyppi))
                              (map #(dissoc % :kohdistettava)))
                            @(materiaali-tiedot/hae-materiaalikoodit))))

#_(def materiaalikoodit (->>
                        @(materiaali-tiedot/hae-materiaalikoodit)
                        (map #(dissoc % :urakkatyyppi))
                        (map #(dissoc % :kohdistettava))))


(defn materiaalit-ja-maarat
  [materiaalit-atom]


  (log "Materiaalit-ja-maarat, tiedot: " (pr-str @materiaalit-atom))
  (log "Materiaalikoodit:" (pr-str @materiaalikoodit))

  [grid/muokkaus-grid
   {:tyhja "Ei materiaaleja."}

   [{:otsikko "Materiaali" :nimi :materiaali :tyyppi :valinta
     :valinnat @materiaalikoodit
     :valinta-nayta #(if % (:nimi %) "- valitse materiaali -")
     :validoi [[:ei-tyhja "Valitse materiaali."]]
     :leveys "50%"}

    {:otsikko "Määrä" :nimi :maara :tyyppi :string :leveys "40%"}
    {:otsikko "Yks." :muokattava? (constantly false) :nimi :yksikko :hae (comp :yksikko :materiaali) :leveys "5%"}]
   materiaalit-atom])

(defn materiaalit-tiedot
  [ur]
  "Valitun toteuman tietojen näkymä"
  [ur]
  (let [tiedot (atom nil)
        muokattu (reaction @tiedot)
        ;tallennus-kaynnissa (atom false)
        materiaalitoteumat-mapissa (reaction (into {} (map (juxt :tmid identity) (:toteumamateriaalit @muokattu))))
        uusi-toteuma? (if (:id @valittu-materiaalin-kaytto) true false)]

    (komp/luo
      {:component-will-mount
       (fn [_]
         (log "MATERIAALIT-TIEDOT WILL MOUNT")
         (log (pr-str @valittu-materiaalin-kaytto))
         (when (:id @valittu-materiaalin-kaytto)
           (go
             (reset! tiedot
                     (<!(materiaali-tiedot/hae-toteuman-materiaalitiedot (:id ur) (:id @valittu-materiaalin-kaytto)))))))}

      (fn [ur]
        (log "Lomake, muokattu: " (pr-str @muokattu))
        (log "Uusi toteuma?" uusi-toteuma?)
        [:div.toteuman-tiedot
         [:button.nappi-toissijainen {:on-click #(reset! valittu-materiaalin-kaytto nil)}
          (ikonit/chevron-left) " Takaisin materiaaliluetteloon"]
         (if uusi-toteuma?
           [:h3 "Muokkaa toteumaa"]
           [:h3 "Luo uusi toteuma"])

         [lomake {:luokka   :horizontal
                  :muokkaa! (fn [uusi]
                              (log "MUOKATAAN " (pr-str uusi))
                              (reset! muokattu uusi))
                  :footer   [harja.ui.napit/palvelinkutsu-nappi
                             "Tallenna toteuma"
                             #(tallenna-toteuma-ja-toteumamateriaalit! (vals @materiaalitoteumat-mapissa) @muokattu)
                             {:luokka :nappi-ensisijainen}
                             #(do
                               (reset! urakan-materiaalin-kaytot %)
                               (reset! valittu-materiaalin-kaytto nil))]
                            #_[:button.nappi-ensisijainen
                             {:class (when @tallennus-kaynnissa "disabled")
                              :on-click #(do (.preventDefault %)
                                          (reset! tallennus-kaynnissa true)
                                          (go (let
                                                [res (<! (tallenna-toteuma-ja-toteumamateriaalit!
                                                           (vals @materiaalitoteumat-mapissa)
                                                           @muokattu))]
                                                (if res
                                                  ;; Tallennus ok
                                                  (do
                                                    (reset! tallennus-kaynnissa false)
                                                    (viesti/nayta! "Toteuma tallennettu")
                                                    (reset! valittu-materiaali-toteuma nil)

                                                  ;; Epäonnistui jostain syystä
                                                  (reset! tallennus-kaynnissa false))))))}
                             "Tallenna toteuma"]}

          [{:otsikko "Sopimus" :nimi :sopimus :hae (fn [_] (second @u/valittu-sopimusnumero)) :muokattava? (constantly false)}

           {:otsikko "Hoitokausi" :nimi :hoitokausi :hae (fn [_]
                                                           (let [[alku loppu] @u/valittu-hoitokausi]
                                                             [:span (pvm/pvm alku) " \u2014 " (pvm/pvm loppu)]))
            :fmt identity
            :muokattava? (constantly false)}

           {:otsikko "Aloitus" :tyyppi :pvm :nimi :alkanut
            :muokattava? (constantly (not uusi-toteuma?)) :leveys "30%"} ;fixme päivämääräkenttä fukkaa kun muokattava?=false
           {:otsikko "Lopetus" :tyyppi :pvm :nimi :paattynyt
            :muokattava? (constantly (not uusi-toteuma?)) :leveys "30%"}
           {:otsikko "Materiaalit" :nimi :materiaalit :komponentti [materiaalit-ja-maarat materiaalitoteumat-mapissa]}]

          @muokattu]]))))

(defn materiaalinkaytto-vetolaatikko
  [urakan-id mk]
  (let [tiedot (atom nil)]
    (komp/luo
      {:component-will-mount
       (fn [_]
         ;(log "COMPONENT WILL MOUNT")
         (go
           (reset! tiedot
                   (<!(materiaali-tiedot/hae-toteumat-materiaalille urakan-id (:id (:materiaali mk)))))))}

      (fn [urakan-id vm]
        (log "Vetolaatikko tiedot:" (pr-str @tiedot))
        {:key (:id vm)}
        [:span
         [grid/grid
          {:otsikko (str (get-in mk [:materiaali :nimi]) " toteumat")
           :tyhja   (if (nil? @tiedot) [ajax-loader "Ladataan toteumia"] "Ei toteumia")
           :rivi-klikattu #(reset! valittu-materiaalin-kaytto %)}

          [{:otsikko "Aloitus" :tyyppi :pvm :nimi :aloitus :hae (comp pvm/pvm :alkanut :toteuma) :leveys "30%"}
           {:otsikko "Lopetus" :tyyppi :pvm :nimi :lopetus :hae (comp pvm/pvm :paattynyt :toteuma) :leveys "30%"}
           {:otsikko "Määrä" :nimi :toteuman_maara :hae (comp :maara :toteuma) :leveys "40%"}]
          @tiedot]]))))

(defn materiaalit-paasivu
  [ur]
  (log "Paasivu, urakan-materiaalin-kaytot:" (pr-str @urakan-materiaalin-kaytot))
  [:span
   [valinnat/urakan-sopimus-ja-hoitokausi ur]
   [:button.nappi-ensisijainen {:on-click #(reset! valittu-materiaalin-kaytto {})}
    (ikonit/plus-sign) " Lisää toteuma"]
   [grid/grid
    {:otsikko        "Suunnitellut ja toteutuneet materiaalit"
     :tyhja          (if (nil? @urakan-materiaalin-kaytot) [ajax-loader "Toteuman materiaaleja haetaan."] "Ei löytyneitä tietoja.")
     :tunniste #(:id (:materiaali %))
     :vetolaatikot
      (into {}
            (map (juxt (comp :id :materiaali) (fn [mk] [materiaalinkaytto-vetolaatikko (:id ur) mk])))
            @urakan-materiaalin-kaytot)
     }

    ;; sarakkeet
    [{:tyyppi :vetolaatikon-tila :leveys "5%"}
     {:otsikko "Nimi" :nimi :materiaali_nimi :hae (comp :nimi :materiaali) :leveys "50%"}
     {:otsikko "Yksikkö" :nimi :materiaali_yksikko :hae (comp :yksikko :materiaali) :leveys "10%"}
     {:otsikko "Suunniteltu määrä" :nimi :sovittu_maara :hae :maara :leveys "20%"}
     {:otsikko "Käytetty määrä" :nimi :toteutunut_maara :hae :kokonaismaara :leveys "20%"}
     ]

    @urakan-materiaalin-kaytot]])

(defn materiaalit-nakyma
  [ur]
  (if @valittu-materiaalin-kaytto
    [materiaalit-tiedot ur]
    [materiaalit-paasivu ur]))