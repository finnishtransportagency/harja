(ns harja.views.raportit
  "Harjan raporttien pääsivu."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko vihje] :as yleiset]
            [harja.tiedot.raportit :as raportit]
            [cljs.core.async :refer [<! >! chan]]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.suunnittelu.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.domain.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.ui.raportti :as raportti]
            [harja.transit :as t]
            [alandipert.storage-atom :refer [local-storage]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.tiedot.hallintayksikot :as hy])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-raporttityyppi-nimi (reaction (nav/valittu-valilehti :raportit)))
(defn- valitse-raporttityyppi! [nimi]
  (nav/aseta-valittu-valilehti! :raportit nimi))

;; Mäppi raporttityyppejä, haetaan ensimmäisellä kerralla kun raportointiin tullaan
(defonce raporttityypit (atom nil))

(def valittu-raporttityyppi
  (reaction (let [raporttityypit @raporttityypit]
              (when raporttityypit
                (get raporttityypit @valittu-raporttityyppi-nimi)))))

(def muistetut-parametrit (local-storage (atom {}) :raportin-muistetut-parametrit))


(tarkkaile! "Rapsat" raporttityypit)

(defn- raportin-sort-avain
  "Raportin sort avain. Kuvauksen mukaan aakkosjärjestyksessä, paitsi työmaakokous ensin."
  [{kuvaus :kuvaus}]
  (cond
    (= kuvaus "Työmaakokousraportti") ""
    :default kuvaus))

(defonce mahdolliset-raporttityypit
  (reaction (let [v-ur @nav/valittu-urakka
                  v-hal @nav/valittu-hallintayksikko
                  mahdolliset-kontekstit (into #{"koko maa"}
                                               (keep identity [(when v-ur "urakka")
                                                               (when v-hal "hallintayksikko")]))
                  urakkatyypin-raportit (filter
                                         #(= (:urakkatyyppi %) (:arvo @nav/valittu-urakkatyyppi))
                                          (vals @raporttityypit))]
              (sort-by raportin-sort-avain
                         (into []
                               (comp (filter #(some mahdolliset-kontekstit (:konteksti %)))
                                     (filter #(oikeudet/voi-lukea?
                                               (oikeudet/raporttioikeudet (:kuvaus %))
                                               (:id v-ur))))
                               urakkatyypin-raportit)))))

