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
            [harja.ui.yleiset :refer [livi-pudotusvalikko] :as yleiset]
            [harja.tiedot.raportit :as raportit]
            [cljs.core.async :refer [<! >! chan]]
            [harja.views.kartta :as kartta]
            [harja.tiedot.urakka.suunnittelu.yksikkohintaiset-tyot :as yks-hint-tyot]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.suunnittelu.kokonaishintaiset-tyot :as kok-hint-tyot]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.domain.roolit :as roolit]
            [harja.ui.raportti :as raportti]
            [harja.transit :as t]
            [alandipert.storage-atom :refer [local-storage]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-raporttityyppi (atom nil))

(def muistetut-parametrit (local-storage (atom {}) :raportin-muistetut-parametrit))

;; Tähän asetetaan suoritetun raportin elementit, jotka renderöidään
(defonce suoritettu-raportti (atom nil))

;; Mäppi raporttityyppejä, haetaan ensimmäisellä kerralla kun raportointiin tullaan
(defonce raporttityypit (atom nil))

(defonce mahdolliset-raporttityypit
  (reaction (let [v-ur @nav/valittu-urakka
                  v-hal @nav/valittu-hallintayksikko
                  mahdolliset-kontekstit (into #{"koko maa"}
                                               (keep identity [(when v-ur "urakka")
                                                               (when v-hal "hallintayksikko")]))
                  urakkatyypin-raportit (filter
                                          #(= (:urakkatyyppi %) (:arvo @nav/valittu-urakkatyyppi))
                                          (vals @raporttityypit))]
              (sort-by :kuvaus
                       (into []
                             (filter #(some mahdolliset-kontekstit (:konteksti %)))
                             urakkatyypin-raportit)))))

(add-watch mahdolliset-raporttityypit :konteksti-muuttui
           (fn [_ _ old new]
             (let [mahdolliset (into #{} @mahdolliset-raporttityypit)
                   valittu @valittu-raporttityyppi]
               (when-not (mahdolliset valittu)
                 (log "Resetoidaan valittu raportti, ei enää mahdollinen")
                 (reset! valittu-raporttityyppi nil)))))

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


(defmethod raportin-parametri "aikavali" [p arvo]
  ;; Näytetään seuraavat valinnat
  ;; vuosi (joko urakkavuodet tai generoitu lista)
  ;; hoitokaudet (joko urakan hoitokaudet tai generoitu lista)
  ;; kuukausi (valitun urakan tai hoitokauden kuukaudet, tai kaikki)
  ;; vapaa tekstisyöttö aikavälille
  ;;
  ;; Jos valittuna on urakka, joka ei ole tyyppiä hoito,
  ;; ei näytetä hoitokausivalintaa.

  (let [ur (reaction @nav/valittu-urakka)
        hoitourakassa? (reaction (= :hoito (:tyyppi @ur)))
        valittu-vuosi (reaction (when-not @hoitourakassa? (pvm/vuosi (pvm/nyt))))
        valittu-hoitokausi (reaction (when @hoitourakassa?
                                       @u/valittu-hoitokausi))
        kuukaudet (reaction
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
                         [])))))
        valittu-kuukausi (atom nil)
        vapaa-aikavali? (atom false)
        vapaa-aikavali (atom [nil nil])]
    (run! (let [hk @valittu-hoitokausi
                vuosi @valittu-vuosi
                kk @valittu-kuukausi
                vapaa-aikavali? @vapaa-aikavali?
                aikavali @vapaa-aikavali
                [alku loppu] (cond
                               vapaa-aikavali? aikavali
                               kk kk
                               vuosi (pvm/vuoden-aikavali vuosi)
                               :default hk)]
            (log "ASETA ARVO: " (pr-str [alku loppu]))
            (reset! arvo {:alkupvm alku :loppupvm loppu})))
    
    (fn [_ _]
      (let [ur @ur
            hoitourakassa? @hoitourakassa?
            hal @nav/valittu-hallintayksikko
            vuosi-eka (if ur
                        (pvm/vuosi (:alkupvm ur))
                        2010)
            vuosi-vika (if ur
                         (pvm/vuosi (:loppupvm ur))
                         (pvm/vuosi (pvm/nyt)))]
        [:span
         [:div 
          [ui-valinnat/vuosi {:disabled @vapaa-aikavali?}
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
          [ui-valinnat/kuukausi {:disabled @vapaa-aikavali?
                                 :nil-valinta (if @valittu-vuosi
                                                "Koko vuosi"
                                                "Koko hoitokausi")}
           @kuukaudet valittu-kuukausi]]

         [:div.raportin-valittu-aikavali
          [yleiset/raksiboksi "Valittu aikaväli" @vapaa-aikavali?
           #(swap! vapaa-aikavali? not)
           nil false (when @vapaa-aikavali?
                       [ui-valinnat/aikavali vapaa-aikavali])]]]))))

     
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

