(ns harja.views.raportit
  "Harjan raporttien pääsivu."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.domain.urakka :as urakka-domain]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as ku]
            [harja.pvm :as pvm]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [livi-pudotusvalikko vihje] :as yleiset]
            [harja.tiedot.raportit :as raportit]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.views.kartta :as kartta]
            [harja.domain.laadunseuranta.laatupoikkeama :as laatupoikkeamat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.ui.raportti :as raportti]
            [harja.transit :as tr]
            [alandipert.storage-atom :refer [local-storage]]
            [clojure.string :as str]
            [clojure.set :as set]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.domain.raportointi :as raportti-domain]
            [harja.tiedot.hallintayksikot :as hy]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [cljs-time.core :as t]
            [harja.fmt :as fmt]
            [harja.ui.viesti :as viesti]
            [harja.ui.kentat :as kentat])
  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-raporttityyppi-nimi (reaction (nav/valittu-valilehti :raportit)))
(defn- valitse-raporttityyppi! [nimi]
  (nav/aseta-valittu-valilehti! :raportit nimi))

(defn- raportin-sort-avain
  "Raportin sort avain. Kuvauksen mukaan aakkosjärjestyksessä, paitsi työmaakokous ensin."
  [{kuvaus :kuvaus}]
  (cond
    (= kuvaus "Työmaakokousraportti") ""
    :default kuvaus))

;; Mäppi raporttityyppejä, haetaan ensimmäisellä kerralla kun raportointiin tullaan
(defonce raporttityypit (atom nil))

(def mahdolliset-raporttityypit
  (reaction (let [v-ur @nav/valittu-urakka
                  v-hal @nav/valittu-hallintayksikko
                  v-urakkatyyppi #{(:arvo @nav/urakkatyyppi)}
                  ;; vesiväylä-urakkatyypillä toistaiseksi tunnistetaan kanava vs. vesiväylät hallintayksikön nimestä
                  v-urakkatyyppi (if (= :vesivayla (first v-urakkatyyppi))
                                   (if (= (:nimi v-hal) "Kanavat ja avattavat sillat")
                                     urakka-domain/kanava-urakkatyypit
                                     urakka-domain/vesivayla-urakkatyypit-ilman-kanavia)
                                   v-urakkatyyppi)
                  salli-laaja-konteksti? (raportti-domain/nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit?)

                  mahdolliset-kontekstit (if salli-laaja-konteksti?
                                           (into #{"koko maa"}
                                                 (keep identity [(when v-ur "urakka")
                                                                 (when v-hal "hallintayksikko")]))

                                           #{"urakka"})
                  urakkatyypin-raportit (filter
                                          #(set/subset? v-urakkatyyppi (:urakkatyyppi %))
                                          (vals @raporttityypit))]

              (if (and (not salli-laaja-konteksti?) (nil? v-ur))
                nil
                (sort-by raportin-sort-avain
                         (into []
                               (comp (filter #(some mahdolliset-kontekstit (:konteksti %)))
                                     (filter #(oikeudet/voi-lukea?
                                               (oikeudet/raporttioikeudet (:kuvaus %))
                                               (:id v-ur))))
                               urakkatyypin-raportit))))))

(def valittu-raporttityyppi
  (reaction (when-let [raporttityypit @raporttityypit]
              (let [valittu-raportti (get raporttityypit @valittu-raporttityyppi-nimi)]
                (when ((set @mahdolliset-raporttityypit) valittu-raportti) valittu-raportti)))))

(def muistetut-parametrit (local-storage (atom {}) :raportin-muistetut-parametrit))

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


(defonce hoitourakassa? (reaction (#{:hoito :teiden-hoito} (:tyyppi @nav/valittu-urakka))))

(defonce valittu-urakkatyyppi (reaction (:arvo @nav/urakkatyyppi)))

(defonce vesivaylaurakassa? (reaction (urakka-domain/vesivayla-urakkatyypit (:tyyppi @nav/valittu-urakka))))

(defonce valittu-hoitokausi (reaction-writable
                              (when (#{:hoito :teiden-hoito} @valittu-urakkatyyppi)
                                (if @hoitourakassa?
                                  @u/valittu-hoitokausi
                                  (pvm/paivamaaran-hoitokausi (pvm/nyt))))))

(def valittu-vuosi (reaction-writable
                     (if (#{:hoito :teiden-hoito} @valittu-urakkatyyppi)
                       nil
                       ;; Ylläpidossa vuosi-valintaa käytetään kk:n valitsemiseen,
                       ;; joten valitaan oletukseksi tämä vuosi
                       (pvm/vuosi (pvm/nyt)))))

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
                             (pvm/aikavalin-kuukausivalit hk)

                             vuosi
                             (pvm/vuoden-kuukausivalit vuosi)

                             :default
                             []))))))