(add-watch mahdolliset-raporttityypit :konteksti-muuttui
           (fn [_ _ old new]
             (let [mahdolliset (into #{} @mahdolliset-raporttityypit)
                   valittu @valittu-raporttityyppi]
               (when-not (mahdolliset valittu)
                 (log "Resetoidaan valittu raportti, ei enää mahdollinen")
                 (valitse-raporttityyppi! nil)))))

(defonce tyhjenna-raportti-kun-valinta-muuttuu
  (run! @valittu-raporttityyppi
        (reset! raportit/suoritettu-raportti nil)))

;; Raportin parametrit, parametrityypin lisäämiseksi luo
;; defmethodit parametrin tyypin mukaan

(defmulti raportin-parametri
  "Muodosta UI-komponentti raportin parametristä. Komponentin tulee olla täysin
  itsenäinen ja sisällettävä otsikon ja muun tarpeellisen.
  Toinen parametri on atom, jonne parametrin arvo tulee päivittää.
  Jos parametri on virheellisessä tilassa, asetetaan arvoksi :virhe.
  Raporttia ei voi suorittaa, jos parametreissä on virheitä"
  (fn [parametri arvo]
    (:tyyppi parametri)))


(defonce parametri-arvot (atom {}))
(defonce tyhjenna-raportti-kun-parametri-muuttuvat
  (run! @parametri-arvot
        (reset! raportit/suoritettu-raportti nil)))


(defonce hoitourakassa? (reaction (= :hoito (:tyyppi @nav/valittu-urakka))))

(defonce valittu-hoitokausi (reaction (if @hoitourakassa?
                                        @u/valittu-hoitokausi
                                        (pvm/paivamaaran-hoitokausi (pvm/nyt)))))

(def valittu-vuosi (atom nil))

(defonce kuukaudet (reaction
                    (let [hk @valittu-hoitokausi
                          vuosi @valittu-vuosi]
                      (into
                       []
                       ;; koko hoitokausi tai vuosi
                       (concat
                        [nil]
                        (cond
                          hk
                          (pvm/hoitokauden-kuukausivalit hk)

                          vuosi
                          (pvm/vuoden-kuukausivalit vuosi)

                          :default
                          []))))))

(defonce valittu-kuukausi (reaction @u/valittu-hoitokauden-kuukausi))
(defonce vapaa-aikavali? (atom false))
(defonce vapaa-aikavali (atom [nil nil]))

(def vain-hoitokausivalinta? #{:suolasakko})

(defonce paivita-aikavalinta
    (run! (let [hk @valittu-hoitokausi
                vuosi @valittu-vuosi
                kk @valittu-kuukausi
                vapaa-aikavali? @vapaa-aikavali?
                aikavali @vapaa-aikavali
                vain-hoitokausivalinta? (vain-hoitokausivalinta?
                                          (:nimi @valittu-raporttityyppi))
                [alku loppu] (cond
                               vain-hoitokausivalinta? hk
                               vapaa-aikavali? aikavali
                               kk kk
                               hk hk
                               vuosi (pvm/vuoden-aikavali vuosi)
                               :default hk)]
            (if (and alku loppu)
              (swap! parametri-arvot
                     assoc "Aikaväli" {:alkupvm alku :loppupvm loppu})
              (swap! parametri-arvot
                     assoc "Aikaväli" {:virhe "Aikaväli puuttuu"})))))

(defmethod raportin-parametri "aikavali" [p arvo]
  ;; Näytetään seuraavat valinnat
  ;; - vuosi (joko urakkavuodet tai generoitu lista)
  ;; - hoitokaudet (joko urakan hoitokaudet tai generoitu lista)
  ;; - kuukausi (valitun urakan tai hoitokauden kuukaudet, tai kaikki)
  ;; - vapaa tekstisyöttö aikavälille
  ;;
  ;; Jos valittuna on urakka, joka ei ole tyyppiä hoito,
  ;; ei näytetä hoitokausivalintaa.
  (let [ur @nav/valittu-urakka
        hoitourakassa? @hoitourakassa?
        hal @nav/valittu-hallintayksikko
        vuosi-eka (if ur
                    (pvm/vuosi (:alkupvm ur))
                    2010)
        vuosi-vika (if ur
                     (pvm/vuosi (:loppupvm ur))
                     (pvm/vuosi (pvm/nyt)))
        vain-hoitokausivalinta? (vain-hoitokausivalinta? (:nimi @valittu-raporttityyppi))]
    [:span
     [:div.raportin-vuosi-hk-kk-valinta
      [ui-valinnat/vuosi {:disabled (or @vapaa-aikavali?
                                        vain-hoitokausivalinta?)}
       vuosi-eka vuosi-vika valittu-vuosi
       #(do
          (reset! valittu-vuosi %)
          (reset! valittu-hoitokausi nil)
          (reset! valittu-kuukausi nil))]
      (when (or hoitourakassa? (nil? ur))
        [ui-valinnat/hoitokausi
         {:disabled @vapaa-aikavali?}
         (if hoitourakassa?
           (u/hoitokaudet ur)
           (u/edelliset-hoitokaudet 5 true))
         valittu-hoitokausi
         #(do
            (reset! valittu-hoitokausi %)
            (reset! valittu-vuosi nil)
            (reset! valittu-kuukausi nil))])
      [ui-valinnat/kuukausi {:disabled    (or @vapaa-aikavali?
                                              vain-hoitokausivalinta?)
                             :nil-valinta (if @valittu-vuosi
                                            "Koko vuosi"
                                            "Koko hoitokausi")}
       @kuukaudet valittu-kuukausi]]

     (when-not vain-hoitokausivalinta?
       [:div.raportin-valittu-aikavali
       [yleiset/raksiboksi "Valittu aikaväli" @vapaa-aikavali?
        #(swap! vapaa-aikavali? not)
        nil false (when @vapaa-aikavali?
                    [:div
                     [ui-valinnat/aikavali vapaa-aikavali {:aikavalin-rajoitus [5 :vuosi]}]
                     [vihje "Raportin suurin sallitu aikaväli on 5 vuotta" "raportit-valittuaikavali-vihje"]])]])]))