(def tyomaakokousraportit
  {"Laskutusyhteenveto" :laskutusyhteenveto
   "Yksikköhintaisten töiden raportti" :yksikkohintaiset-tyot
   "Ilmoitusraportti" :ilmoitusraportti
   "Turvallisuusraportti" :turvallisuus
   "Ympäristöraportti" :ymparisto})

(defmethod raportin-parametri "checkbox" [p arvo]
  (let [avaimet [(:nimi @valittu-raporttityyppi) (:nimi p)]
        paivita! #(do (swap! muistetut-parametrit
                             update-in avaimet not)
                      (reset! arvo
                              {(or (tyomaakokousraportit (:nimi p))
                                   (:nimi p)) (get-in @muistetut-parametrit [(:nimi @valittu-raporttityyppi) (:nimi p)])}))]
    [:div
     [yleiset/raksiboksi (:nimi p) (get-in @muistetut-parametrit avaimet) paivita! nil false]]))



(defmethod raportin-parametri :default [p arvo]
  [:span (pr-str p)])

;; Tarkistaa raporttityypin mukaan voiko näillä parametreilla suorittaa
(defmulti raportin-voi-suorittaa? (fn [raporttityyppi parametrit] (:nimi raporttityyppi)))

(defmethod raportin-voi-suorittaa? :tyomaakokous [_ parametrit]
  (some #(get parametrit %) (vals tyomaakokousraportit)))

;; Oletuksena voi suorittaa, jos ei raporttikohtaista sääntöä ole
(defmethod raportin-voi-suorittaa? :default [_ _] true)

(def parametrien-jarjestys
  ;; Koska parametreillä ei ole mitään järjestysnumeroa
  ;; annetaan osalle sellainen, että esim. kuukauden hoitokausi
  ;; ei tule hoitokausivalinnan yläpuolelle.
  {"aikavali" 1
   "urakan-toimenpide" 3})

(def omalle-riville? #{"checkbox" "aikavali" "urakoittain"})

(defn raportin-parametrit [raporttityyppi konteksti v-ur v-hal]
  (let [parametri-arvot (atom {})
        ]
    (reset! suoritettu-raportti nil)
    (komp/luo
      (fn [raporttityyppi konteksti v-ur v-hal]
         (let [parametrit (sort-by #(or (parametrien-jarjestys (:tyyppi %))
                                        100)
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
               voi-suorittaa? (and (not (contains? arvot-nyt :virhe))
                                   (raportin-voi-suorittaa? raporttityyppi arvot-nyt))
               _ (log "Arvot: " (pr-str arvot-nyt) ", parametrit: " (pr-str parametrit))]

           ;; Jos parametreja muutetaan tai ne vaihtuu lomakkeen vaihtuessa, tyhjennä suoritettu raportti

           [:span
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
                                                 {:class (if (omalle-riville? (:tyyppi p))
                                                           "col-md-12"
                                                           "col-md-4")}
                                                 [raportin-parametri p arvo]]]
                      (cond
                        ;; checkboxit ja aikaväli aina omalle riville
                        (omalle-riville? (:tyyppi p))
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
                               parametrit)))))))

            [:div.row
             [:div.col-md-12
              [:div.raportin-toiminnot

               [:form {:target "_blank" :method "POST" :id "raporttipdf"
                       :action (k/pdf-url :raportointi)}
                [:input {:type  "hidden" :name "parametrit"
                         :value ""}]
                [:button.nappi-ensisijainen.pull-right
                 {:type     "submit"
                  :disabled (not voi-suorittaa?)
                  :on-click #(do
                              (let [input (-> js/document
                                              (.getElementById "raporttipdf")
                                              (aget "parametrit"))
                                    parametrit (case konteksti
                                                 "koko maa" (raportit/suorita-raportti-koko-maa-parametrit (:nimi raporttityyppi) arvot-nyt)
                                                 "hallintayksikko" (raportit/suorita-raportti-hallintayksikko-parametrit (:id v-hal) (:nimi raporttityyppi) arvot-nyt)
                                                 "urakka" (raportit/suorita-raportti-urakka-parametrit (:id v-ur) (:nimi raporttityyppi) arvot-nyt))]
                                (set! (.-value input)
                                      (t/clj->transit parametrit)))
                              true)}
                 (ikonit/print) " Tallenna PDF"]]
               [napit/palvelinkutsu-nappi " Tee raportti"
                #(go (reset! suoritettu-raportti :ladataan)
                     (let [raportti (<! (case konteksti
                                          "koko maa" (raportit/suorita-raportti-koko-maa (:nimi raporttityyppi)
                                                                                         arvot-nyt)
                                          "hallintayksikko" (raportit/suorita-raportti-hallintayksikko (:id v-hal)
                                                                                                       (:nimi raporttityyppi) arvot-nyt)
                                          "urakka" (raportit/suorita-raportti-urakka (:id v-ur)
                                                                                     (:nimi raporttityyppi)
                                                                                     arvot-nyt)))]
                       (if-not (k/virhe? raportti)
                         (reset! suoritettu-raportti raportti)
                         (do
                           (reset! suoritettu-raportti nil)
                           raportti))))
                {:ikoni    [ikonit/list]
                 :disabled (not voi-suorittaa?)}]]]]])))))