(defonce valittu-kuukausi (reaction-writable @u/valittu-hoitokauden-kuukausi))
(defonce vapaa-aikavali? (atom false))
(defonce vapaa-aikavali (atom [nil nil]))

(defn vain-hoitokausivalinta? [raportti]
  (#{:suolasakko} raportti))

(defn vain-kuukausivalinta? [raportti urakka-valittu?]
  ;; Näytetään vain kuukausivalinta, jos kyseessä on työmaakokous
  ;; TAI jos kyseessä on tarkastusraportti, eikä ole valittu urakkaa.
  (or (#{:tyomaakokous} raportti)
      (and
        (not urakka-valittu?)
        (#{:kelitarkastusraportti
          :laaduntarkastusraportti
          :laatupoikkeamaraportti
          :soratietarkastusraportti
          :tiestotarkastusraportti} raportti))))

(defonce paivita-aikavalinta
  (run! (let [hk @valittu-hoitokausi
              vuosi @valittu-vuosi
              kk @valittu-kuukausi
              vapaa-aikavali? @vapaa-aikavali?
              aikavali @vapaa-aikavali
              vain-hoitokausivalinta? (vain-hoitokausivalinta?
                                        (:nimi @valittu-raporttityyppi))
              vain-kuukausivalinta? (vain-kuukausivalinta? (:nimi @valittu-raporttityyppi)
                                                           (and @nav/valittu-urakka @nav/valittu-hallintayksikko))
              [alku loppu] (cond
                             vain-kuukausivalinta? kk
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

;; Sovittu asiakaspalaverissa että tarjotaan 7 vuotta, ja jos jokin rapsa ei hyvin
;; toimi, ei se ole niin vaarallista. Voidaan tarvittaessa myöh. korjailla yksittäisiä
;; raportteja. Syytä tarjota 7v koska on 7-vuotisia urakoita.
(def +raportin-aikavalin-max-pituus-vuotta+ 7)

(defmethod raportin-parametri "aikavali" [p arvo]
  ;; Näytetään seuraavat valinnat
  ;; - vuosi (joko urakkavuodet tai generoitu lista)
  ;; - hoitokaudet (joko urakan hoitokaudet tai generoitu lista, vain jos valittuna hoito-tyyppi)
  ;; - kuukausi (valitun urakan tai hoitokauden kuukaudet, tai kaikki)
  ;; - vapaa tekstisyöttö aikavälille
  ;;
  ;; Jos valittuna on urakka, joka ei ole tyyppiä hoito,
  ;; ei näytetä hoitokausivalintaa.
  (let [ur @nav/valittu-urakka
        hoitourakassa? @hoitourakassa?
        vesivaylaurakassa? @vesivaylaurakassa?
        urakkatyyppi @valittu-urakkatyyppi
        hal @nav/valittu-hallintayksikko
        vuosi-eka (if ur
                    (pvm/vuosi (:alkupvm ur))
                    2010)
        vuosi-vika (if ur
                     (pvm/vuosi (:loppupvm ur))
                     (pvm/vuosi (pvm/nyt)))
        vain-hoitokausivalinta? (vain-hoitokausivalinta? (:nimi @valittu-raporttityyppi))
        vain-kuukausivalinta? (vain-kuukausivalinta? (:nimi @valittu-raporttityyppi) ur)
        korkeintaan-edellinen-paiva (fn [uusi-paiva]
                                      (not (pvm/sama-tai-jalkeen? uusi-paiva (pvm/nyt) true)))
        hoitokauden-pvm-vali (if (or hoitourakassa? vesivaylaurakassa?)
                               (u/hoito-tai-sopimuskaudet ur)
                               (u/edelliset-hoitokaudet 5 false urakkatyyppi))
        ;; Materiaaliraportissa ei näytetä meneillään olevaa päivää
        pvm-rajattu-eiliseen? (boolean (#{:materiaaliraportti :pohjavesialueiden-suolatoteumat} (:nimi @valittu-raporttityyppi)))]
    [:span
     [:div.raportin-vuosi-hk-kk-valinta
      [ui-valinnat/vuosi {:disabled
                          (or @vapaa-aikavali?
                              vain-hoitokausivalinta?
                              (and vain-kuukausivalinta?
                                   ;; Hoidossa /MHU:Ssa valitaan ensin hoitokausi, ja se määrää minkä vuoden
                                   ;; kuukauden voi valita.
                                   ;; Ylläpidossa ei ole hoitokausivalintaa, joten on pakko valita
                                   ;; ensin vuosi, joka sitten taas määrää minkä vuoden kuukauden voi valita.
                                   ;; Tästä syystä, jos vain-kuukausivalinta on tosi,
                                   ;; disabloidaan vuosi-valinta vain hoidon urakoille
                                   (#{:hoito :teiden-hoito} urakkatyyppi)))}
       vuosi-eka vuosi-vika valittu-vuosi
       #(do
         (reset! valittu-vuosi %)
         (reset! valittu-hoitokausi nil)
         (reset! valittu-kuukausi nil))]
      ;; Erikoiskeissi, miksi hassunnäköisiä ehtoja:
      ;; Jos valittuna on koko maa tai hallintayksikkö, mutta ei urakkaa, silloin
      ;; urakkatyyppi (alasvetovalinnasta) on :hoito, mutta hoitourakassa? saa arvon null
      ;; Jos taas on valittu yksittäinen MHU urakka, urakkatyyppi on :teiden-hoito, ja hoitourakassa? totuudellinen
      (when (or (and (#{:hoito :teiden-hoito} urakkatyyppi)
                     (or hoitourakassa? (nil? ur)))
                (and (= urakkatyyppi :vesivayla)
                     (or vesivaylaurakassa? (nil? ur))))
        [ui-valinnat/hoitokausi
         {:disabled @vapaa-aikavali?
          :disabloi-tulevat-hoitokaudet? true}
         hoitokauden-pvm-vali
         valittu-hoitokausi
         #(do
           (reset! valittu-hoitokausi %)
           (reset! valittu-vuosi nil)
           (reset! valittu-kuukausi nil))])
      [ui-valinnat/kuukausi {:disabled (or @vapaa-aikavali?
                                           vain-hoitokausivalinta?)
                             :nil-valinta (cond
                                            vain-kuukausivalinta?
                                            "Valitse kuukausi"

                                            @valittu-vuosi
                                            "Koko vuosi"

                                            :else
                                            "Koko hoitokausi")
                             :disabloi-tulevat-kk? true}
       (cond-> @kuukaudet
               vain-kuukausivalinta? rest)
       valittu-kuukausi]]

     (when-not (or vain-hoitokausivalinta? vain-kuukausivalinta?)
       [:div.raportin-valittu-aikavali
        [yleiset/raksiboksi {:teksti "Valittu aikaväli"
                             :toiminto #(swap! vapaa-aikavali? not)
                             :komponentti (when @vapaa-aikavali?
                                            [:div
                                             [ui-valinnat/aikavali vapaa-aikavali {:aikavalin-rajoitus [+raportin-aikavalin-max-pituus-vuotta+ :vuosi]
                                                                                   :validointi (if pvm-rajattu-eiliseen?
                                                                                                 korkeintaan-edellinen-paiva
                                                                                                 :korkeintaan-kuluva-paiva)}]
                                             [vihje (str "Raportin pisin sallittu aikaväli on " +raportin-aikavalin-max-pituus-vuotta+ " vuotta") "raportit-valittuaikavali-vihje"]])}
         @vapaa-aikavali?]])]))

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
      (fn [_ _]
        @u/valittu-toimenpideinstanssi
        [valinnat/urakan-toimenpide+kaikki]))))

(defmethod raportin-parametri "urakan-tehtava" [p arvo]
  (let [aseta-tpk (fn [tpk]
                    (reset! arvo (if tpk
                                   {:tehtava-id (:t4_id tpk)}
                                   {:virhe "Ei tehtävävalintaa"})))]
    (komp/luo
      (komp/watcher u/valittu-tehtava
                    (fn [_ _ tpk]
                      (aseta-tpk tpk)))
      (fn [_ _]
        @u/valittu-tehtava
        [valinnat/urakan-tehtava+kaikki]))))

(defmethod raportin-parametri "kanavaurakan-kohde" [p arvo]
  (let [aseta-kohde (fn [kohde]
                    (reset! arvo (if kohde
                                   {:kohde-id (:harja.domain.kanavat.kohde/id kohde)}
                                   {:virhe "Ei kohdevalintaa"})))]
    (komp/luo
      (komp/watcher ku/valittu-kohde
                    (fn [_ _ kohde]
                      (aseta-kohde kohde)))
      (fn [_ _]
        @ku/valittu-kohde
        [valinnat/kanavaurakan-kohde+kaikki]))))

(defonce urakoittain? (atom false))

(defmethod raportin-parametri "urakoittain" [p arvo]
  (if @nav/valittu-urakka
    [:span]
    [:div.urakoittain
     [yleiset/raksiboksi {:teksti (:nimi p)
                          :toiminto #(do (swap! urakoittain? not)
                                         (reset! arvo
                                                 {:urakoittain? @urakoittain?}))}
      @urakoittain?]]))

(defonce valittu-muutostyotyyppi (atom nil))

(defmethod raportin-parametri "muutostyotyyppi" [p arvo]
  [ui-valinnat/muutostyon-tyyppi (cons nil toteumat/+muun-tyon-tyypit+)
   valittu-muutostyotyyppi
   (fn [uusi]
     (do
       (reset! valittu-muutostyotyyppi uusi)
       (reset! arvo {:muutostyotyyppi uusi})))])

(def laatupoikkeama-tekija (atom :kaikki))

(defmethod raportin-parametri "laatupoikkeamatekija" [p arvo]
  (reset! arvo {:laatupoikkeamatekija @laatupoikkeama-tekija})
  (fn []
    [yleiset/pudotusvalikko
     "Tekijä"
     {:valinta @laatupoikkeama-tekija
      :valitse-fn #(do (reset! laatupoikkeama-tekija %)
                       (reset! arvo {:laatupoikkeamatekija %}))
      :format-fn #(case %
                   :kaikki "Kaikki"
                   (laatupoikkeamat/kuvaile-tekija %))}

     [:kaikki :urakoitsija :tilaaja :konsultti]]))

(def silta (atom :kaikki))
(def urakan-sillat (reaction<! [nakymassa? @raportit/raportit-nakymassa?
                                urakka @nav/valittu-urakka]
                               {:nil-kun-haku-kaynnissa? true}
                               (let [oikeus? (oikeudet/urakat-laadunseuranta-siltatarkastukset (:id urakka))]
                                 (when (and urakka nakymassa? oikeus?)
                                  (k/post! :hae-urakan-sillat
                                           {:urakka-id (:id urakka)
                                            :listaus :kaikki})))))

(defmethod raportin-parametri "silta" [p arvo]
  (reset! arvo {:silta-id (if (= @silta :kaikki)
                            :kaikki
                            (:id @silta))})
  (fn []
    [yleiset/pudotusvalikko
     "Silta"
     {:valinta @silta
      :valitse-fn #(do (reset! silta %)
                       (reset! arvo {:silta-id (if (= :kaikki %)
                                                 :kaikki
                                                 (:id %))}))
      :format-fn #(case %
                    :kaikki (if (empty? @urakan-sillat) "Ei siltoja" "Kaikki")
                    (str (:siltanimi %) " (" (:siltatunnus %) ")"))}

     (into [] (cons :kaikki (sort-by :siltanimi @urakan-sillat)))]))

(def urakan-vuodet (reaction
                     (let [urakka @nav/valittu-urakka]
                       (if urakka
                         (mapv
                           #(t/year (first %))
                           (reverse (pvm/urakan-vuodet (:alkupvm urakka) (:loppupvm urakka))))
                         (pvm/edelliset-n-vuosivalia 10)))))

(defonce valitse-vuosi-kun-urakka-muuttuu
  (run! (swap! parametri-arvot assoc-in ["Vuosi" :vuosi]
               (let [nykyinen-vuosi (pvm/vuosi (pvm/nyt))
                     urakan-vuodet @urakan-vuodet]
                 (if (some #(= % nykyinen-vuosi) urakan-vuodet)
                   nykyinen-vuosi
                   (first urakan-vuodet))))))

(defmethod raportin-parametri "urakan-vuosi" [p arvo]
  [yleiset/pudotusvalikko
   "Vuosi"
   {:valinta    (:vuosi @arvo)
    :valitse-fn #(reset! arvo {:vuosi %})}
   @urakan-vuodet])

(def tyomaakokousraportit
  {"Erilliskustannukset" :erilliskustannukset
   "Ilmoitukset" :ilmoitusraportti
   "Kelitarkastusraportti" :kelitarkastusraportti
   "Laaduntarkastusraportti" :laaduntarkastusraportti
   "Laatupoikkeamat" :laatupoikkeamaraportti
   "Laskutusyhteenveto" :laskutusyhteenveto
   "Materiaaliraportti" :materiaaliraportti
   "Muutos- ja lisätyöt" :muutos-ja-lisatyot
   "Sanktioiden yhteenveto" :sanktioraportti
   "Soratietarkastukset" :soratietarkastusraportti
   "Tiestötarkastukset" :tiestotarkastusraportti
   "Turvallisuusraportti" :turvallisuus
   "Yksikköhintaiset työt kuukausittain" :yks-hint-kuukausiraportti
   "Yksikköhintaiset työt päivittäin" :yksikkohintaiset-tyot
   "Yksikköhintaisten töiden raportti" :yksikkohintaiset-tyot
   "Yksikköhintaiset työt tehtävittäin" :yks-hint-tehtavien-summat
   "Ympäristöraportti" :ymparisto
   "Toimenpiteiden ajoittuminen" :toimenpideajat})

(defmethod raportin-parametri "checkbox" [p arvo]
  (let [avaimet [(:nimi @valittu-raporttityyppi)
                 (or (tyomaakokousraportit (:nimi p)) (:nimi p))]
        paivita! #(do (swap! muistetut-parametrit
                             update-in avaimet not)
                      (reset! arvo
                              {(or (tyomaakokousraportit (:nimi p))
                                   (:nimi p)) (get-in @muistetut-parametrit [(:nimi @valittu-raporttityyppi) (:nimi p)])}))]
    [:div
     [yleiset/raksiboksi {:teksti (:nimi p)
                          :toiminto paivita!}
      (get-in @muistetut-parametrit avaimet)]]))

(defmethod raportin-parametri "hoitoluokat" [p arvo]
  (komp/luo
    (komp/sisaan #(reset! arvo {:hoitoluokat (or (get @muistetut-parametrit :hoitoluokat)
                                                 (into #{}
                                                       (map :numero hoitoluokat/hoitoluokkavaihtoehdot-raporteille)))}))
    (fn [p arvo]
      [:div.hoitoluokat
       [yleiset/otsikolla "Hoitoluokat"
        (let [arvo-nyt @arvo
              valitut (:hoitoluokat arvo-nyt)]
          (vec (concat
                 [yleiset/rivi {:koko "col-sm-2"}]
                 (for [sarake (partition 3 hoitoluokat/hoitoluokkavaihtoehdot-raporteille)]
                   ^{:key (:numero (first sarake))}
                   [:div.inline
                    (for [{:keys [nimi numero]} sarake
                          :let [valittu? (valitut numero)]]
                      ^{:key numero}
                      [yleiset/raksiboksi {:teksti nimi
                                           :toiminto #(let [uusi-arvo {:hoitoluokat
                                                                       ((if valittu? disj conj) valitut numero)}]
                                                       (reset! arvo uusi-arvo)
                                                       (swap! muistetut-parametrit merge uusi-arvo))}
                       valittu?])]))))]])))