(def tienumero (atom nil))

(defmethod raportin-parametri "tienumero" [p arvo]
  (fn [_ _]
    [valinnat/tienumero (r/wrap @tienumero (fn [uusi]
                                             (reset! arvo {:tienumero uusi})
                                             (reset! tienumero uusi)))]))

(defmethod raportin-parametri "urakan-toimenpide" [p arvo]
  (let [aseta-tpi (fn [tpi]
                    (reset! arvo (if tpi
                                   {:toimenpide-id (:id tpi)}
                                   {:virhe "Ei tpi valintaa"})))]
    (komp/luo
      (komp/watcher u/valittu-toimenpideinstanssi
                    (fn [_ _ tpi]
                      (aseta-tpi tpi)))
      (komp/piirretty #(reset! u/valittu-toimenpideinstanssi {:tpi_nimi "Kaikki"}))

      (fn [_ _]
        @u/valittu-toimenpideinstanssi
        [valinnat/urakan-toimenpide+kaikki]))))

(defonce urakoittain? (atom false))

(defmethod raportin-parametri "urakoittain" [p arvo]
  (if @nav/valittu-urakka
    [:span]
    [:div.urakoittain
     [yleiset/raksiboksi (:nimi p)
      @urakoittain?
      #(do (swap! urakoittain? not)
           (reset! arvo
                   {:urakoittain? @urakoittain?}))
      nil false]]))

(def laatupoikkeama-tekija (atom :kaikki))

(defmethod raportin-parametri "laatupoikkeamatekija" [p arvo]
  (reset! arvo {:laatupoikkeamatekija @laatupoikkeama-tekija})
  (fn []
    [yleiset/pudotusvalikko
     "Tekijä"
     {:valinta    @laatupoikkeama-tekija
      :valitse-fn #(do (reset! laatupoikkeama-tekija %)
                       (reset! arvo {:laatupoikkeamatekija %}))
      :format-fn  #(case %
                    :kaikki "Kaikki"
                    (laatupoikkeamat/kuvaile-tekija %))}

     [:kaikki :urakoitsija :tilaaja :konsultti]]))

(def tyomaakokousraportit
  {"Erilliskustannukset" :erilliskustannukset
   "Ilmoitukset" :ilmoitusraportti
   "Kelitarkastusraportti" :kelitarkastusraportti
   "Laatupoikkeamat" :laatupoikkeamaraportti
   "Laskutusyhteenveto" :laskutusyhteenveto
   "Materiaaliraportti" :materiaaliraportti
   "Sanktioiden yhteenveto" :sanktioraportti
   "Soratietarkastukset" :soratietarkastusraportti
   "Tiestötarkastukset" :tiestotarkastusraportti
   "Turvallisuusraportti" :turvallisuus
   "Yksikköhintaiset työt kuukausittain" :yks-hint-kuukausiraportti
   "Yksikköhintaiset työt päivittäin" :yksikkohintaiset-tyot
   "Yksikköhintaisten töiden raportti" :yksikkohintaiset-tyot
   "Yksikköhintaiset työt tehtävittäin" :yks-hint-tehtavien-summat
   "Ympäristöraportti" :ymparisto})

(defmethod raportin-parametri "checkbox" [p arvo]
  (let [avaimet [(:nimi @valittu-raporttityyppi)
                 (or (tyomaakokousraportit (:nimi p)) (:nimi p))]
        paivita! #(do (swap! muistetut-parametrit
                             update-in avaimet not)
                      (reset! arvo
                              {(or (tyomaakokousraportit (:nimi p))
                                   (:nimi p)) (get-in @muistetut-parametrit [(:nimi @valittu-raporttityyppi) (:nimi p)])}))]
    [:div
     [yleiset/raksiboksi (:nimi p) (get-in @muistetut-parametrit avaimet) paivita! nil false]]))

