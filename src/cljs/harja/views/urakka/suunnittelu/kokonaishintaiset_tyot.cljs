(ns harja.views.urakka.suunnittelu.kokonaishintaiset-tyot
  "Urakan 'Kokonaishintaiset työt' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                                      alasveto-ei-loydoksia livi-pudotusvalikko radiovalinta]
             :as yleiset]
            [harja.visualisointi :as vis]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as kok-hint-tyot]

            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]

            [clojure.set :refer [difference]]
            [cljs.core.async :refer [<!]]
            [cljs-time.core :as t]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.grid-yhteiset :as grid-yhteiset])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))



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
  (go (let [hoitokaudet (u/hoitokaudet ur)
            muuttuneet
            (into []
                  (if @tuleville?
                    (u/rivit-tulevillekin-kausille-kok-hint-tyot ur uudet-tyot valittu-hoitokausi)
                    uudet-tyot
                    ))
            res (<! (kok-hint-tyot/tallenna-kokonaishintaiset-tyot ur sopimusnumero muuttuneet))
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
              (concat [(grid-yhteiset/otsikko otsikko)] rivit))
            (seq otsikon-mukaan))))

(defn hoidon-kustannusyhteenveto
  [valitun-hoitokauden-ja-tpin-kustannukset
   valitun-hoitokauden-kaikkien-tpin-kustannukset
   kaikkien-hoitokausien-taman-tpin-kustannukset
   yks-kustannukset]
  [:div.col-md-6.hoitokauden-kustannukset
   [:div.piirakka-hoitokauden-kustannukset-per-kaikki
    [:div.piirakka
     (let [valittu-kust valitun-hoitokauden-ja-tpin-kustannukset
           kaikki-kust kaikkien-hoitokausien-taman-tpin-kustannukset]
       (when (or (not= 0 valittu-kust) (not= 0 kaikki-kust))
         [:span.piirakka-wrapper
          [:h5.piirakka-label "Tämän hoitokauden osuus kaikista hoitokausista"]
          [vis/pie
           {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
           {"Valittu hoitokausi" valittu-kust "Muut hoitokaudet" (- kaikki-kust valittu-kust)}]]))]
    [:div.piirakka
     (let [valittu-kust valitun-hoitokauden-ja-tpin-kustannukset
           kaikki-kust valitun-hoitokauden-kaikkien-tpin-kustannukset]
       (when (or (not= 0 valittu-kust) (not= 0 kaikki-kust))
         [:span.piirakka-wrapper
          [:h5.piirakka-label "Tämän toimenpiteen osuus kaikista toimenpiteistä"]
          [vis/pie
           {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
           {"Valittu toimenpide" valittu-kust "Muut toimenpiteet" (- kaikki-kust valittu-kust)}]]))]
    [:div.piirakka
     (let [kok-hint-yhteensa valitun-hoitokauden-kaikkien-tpin-kustannukset
           yks-hint-yhteensa yks-kustannukset]
         (when (or (not= 0 kok-hint-yhteensa) (not= 0 yks-hint-yhteensa))
             [:span.piirakka-wrapper
              [:h5.piirakka-label "Hoitokauden kokonaishintaisten töiden osuus kaikista töistä"]
              [vis/pie
               {:width 230 :height 150 :radius 60 :show-text :percent :show-legend true}
               {"Kokonaishintaiset" kok-hint-yhteensa "Yksikkohintaiset" yks-hint-yhteensa}]]))]]

   [:div.summa.summa-toimenpiteen-hoitokausi
    "Kokonaishintaisten töiden toimenpiteen hoitokausi yhteensä "
    [:span (fmt/euro valitun-hoitokauden-ja-tpin-kustannukset)]]

   [:div.summa.summa-toimenpiteiden-hoitokaudet
    "Kokonaishintaisten töiden toimenpiteiden kaikki hoitokaudet yhteensä "
    [:span (fmt/euro kaikkien-hoitokausien-taman-tpin-kustannukset)]]])

(defn kokonaishintaiset-tyot-tehtavalista [tehtavat tpi]
  [:div
   [grid/grid {:otsikko "Raportoitavat kokonaishintaiset tehtävät"}
    [{:otsikko "Tehtävä" :nimi :nimi :leveys 9}
     {:otsikko "Yksikkö" :nimi :yksikko :leveys 1}]

    (map #(nth % 3)
         (filter (fn [[_ _ t3 _]]
                   (= (:koodi t3) (:t3_koodi tpi)))
                 tehtavat))]
   [yleiset/vihje
    "Tehtävät ovat järjestelmän laajuisia ja vain järjestelmän vastuuhenkilö voi muuttaa niitä."]])

(defn- tyorivit [urakka valittu-sopimusnumero valittu-hoitokausi valittu-toimenpideinstanssi
                 valitun-toimenpiteen-ja-hoitokauden-tyot ]
  (let [kirjatut-kkt (into #{} (map #(:kuukausi %)
                                    valitun-toimenpiteen-ja-hoitokauden-tyot))
        tyhjat-kkt (difference (into #{} (range 1 13)) kirjatut-kkt)
        [hoitokauden-alku hoitokauden-loppu] valittu-hoitokausi
        tyhjat-tyot (when hoitokauden-alku
                      (keep #(luo-tyhja-tyo (:tyyppi urakka)
                                            (:tpi_id valittu-toimenpideinstanssi)
                                            valittu-hoitokausi
                                            %
                                            (first valittu-sopimusnumero))
                            tyhjat-kkt))]

    (vec (sort-by (juxt :vuosi :kuukausi)
                  (concat valitun-toimenpiteen-ja-hoitokauden-tyot
                          ;; filteröidään pois hoitokauden ulkopuoliset kk:t
                          (filter #(pvm/valissa? (pvm/luo-pvm (:vuosi %) (dec (:kuukausi %)) 15)
                                                 hoitokauden-alku hoitokauden-loppu)
                                  tyhjat-tyot))))))

(defn kokonaishintaiset-tyot [ur valitun-hoitokauden-yks-hint-kustannukset]
  (let [urakan-kok-hint-tyot u/urakan-kok-hint-tyot
        toimenpiteet u/urakan-toimenpideinstanssit
        urakka (atom nil)
        aseta-urakka! (fn [ur] (reset! urakka ur))
        ;; ryhmitellään valitun sopimusnumeron mukaan hoitokausittain
        sopimuksen-tyot-hoitokausittain
        (reaction (let [[sopimus-id _] @u/valittu-sopimusnumero
                        sopimuksen-tyot (filter #(= sopimus-id (:sopimus %))
                                                @urakan-kok-hint-tyot)]
                    (u/ryhmittele-hoitokausittain sopimuksen-tyot (u/hoitokaudet @urakka))))


        ;; valitaan materiaaleista vain valitun hoitokauden
        valitun-hoitokauden-tyot
        (reaction (let [hk @u/valittu-hoitokausi]
                    (get @sopimuksen-tyot-hoitokausittain hk)))

        valitun-toimenpiteen-ja-hoitokauden-tyot
        (reaction (let [valittu-tp-id (:id @u/valittu-toimenpideinstanssi)]
                    (filter #(= valittu-tp-id (:toimenpide %))
                            @valitun-hoitokauden-tyot)))

        tyorivit (reaction
                  (tyorivit
                   @urakka @u/valittu-sopimusnumero
                   @u/valittu-hoitokausi @u/valittu-toimenpideinstanssi
                   @valitun-toimenpiteen-ja-hoitokauden-tyot))
        ;; kopioidaanko myös tuleville kausille (oletuksena false, vaarallinen)
        tuleville? (atom false)

        ;; jos tulevaisuudessa on dataa, joka poikkeaa tämän hoitokauden materiaaleista, varoita ylikirjoituksesta
        varoita-ylikirjoituksesta?
        (reaction (let [kopioi? @tuleville?
                        varoita? (s/varoita-ylikirjoituksesta? @sopimuksen-tyot-hoitokausittain
                                                               @u/valittu-hoitokausi)]
                    (if-not kopioi?
                      false
                      varoita?)))

        kaikki-sopimuksen-ja-tpin-rivit
        (reaction (let [tpi-id (:tpi_id @u/valittu-toimenpideinstanssi)]
                    (filter #(= tpi-id (:toimenpideinstanssi %))
                            @s/kaikki-sopimuksen-kok-hint-rivit)))

        kaikkien-hoitokausien-taman-tpin-kustannukset
        (reaction (s/toiden-kustannusten-summa
                   @kaikki-sopimuksen-ja-tpin-rivit
                   :summa))

        valitun-hoitokauden-ja-tpin-kustannukset
        (reaction (s/toiden-kustannusten-summa (let [hk-alku (first @u/valittu-hoitokausi)]
                                                 (filter
                                                  #(pvm/sama-pvm?
                                                    (:alkupvm %) hk-alku)
                                                  @kaikki-sopimuksen-ja-tpin-rivit))
                                               :summa))]

    (aseta-urakka! ur)

    (komp/luo
     {:component-will-receive-props
      (fn [this & [_ ur]]
        (aseta-urakka! ur))

      :component-will-unmount
      (fn [this]
        (reset! tuleville? false))}

     (fn [ur valitun-hoitokauden-yks-hint-kustannukset]
       [:span
        [:div.row
         [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide ur]]

        [:div.row.kokonaishintaiset-tyot


         (if (empty? @toimenpiteet)
           (when @toimenpiteet
             [:span
              [:h5 "Töitä ei voi suunnitella"]
              [:p "Toimenpide pitää olla valittuna, jotta voidaan suunnitella urakalle kokonaishintaisia töitä.
            Varmista että urakalla on ainakin yksi Samposta tullut toimenpideinstanssi. Varsinkin kehitysvaiheessa
            puutteet tietosisällössä ovat mahdollisia."]])

           [:div.col-md-6
            [grid/grid
             {:otsikko (str "Kokonaishintaiset työt: " (:tpi_nimi @u/valittu-toimenpideinstanssi))
              :piilota-toiminnot? true
              :tyhja (if (nil? @toimenpiteet) [ajax-loader "Kokonaishintaisia töitä haetaan..."] "Ei kokonaishintaisia töitä")
              :tallenna (if (oikeudet/voi-kirjoittaa?
                              (oikeudet/tarkistettava-oikeus-kok-hint-tyot (:tyyppi ur)) (:id ur))
                          #(tallenna-tyot ur @u/valittu-sopimusnumero @u/valittu-hoitokausi urakan-kok-hint-tyot % tuleville?)
                          :ei-mahdollinen)
              :tallennus-ei-mahdollinen-tooltip (oikeudet/oikeuden-puute-kuvaus
                                                 :kirjoitus
                                                 (oikeudet/tarkistettava-oikeus-kok-hint-tyot (:tyyppi ur)))
              :tallenna-vain-muokatut false
              :peruuta #(reset! tuleville? false)
              :tunniste #((juxt :vuosi :kuukausi) %)
              :voi-lisata? false
              :voi-poistaa? (constantly false)
              :muokkaa-footer (fn [g]
                                [:div.kok-hint-muokkaa-footer
                                 [raksiboksi {:teksti (s/monista-tuleville-teksti (:tyyppi @urakka))
                                              :toiminto #(swap! tuleville? not)
                                              :info-teksti  [:div.raksiboksin-info (ikonit/livicon-warning-sign) "Tulevilla hoitokausilla eri tietoa, jonka tallennus ylikirjoittaa."]
                                              :nayta-infoteksti? (and @tuleville? @varoita-ylikirjoituksesta?)}
                                  @tuleville?]])}

             ;; sarakkeet
             [{:otsikko "Vuosi" :nimi :vuosi :muokattava? (constantly false) :tyyppi :numero :leveys 25}
              {:otsikko "Kuukausi" :nimi "kk" :hae #(pvm/kuukauden-nimi (:kuukausi %)) :muokattava? (constantly false)
               :tyyppi  :numero :leveys 25}
              {:otsikko       "Summa" :nimi :summa :fmt fmt/euro-opt :tasaa :oikea
               :tyyppi        :positiivinen-numero :leveys 25
               :tayta-alas?   #(not (nil? %))
               :tayta-tooltip "Kopioi sama summa tuleville kuukausille"}
              {:otsikko       "Maksupvm" :nimi :maksupvm :pvm-tyhjana #(pvm/luo-pvm (:vuosi %) (- (:kuukausi %) 1) 15)
               :tyyppi        :pvm :fmt #(if % (pvm/pvm %)) :leveys 25
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
                                      kk-ero ;; lasketaan kuinka monta kuukautta eroa on maksupäivällä ja rivin kuukaudella
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
             @tyorivit]

            (when (not= (:tyyppi @urakka) :tiemerkinta)
              [kokonaishintaiset-tyot-tehtavalista
               @u/urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat-tehtavat
               @u/valittu-toimenpideinstanssi])])

         ;; TODO Jos on tiemerkintä, niin pitäisi näyttää kok. hint. yhteensä, yks. hint yhteensä ja muut yhteensä,
         ;; myös muilla välilehdillä
         (when (not= (:tyyppi @urakka) :tiemerkinta)
           [hoidon-kustannusyhteenveto
            @valitun-hoitokauden-ja-tpin-kustannukset
            @s/valitun-hoitokauden-kok-hint-kustannukset
            @kaikkien-hoitokausien-taman-tpin-kustannukset
            @valitun-hoitokauden-yks-hint-kustannukset])]]))))