(defmethod raportin-parametri :default [p arvo]
  (if (keyword? (:tyyppi p))
    ;; Tehdään suoraan lomake kenttä annetuilla spekseillä
    (let [nimi (:nimi p)]
      [:div.label-ja-kentta
       [:span (:otsikko p)]
       [kentat/tee-kentta p (r/wrap (nimi @arvo)
                                    #(swap! arvo merge {nimi %}))]])

    [:span (pr-str p)]))

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

(def parametri-omalle-riville? #{"aikavali" "urakoittain" "tienumero"})

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
                                    (tr/clj->transit parametrit))
                              true))]
    [:span
     (for [[ikoni teksti id url] yleiset/+raportin-vientimuodot+]
       ^{:key id}
       [:form {:target "_blank" :method "POST" :id id
               :style {:display "inline"}
               :action url}
        [:input {:type "hidden" :name "parametrit"
                 :value ""}]
        [:button.nappi-ensisijainen.pull-right
         {:type "submit"
          :disabled (not voi-suorittaa?)
          :on-click #(aseta-parametrit! id)}
         ikoni " " teksti]])]))

(defn- suorita-raportti! [suorituksen-parametrit]
  (go
    (log "SUORITA-RAPORTTI! " (pr-str suorituksen-parametrit))
    (reset! raportit/suoritettu-raportti :ladataan)
    (let [[konteksti nimi arvot-nyt v-ur v-hal] suorituksen-parametrit
          raportti (<! (case konteksti
                         "koko maa"
                         (raportit/suorita-raportti-koko-maa nimi arvot-nyt)
                         "hallintayksikko"
                         (raportit/suorita-raportti-hallintayksikko v-hal nimi arvot-nyt)
                         "urakka"
                         (raportit/suorita-raportti-urakka v-ur nimi arvot-nyt)))]
      (log "[RAPORTTI] Raportin suoritus valmis")
      (cond
        (not= @raportit/suoritettu-raportti :ladataan)
        (do (log "[RAPORTTI] Poistuttu latausnäkymästä, hylätään suoritettu raportti.")
            raportti)

        (not= @raportit/suorituksessa-olevan-raportin-parametrit suorituksen-parametrit)
        (do (log "[RAPORTTI] Suoritettu raportti oli muu kuin mitä käyttäjä viimeksi pyysi, hylätään raportti")
            raportti)

        (k/virhe? raportti)
        (do
          (viesti/nayta! "Raportin suoritus epäonnistui." :warning viesti/viestin-nayttoaika-lyhyt)
          (reset! raportit/suorituksessa-olevan-raportin-parametrit nil)
          (reset! raportit/suoritettu-raportti nil)
          raportti)

        :default
        (do (reset! raportit/suoritettu-raportti raportti)
            (when-not (= :raportoinnissa-ruuhkaa raportti)
              (reset! raportit/suorituksessa-olevan-raportin-parametrit nil)))))))

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
                         {:urakkatyyppi (:arvo @nav/urakkatyyppi)}
                         (when (some? (:testiversio? raporttityyppi))
                           {:testiversio? (:testiversio? raporttityyppi)}))
        voi-suorittaa? (and (not (contains? arvot-nyt :virhe))
                            (raportin-voi-suorittaa? raporttityyppi arvot-nyt))
        raportissa? (some? @raportit/suoritettu-raportti)]

    ;; Jos parametreja muutetaan tai ne vaihtuu lomakkeen vaihtuessa, tyhjennä suoritettu raportti
    (log "RAPORTIN-PARAMETRIT NYT: " (pr-str arvot-nyt))
    [:span
     (when-not raportissa?
       (map-indexed
         (fn [i cols]
           ^{:key i}
           [:div.row (seq cols)])
         (loop [rows []
                row nil
                [p & parametrit] (filter :tyyppi parametrit)] ;; Kaikilla rapsoilla ei parametreja, älä näytä tyhjiä

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
           #(go
             (reset! raportit/suoritettu-raportti :ladataan)
             (let [suorituksen-parametrit [konteksti
                                           (:nimi raporttityyppi)
                                           arvot-nyt
                                           (:id v-ur)
                                           (:id v-hal)]]
               (reset! raportit/suorituksessa-olevan-raportin-parametrit suorituksen-parametrit)
               (<! (suorita-raportti! suorituksen-parametrit))))
           {:ikoni [ikonit/list]
            :disabled (not voi-suorittaa?)}])]]]]))