(defmethod raportin-parametri "hoitoluokat" [p arvo]
  []
  [:div.hoitoluokat
   [yleiset/otsikolla "Hoitoluokat"
    (let [arvo-nyt (or @arvo {:hoitoluokat (into #{}
                                                 (map :numero hoitoluokat/talvihoitoluokat))})
          _ (log "ARVO-NYT: " (pr-str arvo-nyt))
          valitut (:hoitoluokat arvo-nyt)
          [vasen oikea] (partition 4 hoitoluokat/talvihoitoluokat)]
      (vec (concat
            [yleiset/rivi {:koko  "col-sm-2"}]
            (for [sarake (partition 4 hoitoluokat/talvihoitoluokat)]
              ^{:key (:numero (first sarake))}
              [:div.inline
               (for [{:keys [nimi numero]} sarake
                     :let [valittu? (valitut numero)]]
                 ^{:key numero}
                 [yleiset/raksiboksi
                  nimi valittu?
                  #(reset! arvo {:hoitoluokat
                                 ((if valittu? disj conj) valitut numero)})
                  nil nil])]))))]])

(defmethod raportin-parametri :default [p arvo]
  [:span (pr-str p)])

;; Tarkistaa raporttityypin mukaan voiko näillä parametreilla suorittaa
(defmulti raportin-voi-suorittaa? (fn [raporttityyppi parametrit] (:nimi raporttityyppi)))

