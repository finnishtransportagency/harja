(ns harja.views.urakka.kokonaishintaiset-tyot
  "Urakan 'Kokonaishintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      alasveto-ei-loydoksia livi-pudotusvalikko radiovalinta]]
            [harja.ui.visualisointi :as vis]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.istunto :as istunto]

            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            
            [clojure.set :refer [difference]]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [cljs-time.core :as t]

            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.ui.yleiset :refer [deftk]]))


(defn indeksi [kokoelma itemi]
  (first (keep-indexed #(when (= %2 itemi) %1) kokoelma)))

(defn luo-tyhja-tyo [urakkatyyppi tpi [alkupvm loppupvm] kk sn]
  (let [tyon-kalenteri-vuosi (if-not (= :hoito urakkatyyppi)
                               (pvm/vuosi alkupvm)
                               (if (<= 10 kk 12)
                                 (pvm/vuosi alkupvm)
                                 (pvm/vuosi loppupvm)))
        kk-alku (pvm/luo-pvm tyon-kalenteri-vuosi (dec kk) 1)]
    (if-not (or (pvm/sama-kuukausi? alkupvm kk-alku)
                (pvm/ennen? alkupvm kk-alku))
      nil
      {:toimenpideinstanssi tpi, :summa nil :kuukausi kk :vuosi tyon-kalenteri-vuosi
       :maksupvm nil :alkupvm alkupvm :loppupvm loppupvm :sopimus sn})))

(defn tallenna-tyot [ur sopimusnumero valittu-hoitokausi tyot uudet-tyot tuleville?]
  (go (let [hoitokaudet (s/hoitokaudet ur)
            tallennettavat-hoitokaudet (if @tuleville?
                                         (s/tulevat-hoitokaudet ur valittu-hoitokausi)
                                         valittu-hoitokausi)
            muuttuneet
            (into [] 
                  (if @tuleville?
                    (s/rivit-tulevillekin-kausille-kok-hint-tyot ur uudet-tyot valittu-hoitokausi)
                    uudet-tyot
                    ))
            res (<! (kok-hint-tyot/tallenna-kokonaishintaiset-tyot (:id ur) sopimusnumero muuttuneet))
            res-jossa-hoitokausitieto (map #(kok-hint-tyot/aseta-hoitokausi hoitokaudet %) res)]
        (reset! tyot res-jossa-hoitokausitieto)
        (reset! tuleville? false)
        true)))

(defn ryhmittele-tehtavat
  "Ryhmittelee 4. tason tehtävät. Lisää väliotsikot eri tehtävien väliin"
  [toimenpiteet-tasoittain tyorivit]
  (let [otsikko (fn [{:keys [tehtava]}]
                  (some (fn [[t1 t2 t3 t4]]
                          (when (= (:id t4) tehtava)
                            (str (:nimi t1) " / " (:nimi t2) " / " (:nimi t3))))
                        toimenpiteet-tasoittain))
        otsikon-mukaan (group-by otsikko tyorivit)]
    (mapcat (fn [[otsikko rivit]]
              (concat [(grid/otsikko otsikko)] rivit))
            (seq otsikon-mukaan))))

(defn kustannukset [valitun-hoitokauden-ja-tpin-kustannukset
                    valitun-hoitokauden-kaikkien-tpin-kustannukset
                    kaikkien-hoitokausien-taman-tpin-kustannukset
                    yks-kustannukset]
  [:div.hoitokauden-kustannukset
   [:div.piirakka-hoitokauden-kustannukset-per-kaikki.row
    [:div.col-xs-4.piirakka
     (let [valittu-kust valitun-hoitokauden-ja-tpin-kustannukset
           kaikki-kust kaikkien-hoitokausien-taman-tpin-kustannukset]
       (when (or (not= 0 valittu-kust) (not= 0 kaikki-kust))
         [:span.piirakka-wrapper
          [:h5.piirakka-label "Tämän hoitokauden osuus kaikista hoitokausista"]
          [vis/pie
           {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
           {"Valittu hoitokausi" valittu-kust "Muut hoitokaudet" (- kaikki-kust valittu-kust)}]]))]
    [:div.col-xs-4.piirakka
     (let [valittu-kust valitun-hoitokauden-ja-tpin-kustannukset
           kaikki-kust valitun-hoitokauden-kaikkien-tpin-kustannukset]
       (when (or (not= 0 valittu-kust) (not= 0 kaikki-kust))
         [:span.piirakka-wrapper
          [:h5.piirakka-label "Tämän toimenpiteen osuus kaikista toimenpiteistä"]
          [vis/pie
           {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
           {"Valittu toimenpide" valittu-kust "Muut toimenpiteet" (- kaikki-kust valittu-kust)}]]))]
    [:div.col-xs-4.piirakka
     (let [kok-hint-yhteensa valitun-hoitokauden-kaikkien-tpin-kustannukset
           yks-hint-yhteensa yks-kustannukset]
         (when (or (not= 0 kok-hint-yhteensa) (not= 0 yks-hint-yhteensa))
             [:span.piirakka-wrapper
              [:h5.piirakka-label "Hoitokauden kokonaishintaisten töiden osuus kaikista töistä"]
              [vis/pie
               {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
               {"Kokonaishintaiset" kok-hint-yhteensa "Yksikkohintaiset" yks-hint-yhteensa}]]))]]
   
   [:div.summa "Kokonaishintaisten töiden toimenpiteen hoitokausi yhteensä "
    [:span (fmt/euro valitun-hoitokauden-ja-tpin-kustannukset)]]
   
   [:div.summa "Kokonaishintaisten töiden toimenpiteiden kaikki hoitokaudet yhteensä "
    [:span (fmt/euro kaikkien-hoitokausien-taman-tpin-kustannukset)]]])

(defn kokonaishintaiset-tyot [ur valitun-hoitokauden-yks-hint-kustannukset]
  (let [urakan-kok-hint-tyot s/urakan-kok-hint-tyot
        toimenpiteet (atom nil)
        urakka (atom nil)
        hae-urakan-tiedot (fn [ur]
                            (reset! urakka ur)
                            (go (reset! toimenpiteet (<! (urakan-toimenpiteet/hae-urakan-toimenpiteet (:id ur))))))
        
        ;; ryhmitellään valitun sopimusnumeron mukaan hoitokausittain
        sopimuksen-tyot-hoitokausittain
        (reaction (let [[sopimus-id _] @s/valittu-sopimusnumero
                        sopimuksen-tyot (filter #(= sopimus-id (:sopimus %))
                                                @urakan-kok-hint-tyot)]
                    (s/ryhmittele-hoitokausittain sopimuksen-tyot (s/hoitokaudet @urakka))))

        
        ;; valitaan materiaaleista vain valitun hoitokauden
        valitun-hoitokauden-tyot
        (reaction (let [hk @s/valittu-hoitokausi]
                    (get @sopimuksen-tyot-hoitokausittain hk)))

        valittu-toimenpide (reaction (first @toimenpiteet))
        valitun-toimenpiteen-ja-hoitokauden-tyot
        (reaction (let [valittu-tp-id (:id @valittu-toimenpide)]
                    (filter #(= valittu-tp-id (:toimenpide %))
                            @valitun-hoitokauden-tyot)))
        
        tyorivit (reaction (let [kirjatut-kkt (into #{} (map #(:kuukausi %)
                                                             @valitun-toimenpiteen-ja-hoitokauden-tyot))
                                 tyhjat-kkt (difference (into #{} (range 1 13)) kirjatut-kkt)
                                 tyhjat-tyot (keep #(luo-tyhja-tyo (:tyyppi @urakka)
                                                                   (:tpi_id @valittu-toimenpide)
                                                                   @s/valittu-hoitokausi
                                                                   %
                                                                   (first @s/valittu-sopimusnumero))
                                                  tyhjat-kkt)]
                             (vec (sort-by (juxt :vuosi :kuukausi)
                                           (concat @valitun-toimenpiteen-ja-hoitokauden-tyot tyhjat-tyot)))))
        ;; kopioidaanko myös tuleville kausille (oletuksena false, vaarallinen)
        tuleville? (atom false)

        ;; jos tulevaisuudessa on dataa, joka poikkeaa tämän hoitokauden materiaaleista, varoita ylikirjoituksesta
        varoita-ylikirjoituksesta?
        (reaction (let [kopioi? @tuleville?
                        varoita? (s/varoita-ylikirjoituksesta? @sopimuksen-tyot-hoitokausittain
                                                               @s/valittu-hoitokausi)]
                    (if-not kopioi?
                      false
                      varoita?)))

        kaikki-sopimuksen-rivit
        (reaction (let [sopimus-id (first @s/valittu-sopimusnumero)]
                    (filter #(= sopimus-id (:sopimus %))
                            @urakan-kok-hint-tyot)))

        kaikki-sopimuksen-ja-hoitokauden-rivit
        (reaction (let [hk-alku (first @s/valittu-hoitokausi)]
                    (filter
                     #(pvm/sama-pvm?
                       (:alkupvm %) hk-alku)
                     @kaikki-sopimuksen-rivit)))
        
        kaikki-sopimuksen-ja-tpin-rivit
        (reaction (let [tpi-id (:tpi_id @valittu-toimenpide)]
                    (filter #(= tpi-id (:toimenpideinstanssi %))
                            @kaikki-sopimuksen-rivit)))

        kaikkien-hoitokausien-taman-tpin-kustannukset
        (reaction (s/toiden-kustannusten-summa
                   @kaikki-sopimuksen-ja-tpin-rivit
                   :summa))

        valitun-hoitokauden-ja-tpin-kustannukset
        (reaction (s/toiden-kustannusten-summa (let [hk-alku (first @s/valittu-hoitokausi)]
                                                 (filter
                                                  #(pvm/sama-pvm?
                                                    (:alkupvm %) hk-alku)
                                                  @kaikki-sopimuksen-ja-tpin-rivit))
                                               :summa))

        valitun-hoitokauden-kaikkien-tpin-kustannukset
        (reaction (s/toiden-kustannusten-summa
                   @kaikki-sopimuksen-ja-hoitokauden-rivit
                   :summa))]

      (hae-urakan-tiedot ur)

    (komp/luo
     {:component-will-receive-props
      (fn [this & [_ ur]]
        (hae-urakan-tiedot ur))

      :component-will-unmount
      (fn [this]
        (reset! tuleville? false))}

     (fn [ur]
       [:div.kokonaishintaiset-tyot
        [:div.label-ja-alasveto
         [:span.alasvedon-otsikko "Toimenpide"]
         [livi-pudotusvalikko {:valinta    @valittu-toimenpide
                               ;;\u2014 on väliviivan unikoodi
                               :format-fn  #(if % (str (:tpi_nimi %)) "Ei toimenpidettä")
                               :valitse-fn #(reset! valittu-toimenpide %)}
          @toimenpiteet]]

        ;; Näytetään kustannusten summat ja piirakkadiagrammit
        [kustannukset
         @valitun-hoitokauden-ja-tpin-kustannukset
         @valitun-hoitokauden-kaikkien-tpin-kustannukset
         @kaikkien-hoitokausien-taman-tpin-kustannukset
         @valitun-hoitokauden-yks-hint-kustannukset]
        
        (if (empty? @toimenpiteet)
          (when @toimenpiteet
            [:span
             [:h5 "Töitä ei voi suunnitella"]
             [:p "Toimenpide pitää olla valittuna, jotta voidaan suunnitella urakalle kokonaishintaisia töitä.
            Varmista että urakalla on ainakin yksi Samposta tullut toimenpideinstanssi. Varsinkin kehitysvaiheessa
            puutteet tietosisällössä ovat mahdollisia."]])
          
          [grid/grid
           {:otsikko (str "Kokonaishintaiset työt: " (:t2_nimi @valittu-toimenpide) " / " (:t3_nimi @valittu-toimenpide) " / " (:tpi_nimi @valittu-toimenpide))
            :tyhja (if (nil? @toimenpiteet) [ajax-loader "Kokonaishintaisia töitä haetaan..."] "Ei kokonaishintaisia töitä")
            :tallenna (istunto/jos-rooli-urakassa istunto/rooli-urakanvalvoja
                                                  (:id ur)
                                                  #(tallenna-tyot ur @s/valittu-sopimusnumero @s/valittu-hoitokausi
                                                                  urakan-kok-hint-tyot % tuleville?)
                                                  :ei-mahdollinen)
            :peruuta #(reset! tuleville? false)
             :tunniste #((juxt :vuosi :kuukausi) %)
             :voi-lisata? false
             :voi-poistaa? (constantly false)
             :muokkaa-footer (fn [g]
                               [:div.kok-hint-muokkaa-footer
                                [raksiboksi "Tallenna tulevillekin hoitokausille"
                                 @tuleville?
                                 #(swap! tuleville? not)
                                 [:div.raksiboksin-info (ikonit/warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
                                 (and @tuleville? @varoita-ylikirjoituksesta?)]])
            }
           
           ;; sarakkeet
           [{:otsikko "Vuosi" :nimi :vuosi :muokattava? (constantly false) :tyyppi :numero :leveys "25%"}
            {:otsikko "Kuukausi" :nimi "kk" :hae #(pvm/kuukauden-nimi (:kuukausi %)) :muokattava? (constantly false)
             :tyyppi  :numero :leveys "25%"}
            {:otsikko       "Summa" :nimi :summa :fmt fmt/euro-opt :tasaa :oikea
             :tyyppi        :numero :leveys "25%"
             :tayta-alas?   #(not (nil? %))
             :tayta-tooltip "Kopioi sama summa tuleville kuukausille"}
            {:otsikko       "Maksupvm" :nimi :maksupvm :pvm-tyhjana #(pvm/luo-pvm (:vuosi %) (- (:kuukausi %) 1) 15)
             :tyyppi        :pvm :fmt #(if % (pvm/pvm %)) :leveys "25%"
             :tayta-alas?   #(not (nil? %))
             :tayta-tooltip "Kopioi sama maksupäivän tuleville kuukausille"
             :tayta-fn      (fn [lahtorivi tama-rivi]
                              ;; lasketaan lähtörivin maksupäivän erotus sen rivin vuosi/kk
                              ;; ja tehdään vastaavalla erotuksella oleva muutos
                              (let [maksupvm (:maksupvm lahtorivi)
                                    p (t/day maksupvm)
                                    kk-alku (pvm/luo-pvm (:vuosi lahtorivi) (dec (:kuukausi lahtorivi)) 1)
                                    suunta (if (pvm/sama-kuukausi? maksupvm kk-alku)
                                             0
                                             (if (t/before? kk-alku maksupvm) 1 -1))
                                    kk-ero                 ;; lasketaan kuinka monta kuukautta eroa on maksupäivällä ja rivin kuukaudella
                                    (loop [ero 0
                                           kk kk-alku]
                                      (if (pvm/sama-kuukausi? kk maksupvm)
                                        ero
                                        (recur (+ ero suunta)
                                               (t/plus kk (t/months suunta)))))
                                    maksu-kk (t/plus (pvm/luo-pvm (:vuosi tama-rivi) (dec (:kuukausi tama-rivi)) 1)
                                                     (t/months kk-ero))
                                    paivia (t/number-of-days-in-the-month maksu-kk)
                                    maksu-pvm (pvm/luo-pvm (t/year maksu-kk) (dec (t/month maksu-kk)) (min p paivia))]
                                
                                (assoc tama-rivi :maksupvm maksu-pvm)))}]
           @tyorivit])]))))