(defn hallintayksikko-ja-urakkatyyppi [v-hal v-ur-tyyppi]
  (let [vesivaylien-urakkatyypissa? (= :vesivayla (:arvo v-ur-tyyppi))]
    [:span
    [yleiset/livi-pudotusvalikko
     {:valitse-fn nav/valitse-hallintayksikko!
      :valinta v-hal
      :class "raportti-alasveto"
      :format-fn (fnil hy/elynumero-ja-nimi {:nimi (if vesivaylien-urakkatyypissa?
                                                     "Valitse hallintayksikkö"
                                                     (if (raportti-domain/nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit?)
                                                         "Kaikki ELYt"
                                                         "Valitse ELY"))})}
     (concat (if (and
                   (raportti-domain/nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit?)
                   ;; vesiväylille ei haluta "Kaikki ELYt" vaihtoehtoa
                   (not vesivaylien-urakkatyypissa?))
               [nil]
               [])
             @hy/vaylamuodon-hallintayksikot)]
    " "
    [yleiset/livi-pudotusvalikko
     {:valitse-fn nav/vaihda-urakkatyyppi!
      :valinta v-ur-tyyppi
      :class "raportti-alasveto"
      :format-fn :nimi}
     nav/+urakkatyypit-ja-kaikki+]]))

