(ns harja.views.urakka.suunnittelu.kustannussuunnitelma
  (:refer-clojure :exclude [atom])
  (:require [reagent.core :as r :refer [atom]]
            [clojure.string :as clj-str]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma :as t]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.debug :as debug]
            [harja.ui.taulukko.taulukko :as taulukko]
            [harja.ui.taulukko.jana :as jana]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalut]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]))

(defn haitari-laatikko [_ {:keys [alussa-auki? aukaise-fn otsikko-elementti]} & _]
  (let [auki? (atom alussa-auki?)
        otsikko-elementti (or otsikko-elementti :span)
        aukaise-fn! (comp (or aukaise-fn identity)
                          (fn [event]
                            (.preventDefault event)
                            (swap! auki? not)))]
    (fn [otsikko {:keys [id]} & sisalto]
      [:div.haitari-laatikko {:id id}
       [otsikko-elementti {:on-click aukaise-fn!
                           :class "klikattava"}
        otsikko
        (if @auki?
          ^{:key "haitari-auki"}
          [ikonit/livicon-chevron-up]
          ^{:key "haitari-kiinni"}
          [ikonit/livicon-chevron-down])]
       (when @auki?
         (doall (map-indexed (fn [index komponentti]
                               (with-meta
                                 komponentti
                                 {:key index}))
                             sisalto)))])))

(defn hintalaskuri-sarake
  ([yla ala] (hintalaskuri-sarake yla ala nil))
  ([yla ala luokat]
   [:div {:class luokat}
    [:div yla]
    [:div ala]]))