(defn raporttivalinnat []
  (komp/luo
    (fn []
      (let [v-ur @nav/valittu-urakka
            v-hal @nav/valittu-hallintayksikko
            konteksti (cond
                        v-ur "urakka"
                        v-hal "hallintayksikko"
                        :default "koko maa")]
        [:div.raporttivalinnat
         [:h3 "Raportin tiedot"]
         [yleiset/tietoja {}
          "Kohde" (case konteksti
                    "urakka" "Urakka"
                    "hallintayksikko" "Hallintayksikkö"
                    "koko maa" "Koko maa")
          "Urakka" (when (= "urakka" konteksti)
                     (:nimi v-ur))
          "Hallintayksikkö" (when (= "hallintayksikko" konteksti)
                                (:nimi v-hal))
          "Raportti" [livi-pudotusvalikko {:valinta    @valittu-raporttityyppi
                                           ;;\u2014 on väliviivan unikoodi
                                           :format-fn  #(if % (:kuvaus %) "Valitse")
                                           :valitse-fn #(reset! valittu-raporttityyppi %)
                                           :class      "valitse-raportti-alasveto"}
            @mahdolliset-raporttityypit]]
         
         (when @valittu-raporttityyppi
           [:div.raportin-asetukset
            [raportin-parametrit @valittu-raporttityyppi konteksti v-ur v-hal]])]))))

(defn raporttivalinnat-ja-raportti []
  (let [v-ur @nav/valittu-urakka
        hae-urakan-tyot (fn [ur]
                          (log "[RAPORTTI] Haetaan urakan yks. hint. ja kok. hint. työt")
                          (go (reset! u/urakan-kok-hint-tyot (<! (kok-hint-tyot/hae-urakan-kokonaishintaiset-tyot ur))))
                          (go (reset! u/urakan-yks-hint-tyot
                                      (s/prosessoi-tyorivit ur
                                                            (<! (yks-hint-tyot/hae-urakan-yksikkohintaiset-tyot (:id ur)))))))]

    (when v-ur (hae-urakan-tyot @nav/valittu-urakka)) ; FIXME Tämä on kopioitu suoraan views.urakka-namespacesta.
                                                      ; Yritin siirtää urakka-namespaceen yhteyseksi, mutta tuli circular dependency. :(
                                                      ; Toimisko paremmin jos urakan yks. hint. ja kok. hint. työt käyttäisi
                                                      ; reactionia(?) --> ajettaisiin aina kun urakka vaihtuu
    [:span
     [raporttivalinnat]
     (let [r @suoritettu-raportti]
       (cond (= :ladataan r)
             [yleiset/ajax-loader "Raporttia suoritetaan..."]

             (not (nil? r))
             [raportti/muodosta-html r]))]))

(defn raportit []
  (komp/luo
   (komp/sisaan #(when (nil? @raporttityypit)
                   (go (reset! raporttityypit (<! (raportit/hae-raportit))))))
   (komp/sisaan-ulos #(do
                       (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                       (nav/vaihda-kartan-koko! :M))
                     #(nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko))
    (fn []
      (if (roolit/voi-nahda-raportit?)
        [:span
         [kartta/kartan-paikka]
         (raporttivalinnat-ja-raportti)]
        [:span "Sinulla ei ole oikeutta tarkastella raportteja."]))))