(defn ei-raportteja-saatavilla-viesti [urakkatyyppi valittu-urakka]
  (if (and (nil? valittu-urakka)
           (or (not (raportti-domain/nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit?))
               (= urakkatyyppi "vesiväylät")))              ;Vesiväylissä raportteja toistaiseksi vain urakkatasolla
    (str "Valitse hallintayksikkö ja urakka nähdäksesi raportit")
    (str "Ei raportteja saatavilla urakkatyypissä " urakkatyyppi)))

(defn raporttivalinnat []
  (komp/luo
    ;; Ei tällä hetkellä raporteissa sallita urakoitsijavalintaa
    (komp/sisaan #(nav/valitse-urakoitsija! nil))
    (fn []
      (let [v-ur @nav/valittu-urakka
            v-ur-tyyppi @nav/urakkatyyppi
            v-hal @nav/valittu-hallintayksikko
            urakan-nimen-pituus 36
            konteksti (cond
                        v-ur "urakka"
                        v-hal "hallintayksikko"
                        :default "koko maa")
            raportissa? (some? @raportit/suoritettu-raportti)
            raporttilista @mahdolliset-raporttityypit]
        [:div.raporttivalinnat
         (when-not raportissa?
           [:span
            [:h3 "Raportin tiedot"]
            [yleiset/tietoja {:class "border-bottom"}
             "Hallintayksikkö" [hallintayksikko-ja-urakkatyyppi v-hal v-ur-tyyppi]
             "Urakka" (when v-hal
                        [yleiset/livi-pudotusvalikko
                         {:valitse-fn     nav/valitse-urakka!
                          :valinta        v-ur
                          :class          "raportti-alasveto"
                          :nayta-ryhmat   [:kaynnissa :paattyneet]
                          :ryhmittely     (let [nyt (pvm/nyt)]
                                            #(if (pvm/jalkeen? nyt (:loppupvm %))
                                              :paattyneet
                                              (when (pvm/jalkeen? nyt (:alkupvm %))
                                                :kaynnissa)))
                          :ryhman-otsikko #(case %
                                            :kaynnissa "Käynnissä olevat urakat"
                                            :paattyneet "Päättyneet urakat")
                          :format-fn      (fnil (comp
                                                  (partial fmt/lyhennetty-urakan-nimi urakan-nimen-pituus)
                                                  :nimi)
                                                {:nimi (if (raportti-domain/nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit?)
                                                         "Kaikki urakat"
                                                         "Valitse urakka")})}
                         (concat (if (raportti-domain/nykyinen-kayttaja-voi-nahda-laajemman-kontekstin-raportit?)
                                   [nil]
                                   [])
                                 (map
                                   #(assoc % :nimi
                                             (fmt/lyhennetty-urakan-nimi urakan-nimen-pituus (:nimi %)))
                                   (sort-by :nimi @nav/suodatettu-urakkalista)))])
             "Raportti" (cond
                          (nil? @raporttityypit)
                          [:span "Raportteja haetaan..."]
                          (empty? raporttilista)
                          [:span (ei-raportteja-saatavilla-viesti (str/lower-case (:nimi v-ur-tyyppi)) v-ur)]
                          :default
                          [livi-pudotusvalikko {:valinta @valittu-raporttityyppi
                                                ;;\u2014 on väliviivan unikoodi
                                                :format-fn #(if % (str
                                                                    (:kuvaus %)
                                                                    (if (:testiversio? %)
                                                                      " - TESTIVERSIO"
                                                                      "")) "Valitse")
                                                :valitse-fn #(valitse-raporttityyppi! (:nimi %))
                                                :class "raportti-alasveto"
                                                :li-luokka-fn #(if (= "Työmaakokousraportti" (:kuvaus %))
                                                                "tyomaakokous"
                                                                "")}
                           raporttilista])]])

         (when @valittu-raporttityyppi
           [:div.raportin-asetukset
            [raportin-parametrit @valittu-raporttityyppi konteksti v-ur v-hal]])]))))