(defn hintalaskuri
  [{:keys [otsikko selite hinnat]}]
  [:div.hintalaskuri
   [:h5 otsikko]
   [:div selite]
   [:div.hintalaskuri-vuodet
    (for [{:keys [summa hoitokausi]} hinnat]
      ^{:key hoitokausi}
      [hintalaskuri-sarake (str hoitokausi ". vuosi" (when (= 1 hoitokausi) "*")) (fmt/euro summa)])
    [hintalaskuri-sarake " " "=" "hintalaskuri-yhtakuin"]
    [hintalaskuri-sarake "Yhteensä" (fmt/euro (reduce #(+ %1 (:summa %2)) 0 hinnat))]]])

(defn aakkosta [sana]
  (get {"kesakausi" "kesäkausi"
        "liikenneympariston hoito" "liikenneympäristön hoito"
        "mhu yllapito" "mhu-ylläpito"
        "paallystepaikkaukset" "päällystepaikkaukset"}
       sana
       sana))

(defn kuluva-hoitovuosi []
  (let [hoitovuoden-pvmt (pvm/paivamaaran-hoitokausi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (-> @tila/yleiset :urakka :alkupvm))
        kuluva-urakan-vuosi (inc (- urakan-aloitusvuosi (pvm/vuosi (first hoitovuoden-pvmt))))]
    (fn []
      [:div#kuluva-hoitovuosi
       [:span
        (str "Kuluva hoitovuosi: " kuluva-urakan-vuosi
             ". (" (pvm/pvm (first hoitovuoden-pvmt))
             " - " (pvm/pvm (second hoitovuoden-pvmt)) ")")]
       [:div.hoitovuosi-napit
        [napit/yleinen-ensisijainen "Laskutus" #(println "Painettiin Laskutus") {:ikoni [ikonit/euro] :disabled true}]
        [napit/yleinen-ensisijainen "Kustannusten seuranta" #(println "Painettiin Kustannusten seuranta") {:ikoni [ikonit/stats] :disabled true}]]])))

(defn tavoite-ja-kattohinta [{:keys [tavoitehinnat kattohinnat]}]
  (if (and tavoitehinnat kattohinnat)
    [:div
     [hintalaskuri {:otsikko "Tavoitehinta"
                    :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
                    :hinnat kattohinnat}]
     [hintalaskuri {:otsikko "Kattohinta"
                    :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
                    :hinnat tavoitehinnat}]]
    [yleiset/ajax-loader]))

(defn suunnitelman-selitteet [luokat]
  [:div#suunnitelman-selitteet {:class (apply str (interpose " " luokat))}
   [:span [ikonit/ok] "Kaikki kentätä täytetty"]
   [:span [ikonit/livicon-question] "Keskeneräinen"]
   [:span [ikonit/remove] "Suunnitelma puuttuu"]])

(defn suunnitelmien-taulukko [e! {:keys [toimenpiteet yhteenveto]}]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-8 col-md-8 col-lg-8"
                               :kuukausisuunnitelmat "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :vuosisuunnittelmat "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        paarivi (fn [nimi ikoni-v ikoni-kk]
                   (jana/->Rivi (keyword nimi)
                                [(osa/->Teksti (keyword (str nimi "-nimi")) (clj-str/capitalize nimi) {:class #{(sarakkeiden-leveys :nimi) "reunaton"}})
                                 (osa/->Ikoni (keyword (str nimi "-vuosisuunnitelmat"))
                                              {:ikoni (ikoni-v)}
                                              {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)
                                                        "reunaton"
                                                        "keskita"}})
                                 (osa/->Ikoni (keyword (str nimi "-kuukausisuunnitelmat"))
                                              {:ikoni (ikoni-kk)}
                                              {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)
                                                        "reunaton"
                                                        "keskita"}})]
                                #{"reunaton"}))
        paarivi-laajenna (fn [nimi]
                            (jana/->Rivi (keyword nimi)
                                         [(osa/->Laajenna (keyword (str nimi "-teksti"))
                                                          (clj-str/capitalize nimi)
                                                          #(e! (t/->LaajennaSoluaKlikattu [:suunnitelmien-tila-taulukko] (keyword nimi) %1 %2))
                                                          {:class #{(sarakkeiden-leveys :nimi)
                                                                    "ikoni-vasemmalle"}})
                                          (osa/->Ikoni (keyword (str nimi "-vuosisuunnitelmat")) {:ikoni ikonit/remove} {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)
                                                                                                                                  "keskita"
                                                                                                                                  "reunaton"}})
                                          (osa/->Ikoni (keyword (str nimi "-kuukausisuunnitelmat")) {:ikoni ikonit/livicon-minus} {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)
                                                                                                                                            "keskita"
                                                                                                                                            "reunaton"}})]
                                         #{"reunaton"}))
        lapsirivi (fn [idn-alku teksti ikoni-v ikoni-kk]
                     (jana/->Rivi (keyword idn-alku)
                                  [(osa/->Teksti (keyword (str idn-alku "-nimi")) teksti {:class #{(sarakkeiden-leveys :nimi)
                                                                                                   "solu-sisenna-1"
                                                                                                   "reunaton"}})
                                   (osa/->Ikoni (keyword (str idn-alku "-vuosisuunnitelmat")) {:ikoni (ikoni-v)} {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)
                                                                                                                           "keskita"
                                                                                                                           "reunaton"}})
                                   (osa/->Ikoni (keyword (str idn-alku "-kuukausisuunnitelmat")) {:ikoni (ikoni-kk)} {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)
                                                                                                                               "keskita"
                                                                                                                               "reunaton"}})]
                                  #{"piillotettu" "reunaton"}))
        otsikkorivi (jana/->Rivi :otsikko-rivi
                                 [(osa/->Komponentti :otsikko-selite suunnitelman-selitteet #{(sarakkeiden-leveys :nimi)})
                                  (osa/->Teksti :otsikko-vuosisuunnitelmat "Vuosisuunnitelmat" {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)
                                                                                                         "keskita"
                                                                                                         "alas"
                                                                                                         "suunnitelman-tila-otsikko"
                                                                                                         "reunaton"}})
                                  (osa/->Teksti :otsikko-kuukausisuunnitelmat "Kuukausisuunnitelmat*" {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)
                                                                                                               "keskita"
                                                                                                                "alas"
                                                                                                                "suunnitelman-tila-otsikko"
                                                                                                               "reunaton"}})]
                                 #{"reunaton"})
        taytettyja-hankintakustannuksia (count (keep :maara yhteenveto))
        hankintakustannukset-ikoni (fn []
                                     (case taytettyja-hankintakustannuksia
                                       0 ikonit/remove
                                       5 ikonit/ok
                                       ikonit/livicon-question))
        hankintakustannukset [(paarivi "hankintakustannukset" hankintakustannukset-ikoni hankintakustannukset-ikoni)]
        toimenpiteiden-rivit (mapcat (fn [[toimenpide suunnitelmat]]
                                       (concat [(paarivi-laajenna (name toimenpide))]
                                               (keep (fn [[suunnitelma maara]]
                                                       (let [idn-osa (str (name toimenpide) "-" (name suunnitelma))
                                                             ikoni-v #(if (nil? maara) ikonit/remove ikonit/ok)
                                                             ikoni-kk #(if (nil? maara) ikonit/remove ikonit/ok)]
                                                         (case suunnitelma
                                                           :hankinnat (with-meta (lapsirivi (str idn-osa "-h") "Suunnitellut hankinnat" ikoni-v ikoni-kk)
                                                                                 {:vanhempi toimenpide})
                                                           :korjaukset (with-meta (lapsirivi (str idn-osa "-k") "Kolmansien osapuolien aiheuttamien vaurioiden korjaukset" ikoni-v ikoni-kk)
                                                                                  {:vanhempi toimenpide})
                                                           :akilliset-hoitotyot (with-meta (lapsirivi (str idn-osa "-ah") "Äkilliset hoitotyöt" ikoni-v ikoni-kk)
                                                                                           {:vanhempi toimenpide})
                                                           :muut-rahavaraukset (with-meta (lapsirivi (str idn-osa "-mr") "Muut tilaajan rahavaraukset" ikoni-v ikoni-kk)
                                                                                          {:vanhempi toimenpide})
                                                           nil)))
                                                    suunnitelmat)))
                                     toimenpiteet)]
    (cons otsikkorivi
          (map-indexed #(update %2 :luokat conj (if (odd? %1) "pariton-jana" "parillinen-jana"))
                       (concat hankintakustannukset toimenpiteiden-rivit)))))

(defn suunnitelmien-tila
  [e! app]
  (komp/luo
    (komp/piirretty (fn [this]
                      (let [suunnitelmien-taulukko-alkutila (suunnitelmien-taulukko e! (:hankintakustannukset app))]
                        (e! (tuck-apurit/->MuutaTila [:suunnitelmien-tila-taulukko] suunnitelmien-taulukko-alkutila)))))
    (fn [e! {:keys [suunnitelmien-tila-taulukko]}]
      (if suunnitelmien-tila-taulukko
        [taulukko/taulukko suunnitelmien-tila-taulukko #{"reunaton"}]
        [yleiset/ajax-loader]))))

(defn hankintojen-filter [e! _]
  (let [toimenpide-tekstiksi (fn [toimenpide]
                               (-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/upper-case))
        valitse-toimenpide (fn [toimenpide]
                             (e! (tuck-apurit/->MuutaTila [:hankintakustannukset :valinnat :toimenpide] toimenpide)))
        valitse-kausi (fn [kausi]
                        (e! (tuck-apurit/->MuutaTila [:hankintakustannukset :valinnat :maksetaan] kausi)))
        kausi-tekstiksi (fn [kausi]
                          (-> kausi name aakkosta clj-str/capitalize))]
    (fn [_ {:keys [toimenpide maksetaan]}]
      (let [toimenpide (toimenpide-tekstiksi toimenpide)]
        [:div
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Toimenpide"]
          [yleiset/livi-pudotusvalikko {:valinta toimenpide
                                        :valitse-fn valitse-toimenpide
                                        :format-fn toimenpide-tekstiksi}
           (sort t/toimenpiteet)]]
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Maksetaan"]
          [yleiset/livi-pudotusvalikko {:valinta maksetaan
                                        :valitse-fn valitse-kausi
                                        :format-fn kausi-tekstiksi}
           [:kesakausi :talvikausi]]]]))))

#_(defn hankintojen-taulukko [e! toimenpiteet
                            {valittu-toimenpide :toimenpide
                             laskutukseen-perustuen? :laskutukseen-perustuen?}
                            on-oikeus? laskutuksen-perusteella-taulukko?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-8 col-md-8 col-lg-8"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        paarivi (fn [nimi yhteensa]
                  (let [nimi-k (-> nimi (clj-str/replace #"ä" "a") (clj-str/replace #"ö" "o"))]
                    (jana/->Rivi (keyword nimi-k)
                                 [(osa/->Teksti (keyword nimi-k) (clj-str/capitalize nimi) {:class #{(sarakkeiden-leveys :nimi) "reunaton"}})
                                  (osa/->Teksti (keyword (str nimi-k "-maara-kk")) "" {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}})
                                  (osa/->Teksti (keyword (str nimi-k "-yhteensa")) yhteensa {:class #{(sarakkeiden-leveys :yhteensa) "reunaton"}})]
                                 #{"reunaton"})))
        paarivi-laajenna (fn [rivi-id vuosi yhteensa]
                           (jana/->Rivi rivi-id
                                        [(with-meta (osa/->Teksti (keyword (str vuosi "-nimi")) (str vuosi ". hoitovuosi") {:class #{(sarakkeiden-leveys :nimi) "reunaton"}})
                                                    {:sarake :nimi})
                                         (with-meta (osa/->Teksti (keyword (str vuosi "-maara-kk")) "" {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}})
                                                    {:sarake :maara})
                                         (with-meta (osa/->Laajenna (keyword (str vuosi "-yhteensa"))
                                                                    yhteensa
                                                                    (if laskutuksen-perusteella-taulukko?
                                                                      #(e! (t/->LaajennaSoluaKlikattu [:hankintakustannukset :hankintataulukko-laskutukseen-perustuen] rivi-id %1 %2))
                                                                      #(e! (t/->LaajennaSoluaKlikattu [:hankintakustannukset :hankintataulukko] rivi-id %1 %2)))
                                                                    {:class #{(sarakkeiden-leveys :yhteensa)
                                                                              "reunaton"}})
                                                    {:sarake :yhteensa})]
                                        #{"reunaton"}))
        lapsirivi (fn [nimi maara]
                    (jana/->Rivi (keyword nimi)
                                 [(osa/->Teksti (keyword (str nimi "-nimi")) (clj-str/capitalize nimi) {:class #{(sarakkeiden-leveys :nimi) "reunaton" "solu-sisenna-1"}})
                                  (osa/->Syote (keyword (str nimi "-maara-kk"))
                                               {:on-change (fn [arvo]
                                                             (when arvo
                                                               (e! (t/->PaivitaToimenpiteenHankintaMaara osa/*this* arvo laskutuksen-perusteella-taulukko?))))}
                                               {:on-change [:positiivinen-numero :eventin-arvo]}
                                               {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}
                                                :type "text"
                                                :disabled (not on-oikeus?)
                                                :value maara})
                                  (osa/->Teksti (keyword (str nimi "-yhteensa"))
                                                "0"
                                                {:class #{(sarakkeiden-leveys :yhteensa)
                                                         "reunaton"}})]
                                 #{"piillotettu" "reunaton"}))
        otsikkorivi (jana/->Rivi :otsikko-rivi
                                 [(osa/->Teksti :tyhja-otsikko
                                                (if laskutuksen-perusteella-taulukko?
                                                  "Laskutuksen perusteella"
                                                  (if laskutukseen-perustuen?
                                                    "Kiinteät" " "))
                                                {:class #{(sarakkeiden-leveys :nimi) "reunaton"}})
                                  (osa/->Teksti :maara-kk-otsikko "Määrä €/kk" {:class #{(sarakkeiden-leveys :maara-kk) "reunaton"}})
                                  (osa/->Teksti :yhteensa-otsikko "Yhteensä" {:class #{(sarakkeiden-leveys :yhteensa) "reunaton"}})]
                                 #{"reunaton"})
        hankinnat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (:pvm %))
                                            (if laskutuksen-perusteella-taulukko?
                                              (-> toimenpiteet valittu-toimenpide :hankinnat-laskutukseen-perustuen)
                                              (-> toimenpiteet valittu-toimenpide :hankinnat)))
        toimenpide-rivit (sequence
                           (comp
                             (map-indexed (fn [index [_ hoitokauden-hankinnat]]
                                            [(inc index) hoitokauden-hankinnat]))
                             (mapcat (fn [[vuosi hankinnat]]
                                       (let [rivi-id (keyword (str vuosi))]
                                         (concat [(paarivi-laajenna rivi-id vuosi (apply + (map :maara hankinnat)))]
                                                 (map (fn [hankinta]
                                                        (with-meta (lapsirivi (pvm/pvm (:pvm hankinta)) (:maara hankinta))
                                                                   {:vanhempi rivi-id}))
                                                      hankinnat))))))
                           (sort-by ffirst hankinnat-hoitokausittain))
        yhteensa-rivi (paarivi "Yhteensä" (reduce (fn [summa tiedot]
                                                    (apply + summa (map :maara (get tiedot
                                                                                    (if laskutuksen-perusteella-taulukko?
                                                                                      :hankinnat-laskutukseen-perustuen
                                                                                      :hankinnat)))))
                                                  0 (get toimenpiteet valittu-toimenpide)))]
    (concat [otsikkorivi]
            (map-indexed #(update %2 :luokat conj (if (odd? %1) "pariton-jana" "parillinen-jana"))
                         toimenpide-rivit)
            [yhteensa-rivi])))

(defn suunnitellut-hankinnat [e! {:keys [toimenpiteet valinnat]}]
  (if toimenpiteet
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet
           :let [nakyvissa? (= toimenpide-avain (:toimenpide valinnat))]]
       ^{:key toimenpide-avain}
       [taulukko/taulukko toimenpide-taulukko (if nakyvissa?
                                                #{"reunaton"}
                                                #{"reunaton" "piillotettu"})])]
    [yleiset/ajax-loader]))

(defn laskutukseen-perustuvat-kustannukset [e! {:keys [toimenpiteet-laskutukseen-perustuen valinnat]}]
  (if toimenpiteet-laskutukseen-perustuen
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet-laskutukseen-perustuen
           :let [nakyvissa? (and (= toimenpide-avain (:toimenpide valinnat))
                                 (contains? (:laskutukseen-perustuen valinnat) toimenpide-avain))]]
       ^{:key toimenpide-avain}
       [taulukko/taulukko toimenpide-taulukko (if nakyvissa?
                                                #{"reunaton"}
                                                #{"reunaton" "piillotettu"})])]
    [yleiset/ajax-loader]))

(defn arvioidaanko-laskutukseen-perustuen [e! _ _]
  (let [laskutukseen-perustuen? (fn [laskutukseen-perustuen toimenpide]
                                 (contains? laskutukseen-perustuen toimenpide))
        vaihda-fn (fn [nappi _]
                    (e! (tuck-apurit/->PaivitaTila [:hankintakustannukset :valinnat]
                                                   (fn [{:keys [toimenpide] :as valinnat}]
                                                     (if (= nappi :kylla)
                                                       (update valinnat :laskutukseen-perustuen conj toimenpide)
                                                       (update valinnat :laskutukseen-perustuen disj toimenpide))))))]
    (fn [e! {:keys [laskutukseen-perustuen toimenpide]} on-oikeus?]
      [:div.laskutukseen-perustuen-filter
       [:label
        [:input {:type "radio" :disabled (not on-oikeus?) :checked (false? (laskutukseen-perustuen? laskutukseen-perustuen toimenpide))
                 :on-change (r/partial vaihda-fn :ei)}]
        "Ei"]
       [:label
        [:input {:type "radio" :disabled (not on-oikeus?) :checked (laskutukseen-perustuen? laskutukseen-perustuen toimenpide)
                 :on-change (r/partial vaihda-fn :kylla)}]
        "Kyllä"]])))

(defn suunnitellut-rahavaraukset [e! toimenpiteet]
  [:span "----- TODO: suunnitellut rahavaraukset -----"])

(defn hankintakustannukset [e! {:keys [valinnat yhteenveto] :as kustannukset}]
  ;; Joka kerta kun muutetaan taulukon arvoja, täytyy yhteenveto päivittää myös
  (async/put! t/viive-kanava [:hankintakustannukset (t/->PaivitaKustannussuunnitelmanYhteenveto)])
  [:div
   [:h2 "Hankintakustannukset"]
   (if yhteenveto
     ^{:key "hankintakustannusten-yhteenveto"}
     [hintalaskuri {:otsikko "Yhteenveto"
                    :selite "Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestoiti"
                    :hinnat yhteenveto}]
     ^{:key "hankintakustannusten-loader"}
     [yleiset/ajax-loader "Hankintakustannusten yhteenveto..."])
   [:h5 "Suunnitellut hankinnat"]
   [hankintojen-filter e! valinnat]
   [suunnitellut-hankinnat e! kustannukset]
   [:span "Arivoidaanko urakassa laskutukseen perustuvia kustannuksia toimenpiteelle: "
    [:b (-> valinnat :toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)]]
   ;; TODO: Korjaa oikeus
   [arvioidaanko-laskutukseen-perustuen e! valinnat true]
   [laskutukseen-perustuvat-kustannukset e! kustannukset]
   [:h5 "Rahavarukset"]
   [suunnitellut-rahavaraukset e! kustannukset]
   #_[suunnitellut-hankinnat-ja-rahavaraukset e! kustannukset]])

(defn erillishankinnat []
  [:span "---- TODO erillishankinnat ----"])

(defn johto-ja-hallintokorvaus []
  [:span "---- TODO johto- ja hallintokorvaus ----"])

(defn hoidonjohtopalkkio []
  [:span "---- TODO hoidonjohtopalkkio ----"])

(defn hallinnolliset-toimenpiteet [{{:keys [yhteenveto]} :hallinnolliset-toimenpiteet}]
  [:div
   [hintalaskuri {:otsikko "Yhteenveto"
                  :selite "Tykkään puurosta"
                  :hinnat yhteenveto}]
   [erillishankinnat]
   [johto-ja-hallintokorvaus]
   [hoidonjohtopalkkio]])

(defn kustannussuunnitelma*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (tuck-apurit/->AloitaViivastettyjenEventtienKuuntelu 1000 t/viive-kanava))
                      (e! (t/->HaeKustannussuunnitelma e!))))
    (fn [e! app]
      [:div.kustannussuunnitelma
       [debug/debug app]
       [:h1 "Kustannussuunnitelma"]
       [:div "Kun kaikki määrät on syötetty, voit seurata kustannuksia. Sampoa varten muodostetaan automaattisesti maksusuunnitelma, jotka löydät Laskutus-osiosta. Kustannussuunnitelmaa tarkennetaan joka hoitovuoden alussa."]
       [kuluva-hoitovuosi]
       [haitari-laatikko
        "Tavoite- ja kattohinta lasketaan automaattisesti"
        {:alussa-auki? true
         :id "tavoite-ja-kattohinta"}
        [tavoite-ja-kattohinta app]
        [:span#tavoite-ja-kattohinta-huomio
         "*) Vuodet ovat hoitovuosia, ei kalenterivuosia."]]
       [haitari-laatikko
        "Suunnitelmien tila"
        {:alussa-auki? true
         :otsikko-elementti :h2}
        [suunnitelmien-tila e! app]]
       [hankintakustannukset e! (:hankintakustannukset app)]
       #_[hallinnolliset-toimenpiteet]])))

(defn kustannussuunnitelma []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])