(defmethod raportin-voi-suorittaa? :tyomaakokous [_ parametrit]
  (some #(get parametrit %) (vals tyomaakokousraportit)))

;; Oletuksena voi suorittaa, jos ei raporttikohtaista sääntöä ole
(defmethod raportin-voi-suorittaa? :default [_ _] true)

(defn- parametrin-sort-avain
  "Parametrin sort avain."
  [{nimi :nimi}]
  (cond
    (= nimi "Aikaväli") "1"
    (= nimi "Toimenpide") "3"
    :default nimi))

(def parametri-omalle-riville? #{"aikavali" "urakoittain"})

(def ^{:private true :doc "Mahdolliset raportin vientimuodot"}
  +vientimuodot+
  [[(ikonit/save) "Tallenna Excel" "raporttixls" (k/excel-url :raportointi)]
   [(ikonit/print) "Tallenna PDF" "raporttipdf" (k/pdf-url :raportointi)]])

(defn- vie-raportti [v-hal v-ur konteksti raporttityyppi voi-suorittaa? arvot-nyt]
  (let [aseta-parametrit! (fn [id]
                            (let [input (-> js/document
                                            (.getElementById id)
                                            (aget "parametrit"))
                                  parametrit (case konteksti
                                               "koko maa"
                                               (raportit/koko-maa-raportin-parametrit
                                                (:nimi raporttityyppi) arvot-nyt)
                                               "hallintayksikko"
                                               (raportit/hallintayksikon-raportin-parametrit
                                                (:id v-hal) (:nimi raporttityyppi) arvot-nyt)
                                               "urakka"
                                               (raportit/urakkaraportin-parametrit
                                                (:id v-ur) (:nimi raporttityyppi) arvot-nyt))]
                              (set! (.-value input)
                                    (t/clj->transit parametrit))
                              true))]
    [:span
     (for [[ikoni teksti id url] +vientimuodot+]
       ^{:key id}
       [:form {:target "_blank" :method "POST" :id id
               :style {:display "inline"}
               :action url}
        [:input {:type  "hidden" :name "parametrit"
                 :value ""}]
        [:button.nappi-ensisijainen.pull-right
         {:type     "submit"
          :disabled (not voi-suorittaa?)
          :on-click #(aseta-parametrit! id)}
         ikoni " " teksti]])]))

(defn raportin-parametrit [raporttityyppi konteksti v-ur v-hal]
  (let [parametrit (sort-by parametrin-sort-avain
                            (filter #(let [k (:konteksti %)]
                                      (or (nil? k)
                                          (= k konteksti)))
                                    (:parametrit raporttityyppi)))

        nakyvat-parametrit (into #{} (map :nimi) parametrit)
        arvot-nyt (reduce merge {}
                          (keep (fn [[nimi arvot]]
                                  (when (nakyvat-parametrit nimi)
                                    arvot))
                                @parametri-arvot))
        arvot-nyt (merge arvot-nyt
                         (get @muistetut-parametrit (:nimi raporttityyppi))
                         {:urakkatyyppi (:arvo @nav/valittu-urakkatyyppi)})
        voi-suorittaa? (and (not (contains? arvot-nyt :virhe))
                            (raportin-voi-suorittaa? raporttityyppi arvot-nyt))
        raportissa? (some? @raportit/suoritettu-raportti)]

    ;; Jos parametreja muutetaan tai ne vaihtuu lomakkeen vaihtuessa, tyhjennä suoritettu raportti

    [:span
     (when-not raportissa?
       (map-indexed
        (fn [i cols]
          ^{:key i}
          [:div.row (seq cols)])
        (loop [rows []
               row nil
               [p & parametrit] parametrit]

          (let [arvo (r/wrap (get @parametri-arvot (:nimi p))
                             #(swap! parametri-arvot assoc (:nimi p) %))]
            (if-not p
              (conj rows row)
              (let [par ^{:key (:nimi p)} [:div
                                           {:class (if (parametri-omalle-riville? (:tyyppi p))
                                                     "col-md-12"
                                                     "col-md-4")}
                                           [raportin-parametri p arvo]]]
                (cond
                  ;; checkboxit ja aikaväli aina omalle riville
                  (parametri-omalle-riville? (:tyyppi p))
                  (recur (conj (if row
                                 (conj rows row)
                                 rows)
                               [par])
                         nil
                         parametrit)

                  ;; Jos rivi on täynnä aloitetaan uusi
                  (= 3 (count row))
                  (recur (conj rows row)
                         [par]
                         parametrit)

                  ;; Muutoin lisätään aiempaan riviin
                  :default
                  (recur rows
                         (if row (conj row par)
                             [par])
                         parametrit))))))))

     [:div.row
      [:div.col-md-12
       [:div.raportin-toiminnot
        (when raportissa?
          [napit/takaisin "Palaa raporttivalintoihin"
           #(reset! raportit/suoritettu-raportti nil)])
        [vie-raportti v-hal v-ur konteksti raporttityyppi voi-suorittaa? arvot-nyt]
        (when-not raportissa?
          [napit/palvelinkutsu-nappi " Tee raportti"
           #(go (reset! raportit/suoritettu-raportti :ladataan)
                (let [raportti
                      (<! (case konteksti
                            "koko maa"
                            (raportit/suorita-raportti-koko-maa (:nimi raporttityyppi)
                                                                arvot-nyt)
                            "hallintayksikko"
                            (raportit/suorita-raportti-hallintayksikko (:id v-hal)
                                                                       (:nimi raporttityyppi)
                                                                       arvot-nyt)
                            "urakka"
                            (raportit/suorita-raportti-urakka (:id v-ur)
                                                              (:nimi raporttityyppi)
                                                              arvot-nyt)))]
                  (if-not (k/virhe? raportti)
                    (reset! raportit/suoritettu-raportti raportti)
                    (do
                      (reset! raportit/suoritettu-raportti nil)
                      raportti))))
           {:ikoni    [ikonit/list]
            :disabled (not voi-suorittaa?)}])]]]]))

(defn hallintayksikko-ja-urakkatyyppi [v-hal v-ur-tyyppi]
  [:span
   [yleiset/livi-pudotusvalikko
    {:valitse-fn nav/valitse-hallintayksikko
     :valinta v-hal
     :class "raportti-alasveto"
     :format-fn (fnil hy/elynumero-ja-nimi {:nimi "Kaikki ELYt"})}
    (concat [nil]
            @hy/hallintayksikot)]
   " "
   [yleiset/livi-pudotusvalikko
    {:valitse-fn nav/vaihda-urakkatyyppi!
     :valinta v-ur-tyyppi
     :class "raportti-alasveto"
     :format-fn :nimi}
    nav/+urakkatyypit+]])

(defn raporttivalinnat []
  (komp/luo
   ;; Ei tällä hetkellä raporteissa sallita urakoitsijavalintaa
   (komp/sisaan #(nav/valitse-urakoitsija! nil))
    (fn []
      (let [v-ur @nav/valittu-urakka
            v-ur-tyyppi @nav/valittu-urakkatyyppi
            v-hal @nav/valittu-hallintayksikko
            konteksti (cond
                        v-ur "urakka"
                        v-hal "hallintayksikko"
                        :default "koko maa")
            raportissa? (some? @raportit/suoritettu-raportti)]
        [:div.raporttivalinnat
         (when-not raportissa?
           [:span
            [:h3 "Raportin tiedot"]
            [yleiset/tietoja {:class "border-bottom"}
             "Hallintayksikkö" [hallintayksikko-ja-urakkatyyppi v-hal v-ur-tyyppi]
             "Urakka" (when v-hal
                        [yleiset/livi-pudotusvalikko
                         {:valitse-fn nav/valitse-urakka
                          :valinta v-ur
                          :class "raportti-alasveto"
                          :format-fn (fnil :nimi {:nimi "Kaikki urakat"})}
                         (concat [nil]
                                 (sort-by :nimi @nav/suodatettu-urakkalista))])
             "Hallintayksikkö" (when (= "hallintayksikko" konteksti)
                                 (:nimi v-hal))
             "Raportti" (cond
                          (nil? @raporttityypit)
                          [:span "Raportteja haetaan..."]
                          (empty? @mahdolliset-raporttityypit)
                          [:span (str "Ei raportteja saatavilla urakkatyypissä " (str/lower-case (:nimi v-ur-tyyppi)))]
                          :default
                          [livi-pudotusvalikko {:valinta    @valittu-raporttityyppi
                                                ;;\u2014 on väliviivan unikoodi
                                                :format-fn  #(if % (:kuvaus %) "Valitse")
                                                :valitse-fn #(valitse-raporttityyppi! (:nimi %))
                                                :class      "raportti-alasveto"
                                                :li-luokka-fn #(if (= "Työmaakokousraportti" (:kuvaus %))
                                                                "tyomaakokous"
                                                                "")}
                           @mahdolliset-raporttityypit])]])

         (when @valittu-raporttityyppi
           [:div.raportin-asetukset
            [raportin-parametrit @valittu-raporttityyppi konteksti v-ur v-hal]])]))))

(defn nayta-raportti [tyyppi r]
  (komp/luo
   (fn [tyyppi r]
     [:span
      [raportti/muodosta-html (assoc-in r [1 :tunniste] (:nimi tyyppi))]])))

(defn raporttivalinnat-ja-raportti []
  (let [v-ur @nav/valittu-urakka
        hae-urakan-tyot (fn [ur]
                          (log "[RAPORTTI] Haetaan urakan yks. hint. ja kok. hint. työt")
                          (go (reset! u/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! u/urakan-yks-hint-tyot
                                      (s/prosessoi-tyorivit ur
                                                            (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))]

    (when v-ur (hae-urakan-tyot @nav/valittu-urakka))
    (let [r @raportit/suoritettu-raportti]
      [:span
       [raporttivalinnat]
       (cond (= :ladataan r)
             [yleiset/ajax-loader "Raporttia suoritetaan..."]

             (not (nil? r))
             [nayta-raportti @valittu-raporttityyppi r])])))

(defn raportit []
  (komp/luo
    (komp/lippu raportit/raportit-nakymassa?)
    (komp/sisaan #(do (when (nil? @raporttityypit)
                        (go (reset! raporttityypit (<! (raportit/hae-raportit)))))
                      (nav/valitse-urakoitsija! nil)))
    (komp/sisaan-ulos #(do
                        (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                        (nav/vaihda-kartan-koko! :M))
                      #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn []
      (if (oikeudet/raportit)
        [:span
         (when-not @raportit/suoritettu-raportti
           [kartta/kartan-paikka @nav/murupolku-domissa?])
         (raporttivalinnat-ja-raportti)]
        [:span "Sinulla ei ole oikeutta tarkastella raportteja."]))))