(defn nayta-raportti [tyyppi r]
  (komp/luo
    (fn [tyyppi r]
      [:span
       [raportti/muodosta-html (assoc-in r [1 :tunniste] (:nimi tyyppi))]])))

(defn raporteissa-ruuhkaa []
  (let [yrita-uudelleen? (atom true)
        odota-sekuntia (atom nil)]

    (go (loop [sekunnit 5]
          (if (zero? sekunnit)
            (when @yrita-uudelleen?
              (<! (suorita-raportti! @raportit/suorituksessa-olevan-raportin-parametrit)))
            (do (reset! odota-sekuntia sekunnit)
                (<! (timeout 1000))
                (recur (dec sekunnit))))))

    (komp/luo
     (komp/ulos #(reset! yrita-uudelleen? false))
     (fn []
       [:div "Raportin suoritus epäonnistui, palvelussa on ruuhkaa."
        [:div.yrita-uudestaan "Yritetään uudestaan "
         [:span.yrita-uudestaan-sekunnit @odota-sekuntia]
         " sekunnin kuluttua."]]))))

(defn raporttivalinnat-ja-raportti []
  (let [r @raportit/suoritettu-raportti]
    [:span
     [raporttivalinnat]
     (cond
       (= :ladataan r)
       [yleiset/ajax-loader "Raporttia suoritetaan..."]

       (= :raportoinnissa-ruuhkaa r)
       [raporteissa-ruuhkaa]

       (not (nil? r))
       [nayta-raportti @valittu-raporttityyppi r])]))

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
           [kartta/kartan-paikka])
         [raporttivalinnat-ja-raportti]]
        [:span "Sinulla ei ole oikeutta tarkastella raportteja."]))))
