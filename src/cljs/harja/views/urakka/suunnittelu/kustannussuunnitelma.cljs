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
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.tyokalut :as tyokalut]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]))

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

(defn kuluva-hoitovuosi [{:keys [vuosi pvmt]}]
  (if (and vuosi pvmt)
    [:div#kuluva-hoitovuosi
     [:span
      (str "Kuluva hoitovuosi: " vuosi
           ". (" (pvm/pvm (first pvmt))
           " - " (pvm/pvm (second pvmt)) ")")]
     [:div.hoitovuosi-napit
      [napit/yleinen-ensisijainen "Laskutus" #(println "Painettiin Laskutus") {:ikoni [ikonit/euro] :disabled true}]
      [napit/yleinen-ensisijainen "Kustannusten seuranta" #(println "Painettiin Kustannusten seuranta") {:ikoni [ikonit/stats] :disabled true}]]]
    [yleiset/ajax-loader]))

(defn tavoite-ja-kattohinta [tavoitehinnat kattohinnat]
  (if (and tavoitehinnat kattohinnat)
    [:div
     [hintalaskuri {:otsikko "Tavoitehinta"
                    :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
                    :hinnat tavoitehinnat}]
     [hintalaskuri {:otsikko "Kattohinta"
                    :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
                    :hinnat kattohinnat}]]
    [yleiset/ajax-loader]))

(defn suunnitelman-selitteet [this luokat _]
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
                               [(osa/->Teksti (keyword (str nimi "-nimi")) (clj-str/capitalize nimi) {:class #{(sarakkeiden-leveys :nimi)}})
                                (osa/->Ikoni (keyword (str nimi "-vuosisuunnitelmat"))
                                             {:ikoni (ikoni-v)}
                                             {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)

                                                       "keskita"}})
                                (osa/->Ikoni (keyword (str nimi "-kuukausisuunnitelmat"))
                                             {:ikoni (ikoni-kk)}
                                             {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)

                                                       "keskita"}})]
                               #{}))
        paarivi-laajenna (fn [nimi]
                           (jana/->Rivi (keyword nimi)
                                        [(osa/->Laajenna (keyword (str nimi "-teksti"))
                                                         (clj-str/capitalize nimi)
                                                         #(e! (t/->LaajennaSoluaKlikattu [:suunnitelmien-tila-taulukko] (keyword nimi) %1 %2))
                                                         {:class #{(sarakkeiden-leveys :nimi)
                                                                   "ikoni-vasemmalle"}})
                                         (osa/->Ikoni (keyword (str nimi "-vuosisuunnitelmat")) {:ikoni ikonit/remove} {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)
                                                                                                                                 "keskita"
                                                                                                                                 }})
                                         (osa/->Ikoni (keyword (str nimi "-kuukausisuunnitelmat")) {:ikoni ikonit/livicon-minus} {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)
                                                                                                                                           "keskita"
                                                                                                                                           }})]
                                        #{}))
        lapsirivi (fn [idn-alku teksti ikoni-v ikoni-kk]
                    (jana/->Rivi (keyword idn-alku)
                                 [(osa/->Teksti (keyword (str idn-alku "-nimi")) teksti {:class #{(sarakkeiden-leveys :nimi)
                                                                                                  "solu-sisenna-1"
                                                                                                  }})
                                  (osa/->Ikoni (keyword (str idn-alku "-vuosisuunnitelmat")) {:ikoni (ikoni-v)} {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)
                                                                                                                          "keskita"
                                                                                                                          }})
                                  (osa/->Ikoni (keyword (str idn-alku "-kuukausisuunnitelmat")) {:ikoni (ikoni-kk)} {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)
                                                                                                                              "keskita"
                                                                                                                              }})]
                                 #{"piillotettu"}))
        otsikkorivi (jana/->Rivi :otsikko-rivi
                                 [(osa/->Komponentti :otsikko-selite suunnitelman-selitteet #{(sarakkeiden-leveys :nimi)} nil)
                                  (osa/->Teksti :otsikko-vuosisuunnitelmat "Vuosisuunnitelmat" {:class #{(sarakkeiden-leveys :vuosisuunnittelmat)
                                                                                                         "keskita"
                                                                                                         "alas"
                                                                                                         "suunnitelman-tila-otsikko"
                                                                                                         }})
                                  (osa/->Teksti :otsikko-kuukausisuunnitelmat "Kuukausisuunnitelmat*" {:class #{(sarakkeiden-leveys :kuukausisuunnitelmat)
                                                                                                                "keskita"
                                                                                                                "alas"
                                                                                                                "suunnitelman-tila-otsikko"
                                                                                                                }})]
                                 #{})
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
        [taulukko/taulukko suunnitelmien-tila-taulukko #{"suunnitelma-ikonien-varit"}]
        [yleiset/ajax-loader]))))

(defn hankintojen-filter [e! _]
  (let [toimenpide-tekstiksi (fn [toimenpide]
                               (-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/upper-case))
        valitse-toimenpide (fn [toimenpide]
                             (e! (tuck-apurit/->MuutaTila [:hankintakustannukset :valinnat :toimenpide] toimenpide)))
        valitse-kausi (fn [kausi]
                        (e! (tuck-apurit/->MuutaTila [:hankintakustannukset :valinnat :maksetaan] kausi)))
        kausi-tekstiksi (fn [kausi]
                          (-> kausi name aakkosta clj-str/capitalize))
        vaihda-fn (fn [event]
                    (.preventDefault event)
                    (e! (tuck-apurit/->PaivitaTila [:hankintakustannukset :valinnat :kopioidaan-tuleville-vuosille?] not)))]
    (fn [_ {:keys [toimenpide maksetaan kopioidaan-tuleville-vuosille?]}]
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
           [:kesakausi :talvikausi]]]
         [:label#kopioi-tuleville-vuosille
          [:input {:type "checkbox" :checked kopioidaan-tuleville-vuosille?
                   :on-change (r/partial vaihda-fn)}]
          "Kopioi kuluvan hoitovuoden summat tuleville vuosille samoille kuukausille"]]))))

(defn hankintasuunnitelmien-syotto
  "Käytännössä input kenttä, mutta sillä lisäominaisuudella, että fokusoituna, tulee
   'Täytä alas' nappi päälle."
  [this {:keys [input-luokat nimi e! on-oikeus? polku-taulukkoon]} value]
  (let [on-change (fn [arvo]
                    (when arvo
                      (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :value arvo))))))
        on-blur (fn [event]
                  (let [klikattu-elementti (.-relatedTarget event)]
                    (e! (t/->PaivitaToimenpideTaulukko (::tama-komponentti osa/*this*) polku-taulukkoon))
                    (e! (t/->PaivitaKustannussuunnitelmanYhteenvedot))
                    ;; Tarkastetaan onko klikattu elementti nappi tai tämä elementti itsessään. Jos on, ei piilloteta nappia.
                    (when-not (and (not (nil? klikattu-elementti))
                                   (or (.getAttribute klikattu-elementti "data-kopioi-allaoleviin")
                                       (= (str (.getAttribute klikattu-elementti "data-id"))
                                          (str (p/osan-id this)))))
                      (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :nappi-nakyvilla? false)))))))
        on-focus (fn [_]
                   (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                               (fn [komponentin-tila]
                                                 (assoc komponentin-tila :nappi-nakyvilla? true)))))
        on-key-down (fn [event]
                      (when (= "Enter" (.. event -key))
                        (.. event -target blur)))
        input-osa (osa/->Syote (keyword (str nimi "-maara-kk"))
                               {:on-change on-change
                                :on-blur on-blur
                                :on-focus on-focus
                                :on-key-down on-key-down}
                               {:on-change [:positiivinen-numero :eventin-arvo]}
                               {:class input-luokat
                                :type "text"
                                :disabled (not on-oikeus?)
                                :value value})
        tayta-alas! (fn [this _]
                      (e! (t/->PaivitaTaulukonOsa this polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :nappi-nakyvilla? false))))
                      (e! (t/->TaytaAlas this polku-taulukkoon)))]
    (fn [this {:keys [luokat]} {:keys [value nappi-nakyvilla?]}]
      [:div.kustannus-syotto {:class (apply str (interpose " " luokat))
                              :tab-index -1
                              :data-id (str (p/osan-id this))}
       [napit/yleinen-ensisijainen "Kopioi allaoleviin" (r/partial tayta-alas! this)
        {:luokka (str "kopioi-nappi button-primary-default "
                      (when-not nappi-nakyvilla?
                        "piillotettu"))
         :data-attributes {:data-kopioi-allaoleviin true}
         :tabindex 0}]
       [p/piirra-osa (-> input-osa
                         (tyokalut/aseta-arvo :arvo value)
                         (assoc ::tama-komponentti this))]])))

(defn osien-paivitys-fn [nimi maara yhteensa]
  (fn [osat]
    (mapv (fn [osa]
            (let [otsikko (p/osan-id osa)]
              (case otsikko
                "Nimi" (nimi osa)
                "Määrä" (maara osa)
                "Yhteensä" (yhteensa osa))))
          osat)))

(defn hankintojen-taulukko [e! toimenpiteet
                            {laskutukseen-perustuen :laskutukseen-perustuen
                             valittu-toimenpide :toimenpide}
                            toimenpide-avain
                            on-oikeus? laskutuksen-perusteella-taulukko?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-8 col-md-8 col-lg-8"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        polku-taulukkoon (if laskutuksen-perusteella-taulukko?
                           [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen toimenpide-avain]
                           [:hankintakustannukset :toimenpiteet toimenpide-avain])
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        laskutuksen-perusteella? (and (= toimenpide-avain valittu-toimenpide)
                                      (contains? laskutukseen-perustuen toimenpide-avain))


        hankinnat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (:pvm %))
                                            toimenpiteet)
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (tyokalut/aseta-arvo :id :otsikko-rivi
                                              :class #{"table-default" "table-default-header"})
                         (tyokalut/paivita-arvo :lapset
                                                (osien-paivitys-fn (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo (cond
                                                                                                  laskutuksen-perusteella-taulukko? "Laskutuksen perusteella"
                                                                                                  laskutuksen-perusteella? "Kiinteät"
                                                                                                  :else " ")
                                                                                          :class #{(sarakkeiden-leveys :nimi)}))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Määrä €/kk"
                                                                                          :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Yhteensä"
                                                                                          :class #{(sarakkeiden-leveys :yhteensa)}))))))
        paarivi-laajenna (fn [rivin-pohja rivin-id hoitokausi yhteensa]
                           (-> rivin-pohja
                               (tyokalut/aseta-arvo :id rivin-id
                                                    :class #{"table-default"})
                               (tyokalut/paivita-arvo :lapset
                                                      (osien-paivitys-fn (fn [osa]
                                                                           (tyokalut/aseta-arvo osa
                                                                                                :id (keyword (str rivin-id "-nimi"))
                                                                                                :arvo (str hoitokausi ". hoitovuosi")
                                                                                                :class #{(sarakkeiden-leveys :nimi)}))
                                                                         (fn [osa]
                                                                           (tyokalut/aseta-arvo osa
                                                                                                :id (keyword (str rivin-id "-maara-kk"))
                                                                                                :arvo ""
                                                                                                :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                         (fn [osa]
                                                                           (-> osa
                                                                               (tyokalut/aseta-arvo :id (keyword (str rivin-id "-yhteensa"))
                                                                                                    :arvo yhteensa
                                                                                                    :class #{(sarakkeiden-leveys :yhteensa)})
                                                                               (assoc :aukaise-fn #(e! (t/->LaajennaSoluaKlikattu polku-taulukkoon rivin-id %1 %2)))))))))
        lapsirivi (fn [rivin-pohja nimi maara]
                    (-> rivin-pohja
                        (tyokalut/aseta-arvo :id (keyword nimi)
                                             :class #{"table-default" "piillotettu"})
                        (tyokalut/paivita-arvo :lapset
                                               (osien-paivitys-fn (fn [osa]
                                                                    (tyokalut/aseta-arvo osa
                                                                                         :id (keyword (str nimi "-nimi"))
                                                                                         :arvo (clj-str/capitalize nimi)
                                                                                         :class #{(sarakkeiden-leveys :nimi) "solu-sisenna-1"}))
                                                                  (fn [osa]
                                                                    (-> osa
                                                                        (tyokalut/aseta-arvo :id (keyword (str nimi "-maara"))
                                                                                             :arvo {:value maara})
                                                                        (assoc :komponentti hankintasuunnitelmien-syotto
                                                                               :komponentin-argumentit {:e! e!
                                                                                                        :nimi nimi
                                                                                                        :on-oikeus? on-oikeus?
                                                                                                        :polku-taulukkoon polku-taulukkoon
                                                                                                        :luokat #{(sarakkeiden-leveys :maara-kk)}
                                                                                                        :input-luokat #{"input-default" "komponentin-input"}})))
                                                                  (fn [osa]
                                                                    (tyokalut/aseta-arvo osa
                                                                                         :id (keyword (str nimi "-yhteensa"))
                                                                                         :arvo maara
                                                                                         :class #{(sarakkeiden-leveys :yhteensa)}))))))
        laajenna-lapsille-fn (fn [laajenna-lapsille-pohja]
                               (map-indexed (fn [index [_ hoitokauden-hankinnat]]
                                              (let [hoitokausi (inc index)
                                                    laajenna-rivin-id (str hoitokausi)]
                                                (-> laajenna-lapsille-pohja
                                                    (tyokalut/aseta-arvo :id laajenna-rivin-id)
                                                    (tyokalut/paivita-arvo :lapset
                                                                           (fn [rivit]
                                                                             (into []
                                                                                   (reduce (fn [rivit rivin-pohja]
                                                                                             (let [rivin-tyyppi (p/janan-id rivin-pohja)]
                                                                                               (concat
                                                                                                 rivit
                                                                                                 (case rivin-tyyppi
                                                                                                   :laajenna [(paarivi-laajenna rivin-pohja (str laajenna-rivin-id "-paa") hoitokausi nil)]
                                                                                                   :lapset (map (fn [hankinta]
                                                                                                                  (lapsirivi rivin-pohja (pvm/pvm (:pvm hankinta)) (:summa hankinta)))
                                                                                                                hoitokauden-hankinnat)))))
                                                                                           [] rivit))))
                                                    (assoc :hoitokausi hoitokausi))))
                                            hankinnat-hoitokausittain))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (tyokalut/aseta-arvo :id :yhteensa
                                               :class #{"table-default" "table-default-sum"})
                          (tyokalut/paivita-arvo :lapset
                                                 (osien-paivitys-fn (fn [osa]
                                                                      (tyokalut/aseta-arvo osa
                                                                                           :id :yhteensa-nimi
                                                                                           :arvo "Yhteensä"
                                                                                           :class #{(sarakkeiden-leveys :nimi)}))
                                                                    (fn [osa]
                                                                      (tyokalut/aseta-arvo osa
                                                                                           :id :yhteensa-maara-kk
                                                                                           :arvo ""
                                                                                           :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                    (fn [osa]
                                                                      (tyokalut/aseta-arvo osa :id :yhteensa-yhteensa
                                                                                           :class #{(sarakkeiden-leveys :yhteensa)}))))))]
    (muodosta-taulukko (if laskutuksen-perusteella-taulukko?
                         :hankinnat-taulukko-laskutukseen-perustuen
                         :hankinnat-taulukko)
                       {:normaali {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Teksti
                                          osa/Teksti]}
                        :laajenna {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Teksti
                                          osa/Laajenna]}
                        :lapset {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti
                                        osa/Komponentti
                                        osa/Teksti]}
                        :laajenna-lapsilla {:janan-tyyppi jana/RiviLapsilla
                                            :janat [:laajenna :lapset]}}
                       ["Nimi" "Määrä" "Yhteensä"]
                       [:normaali otsikko-fn :laajenna-lapsilla laajenna-lapsille-fn :normaali yhteensa-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn rahavarausten-taulukko [e! toimenpiteet
                              {valittu-toimenpide :toimenpide}
                              toimenpide-avain
                              on-oikeus?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-8 col-md-8 col-lg-8"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        polku-taulukkoon [:hankintakustannukset :rahavaraukset toimenpide-avain]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        tyyppi->nimi (fn [tyyppi]
                       (case tyyppi
                         "vahinkojen-korjaukset" "Kolmansien osapuolien aih. vaurioiden korjaukset"
                         "akillinen-hoitotyo" "Äkilliset hoitotyöt"
                         "muut-rahavaraukset" "Muut tilaajan rahavaraukset"))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (tyokalut/aseta-arvo :id :otsikko-rivi
                                              :class #{"table-default" "table-default-header"})
                         (tyokalut/paivita-arvo :lapset
                                                (osien-paivitys-fn (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo (-> toimenpide-avain name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)
                                                                                          :class #{(sarakkeiden-leveys :nimi)}))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Määrä €/kk"
                                                                                          :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Yhteensä"
                                                                                          :class #{(sarakkeiden-leveys :yhteensa)}))))))
        syottorivi-fn (fn [syotto-pohja]
                        (mapv (fn [{:keys [summa tyyppi]}]
                                (-> syotto-pohja
                                    (tyokalut/aseta-arvo :class #{"table-default"}
                                                         :id (keyword tyyppi))
                                    (tyokalut/paivita-arvo :lapset
                                                           (osien-paivitys-fn (fn [osa]
                                                                                (tyokalut/aseta-arvo osa
                                                                                                     :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                                     :arvo (tyyppi->nimi tyyppi)
                                                                                                     :class #{(sarakkeiden-leveys :nimi)}))
                                                                              (fn [osa]
                                                                                (assoc (tyokalut/aseta-arvo osa
                                                                                                            :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                                            :arvo summa
                                                                                                            :class #{(sarakkeiden-leveys :maara-kk)
                                                                                                                     "input-default"})
                                                                                  :toiminnot {:on-change (fn [arvo]
                                                                                                           (when arvo
                                                                                                             (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                              :on-blur (fn [arvo]
                                                                                                         (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (* 12 arvo)))
                                                                                                         (e! (t/->PaivitaKustannussuunnitelmanYhteenvedot)))
                                                                                              :on-key-down (fn [event]
                                                                                                             (when (= "Enter" (.. event -key))
                                                                                                               (.. event -target blur)))}
                                                                                  :kayttaytymiset {:on-change [:positiivinen-numero :eventin-arvo]
                                                                                                   :on-blur [:str->number :positiivinen-numero :eventin-arvo]}))
                                                                              (fn [osa]
                                                                                (tyokalut/aseta-arvo osa
                                                                                                     :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                                     :arvo (* summa 12)
                                                                                                     :class #{(sarakkeiden-leveys :yhteensa)}))))))
                              toimenpiteet))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (tyokalut/aseta-arvo :id :yhteensa
                                               :class #{"table-default" "table-default-sum"})
                          (tyokalut/paivita-arvo :lapset
                                                 (osien-paivitys-fn (fn [osa]
                                                                      (tyokalut/aseta-arvo osa
                                                                                           :id :yhteensa-nimi
                                                                                           :arvo "Yhteensä"
                                                                                           :class #{(sarakkeiden-leveys :nimi)}))
                                                                    (fn [osa]
                                                                      (tyokalut/aseta-arvo osa
                                                                                           :id :yhteensa-maara-kk
                                                                                           :arvo ""
                                                                                           :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                    (fn [osa]
                                                                      (tyokalut/aseta-arvo osa :id :yhteensa-yhteensa
                                                                                           :class #{(sarakkeiden-leveys :yhteensa)}))))))]
    (muodosta-taulukko :rahavaraukset-taulukko
                       {:normaali {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Teksti
                                          osa/Teksti]}
                        :syottorivi {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Syote
                                            osa/Teksti]}}
                       ["Nimi" "Määrä" "Yhteensä"]
                       [:normaali otsikko-fn :syottorivi syottorivi-fn :normaali yhteensa-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn johto-ja-hallintokorvaus-laskulla-taulukko
  [e! jh-laskulla on-oikeus?]
  (let [osien-paivitys-fn (fn [toimenkuva tunnit-kk tuntipalkka yhteensa-kk kk-v]
                            (fn [osat]
                              (mapv (fn [osa]
                                      (let [otsikko (p/osan-id osa)]
                                        (case otsikko
                                          "Toimenkuva" (toimenkuva osa)
                                          "Tunnit/kk" (tunnit-kk osa)
                                          "Tuntipalkka" (tuntipalkka osa)
                                          "Yhteensä/kk" (yhteensa-kk osa)
                                          "kk/v" (kk-v osa))))
                                    osat)))
        polku-taulukkoon [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-laskulla]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (tyokalut/aseta-arvo :id :otsikko-rivi
                                              :class #{"table-default" "table-default-header"
                                                       "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"})
                         (tyokalut/paivita-arvo :lapset
                                                (osien-paivitys-fn (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Toimenkuva"))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Tunnit/kk, h"))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Tuntipalkka, €"))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Yhteensä/kk"))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "kk/v"))))))
        syottorivi-fn (fn [syotto-pohja]
                        (mapv (fn [{:keys [toimenkuva tunnit-kk tuntipalkka yhteensa-kk kk-v]}]
                                (-> syotto-pohja
                                    (tyokalut/aseta-arvo :class #{"table-default"
                                                                  "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"}
                                                         :id (keyword toimenkuva))
                                    (tyokalut/paivita-arvo :lapset
                                                           (osien-paivitys-fn (fn [osa]
                                                                                (tyokalut/aseta-arvo osa
                                                                                                     :id (keyword (str toimenkuva "-" (p/osan-id osa)))
                                                                                                     :arvo toimenkuva))
                                                                              (fn [osa]
                                                                                (tyokalut/aseta-arvo
                                                                                  (-> osa
                                                                                      (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                                      (when arvo
                                                                                                                        (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                         :on-blur (fn [arvo]
                                                                                                                    (when arvo
                                                                                                                      (e! (t/->PaivitaJHRivit osa/*this*))))
                                                                                                         :on-key-down (fn [event]
                                                                                                                        (when (= "Enter" (.. event -key))
                                                                                                                          (.. event -target blur)))}
                                                                                             :kayttaytymiset {:on-change [:positiivinen-numero :eventin-arvo]
                                                                                                              :on-blur [:str->number :positiivinen-numero :eventin-arvo]})
                                                                                      (assoc-in [:parametrit :size] 2))
                                                                                  :id (keyword (str toimenkuva "-" (p/osan-id osa)))
                                                                                  :arvo tunnit-kk
                                                                                  :class #{"input-default"}))
                                                                              (fn [osa]
                                                                                (tyokalut/aseta-arvo
                                                                                  (-> osa
                                                                                      (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                                      (when arvo
                                                                                                                        (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                         :on-blur (fn [arvo]
                                                                                                                    (when arvo
                                                                                                                      (e! (t/->PaivitaJHRivit osa/*this*))))
                                                                                                         :on-key-down (fn [event]
                                                                                                                        (when (= "Enter" (.. event -key))
                                                                                                                          (.. event -target blur)))}
                                                                                             :kayttaytymiset {:on-change [:positiivinen-numero :eventin-arvo]
                                                                                                              :on-blur [:str->number :positiivinen-numero :eventin-arvo]})
                                                                                      (assoc-in [:parametrit :size] 2))
                                                                                  :id (keyword (str toimenkuva "-" (p/osan-id osa)))
                                                                                  :arvo tuntipalkka
                                                                                  :class #{"input-default"}))
                                                                              (fn [osa]
                                                                                (tyokalut/aseta-arvo osa
                                                                                                     :id (keyword (str toimenkuva "-" (p/osan-id osa)))
                                                                                                     :arvo yhteensa-kk))
                                                                              (fn [osa]
                                                                                (tyokalut/aseta-arvo osa
                                                                                                     :id (keyword (str toimenkuva "-" (p/osan-id osa)))
                                                                                                     :arvo kk-v))))))
                              jh-laskulla))]
    (muodosta-taulukko :jh-laskulla
                       {:otsikko {:janan-tyyppi jana/Rivi
                                  :osat [osa/Teksti
                                         osa/Teksti
                                         osa/Teksti
                                         osa/Teksti
                                         osa/Teksti]}
                        :syottorivi {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Syote
                                            osa/Syote
                                            osa/Teksti
                                            osa/Teksti]}}
                       ["Toimenkuva" "Tunnit/kk" "Tuntipalkka" "Yhteensä/kk" "kk/v"]
                       [:otsikko otsikko-fn :syottorivi syottorivi-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn johto-ja-hallintokorvaus-yhteenveto-taulukko
  [e! jh-yhteenveto on-oikeus?]
  (let [osien-paivitys-fn (fn [toimenkuva kk-v hoitokausi-1 hoitokausi-2 hoitokausi-3 hoitokausi-4 hoitokausi-5]
                            (fn [osat]
                              (mapv (fn [osa]
                                      (let [otsikko (p/osan-id osa)]
                                        (case otsikko
                                          "Toimenkuva" (toimenkuva osa)
                                          "kk/v" (kk-v osa)
                                          "1.vuosi/€" (hoitokausi-1 osa)
                                          "2.vuosi/€" (hoitokausi-2 osa)
                                          "3.vuosi/€" (hoitokausi-3 osa)
                                          "4.vuosi/€" (hoitokausi-4 osa)
                                          "5.vuosi/€" (hoitokausi-5 osa))))
                                    osat)))
        polku-taulukkoon [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-yhteenveto]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        rivi-fn (fn [rivi-pohja]
                  (into []
                        (cons
                          (-> rivi-pohja
                              (tyokalut/aseta-arvo :id :otsikko-rivi
                                                   :class #{"table-default" "table-default-header"
                                                            "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"})
                              (tyokalut/paivita-arvo :lapset
                                                     (osien-paivitys-fn (fn [osa]
                                                                          (tyokalut/aseta-arvo osa
                                                                                               :arvo "Toimenkuva"))
                                                                        (fn [osa]
                                                                          (tyokalut/aseta-arvo osa
                                                                                               :arvo "kk/v"))
                                                                        (fn [osa]
                                                                          (tyokalut/aseta-arvo osa
                                                                                               :arvo "1.vuosi/€"))
                                                                        (fn [osa]
                                                                          (tyokalut/aseta-arvo osa
                                                                                               :arvo "2.vuosi/€"))
                                                                        (fn [osa]
                                                                          (tyokalut/aseta-arvo osa
                                                                                               :arvo "3.vuosi/€"))
                                                                        (fn [osa]
                                                                          (tyokalut/aseta-arvo osa
                                                                                               :arvo "4.vuosi/€"))
                                                                        (fn [osa]
                                                                          (tyokalut/aseta-arvo osa
                                                                                               :arvo "5.vuosi/€")))))
                          (map (fn [{:keys [toimenkuva kk-v hoitokausi-1 hoitokausi-2 hoitokausi-3 hoitokausi-4 hoitokausi-5]}]
                                 (-> rivi-pohja
                                     (tyokalut/aseta-arvo :id (keyword toimenkuva)
                                                          :class #{"table-default"})
                                     (tyokalut/paivita-arvo :lapset
                                                            (osien-paivitys-fn (fn [osa]
                                                                                 (tyokalut/aseta-arvo osa :arvo toimenkuva))
                                                                               (fn [osa]
                                                                                 (tyokalut/aseta-arvo osa :arvo kk-v))
                                                                               (fn [osa]
                                                                                 (tyokalut/aseta-arvo osa :arvo hoitokausi-1))
                                                                               (fn [osa]
                                                                                 (tyokalut/aseta-arvo osa :arvo hoitokausi-2))
                                                                               (fn [osa]
                                                                                 (tyokalut/aseta-arvo osa :arvo hoitokausi-3))
                                                                               (fn [osa]
                                                                                 (tyokalut/aseta-arvo osa :arvo hoitokausi-4))
                                                                               (fn [osa]
                                                                                 (tyokalut/aseta-arvo osa :arvo hoitokausi-5))))))
                               jh-yhteenveto))))]
    (muodosta-taulukko :jh-yhteenveto
                       {:rivi {:janan-tyyppi jana/Rivi
                               :osat [osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti
                                      osa/Teksti]}}
                       ["Toimenkuva" "kk/v" "1.vuosi/€" "2.vuosi/€" "3.vuosi/€" "4.vuosi/€" "5.vuosi/€"]
                       [:rivi rivi-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn maara-kk-taulukko [e! polku-taulukkoon rivin-nimi
                         {:keys [maara-kk yhteensa]} on-oikeus?]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-8 col-md-8 col-lg-8"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        taulukon-paivitys-fn! (fn [paivitetty-taulukko app]
                                (assoc-in app polku-taulukkoon paivitetty-taulukko))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (tyokalut/aseta-arvo :id :otsikko-rivi
                                              :class #{"table-default" "table-default-header"})
                         (tyokalut/paivita-arvo :lapset
                                                (osien-paivitys-fn (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo " "
                                                                                          :class #{(sarakkeiden-leveys :nimi)}))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Määrä €/kk"
                                                                                          :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                   (fn [osa]
                                                                     (tyokalut/aseta-arvo osa
                                                                                          :arvo "Yhteensä"
                                                                                          :class #{(sarakkeiden-leveys :yhteensa)}))))))
        syottorivi-fn (fn [syotto-pohja]
                        (-> syotto-pohja
                            (tyokalut/aseta-arvo :class #{"table-default"}
                                                 :id (str rivin-nimi "-rivi"))
                            (tyokalut/paivita-arvo :lapset
                                                   (osien-paivitys-fn (fn [osa]
                                                                        (tyokalut/aseta-arvo osa
                                                                                             :id (keyword (p/osan-id osa))
                                                                                             :arvo rivin-nimi
                                                                                             :class #{(sarakkeiden-leveys :nimi)}))
                                                                      (fn [osa]
                                                                        (assoc (tyokalut/aseta-arvo osa
                                                                                                    :id (keyword (p/osan-id osa))
                                                                                                    :arvo maara-kk
                                                                                                    :class #{(sarakkeiden-leveys :maara-kk)
                                                                                                             "input-default"})
                                                                          :toiminnot {:on-change (fn [arvo]
                                                                                                   (when arvo
                                                                                                     (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                      :on-blur (fn [arvo]
                                                                                                 (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (* 12 arvo))))
                                                                                      :on-key-down (fn [event]
                                                                                                     (when (= "Enter" (.. event -key))
                                                                                                       (.. event -target blur)))}
                                                                          :kayttaytymiset {:on-change [:positiivinen-numero :eventin-arvo]
                                                                                           :on-blur [:str->number :positiivinen-numero :eventin-arvo]}))
                                                                      (fn [osa]
                                                                        (tyokalut/aseta-arvo osa
                                                                                             :id (keyword (p/osan-id osa))
                                                                                             :arvo yhteensa
                                                                                             :class #{(sarakkeiden-leveys :yhteensa)}))))))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (tyokalut/aseta-arvo :id :yhteensa
                                               :class #{"table-default" "table-default-sum"})
                          (tyokalut/paivita-arvo :lapset
                                                 (osien-paivitys-fn (fn [osa]
                                                                      (tyokalut/aseta-arvo osa
                                                                                           :id :yhteensa-nimi
                                                                                           :arvo "Yhteensä"
                                                                                           :class #{(sarakkeiden-leveys :nimi)}))
                                                                    (fn [osa]
                                                                      (tyokalut/aseta-arvo osa
                                                                                           :id :yhteensa-maara-kk
                                                                                           :arvo ""
                                                                                           :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                    (fn [osa]
                                                                      (tyokalut/aseta-arvo osa :id :yhteensa-yhteensa
                                                                                           :class #{(sarakkeiden-leveys :yhteensa)}))))))]
    (muodosta-taulukko (str rivin-nimi "-taulukko")
                       {:normaali {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Teksti
                                          osa/Teksti]}
                        :syottorivi {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Syote
                                            osa/Teksti]}}
                       ["Nimi" "Määrä" "Yhteensä"]
                       [:normaali otsikko-fn :syottorivi syottorivi-fn :normaali yhteensa-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!})))

(defn aseta-rivien-luokat
  "Tämä tekee taulukkoon seeprakuvioinnin"
  [rivit taulukko]
  (let [rivien-luokat (last (reduce (fn [[index rivien-luokat] rivi]
                                      (case (p/rivin-skeema taulukko rivi)
                                        :laajenna-lapsilla (let [nakyvat-rivit (remove #(contains? (tyokalut/arvo % :class) "piillotettu") (p/janan-osat rivi))]
                                                             [(+ index (count nakyvat-rivit))
                                                              (merge rivien-luokat
                                                                     (transduce (comp
                                                                                  (map-indexed (fn [i rivi]
                                                                                                 [(+ i index) rivi]))
                                                                                  (map (fn [[i rivi]]
                                                                                         {(p/janan-id rivi) (if (odd? i)
                                                                                                              "table-default-odd"
                                                                                                              "table-default-even")})))
                                                                                merge {} nakyvat-rivit))])
                                        (:rivi :syottorivi) [(inc index) (merge rivien-luokat
                                                                                {(p/janan-id rivi)
                                                                                 (if (odd? index)
                                                                                   "table-default-odd"
                                                                                   "table-default-even")})]
                                        [(inc index) rivien-luokat]))
                                    [0 {}] rivit))]
    (mapv (fn [rivi]
            (case (p/rivin-skeema taulukko rivi)
              :laajenna-lapsilla (tyokalut/paivita-arvo rivi :lapset
                                                        (fn [rivit]
                                                          (mapv (fn [rivi]
                                                                  (if-let [rivin-luokka (get rivien-luokat (p/janan-id rivi))]
                                                                    (tyokalut/paivita-arvo rivi :class conj rivin-luokka)
                                                                    rivi))
                                                                rivit)))
              (tyokalut/paivita-arvo rivi :class conj (get rivien-luokat (p/janan-id rivi)))))
          rivit)))

(defn suunnitellut-hankinnat [e! toimenpiteet valinnat]
  (if toimenpiteet
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet
           :let [nakyvissa? (= toimenpide-avain (:toimenpide valinnat))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (-> toimenpide-taulukko
                              (tyokalut/paivita-arvo :class (fn [luokat]
                                                              (if nakyvissa?
                                                                (disj luokat "piillotettu")
                                                                (conj luokat "piillotettu"))))
                              (tyokalut/paivita-arvo :lapset (fn [rivit]
                                                               (if nakyvissa?
                                                                 (aseta-rivien-luokat rivit toimenpide-taulukko)
                                                                 rivit))))])]
    [yleiset/ajax-loader]))

(defn laskutukseen-perustuvat-kustannukset [e! toimenpiteet-laskutukseen-perustuen valinnat]
  (if toimenpiteet-laskutukseen-perustuen
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet-laskutukseen-perustuen
           :let [nakyvissa? (and (= toimenpide-avain (:toimenpide valinnat))
                                 (contains? (:laskutukseen-perustuen valinnat) toimenpide-avain))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (-> toimenpide-taulukko
                              (tyokalut/paivita-arvo :class (fn [luokat]
                                                              (if nakyvissa?
                                                                (disj luokat "piillotettu")
                                                                (conj luokat "piillotettu"))))
                              (tyokalut/paivita-arvo :lapset (fn [rivit]
                                                               (if nakyvissa?
                                                                 (aseta-rivien-luokat rivit toimenpide-taulukko)
                                                                 rivit))))])]
    [yleiset/ajax-loader]))

(defn arvioidaanko-laskutukseen-perustuen [e! _ _]
  (let [laskutukseen-perustuen? (fn [laskutukseen-perustuen toimenpide]
                                  (contains? laskutukseen-perustuen toimenpide))
        vaihda-fn (fn [nappi _]
                    (e! (tuck-apurit/->PaivitaTila [:hankintakustannukset :valinnat]
                                                   (fn [{:keys [toimenpide] :as valinnat}]
                                                     (if (= nappi :kylla)
                                                       (update valinnat :laskutukseen-perustuen conj toimenpide)
                                                       (update valinnat :laskutukseen-perustuen disj toimenpide)))))
                    (e! (t/->ToggleHankintakustannuksetOtsikko (= nappi :kylla))))]
    (fn [e! {:keys [laskutukseen-perustuen toimenpide]} on-oikeus?]
      [:div#laskutukseen-perustuen-filter
       [:label
        [:input {:type "radio" :disabled (not on-oikeus?) :checked (false? (laskutukseen-perustuen? laskutukseen-perustuen toimenpide))
                 :on-change (r/partial vaihda-fn :ei)}]
        "Ei"]
       [:label
        [:input {:type "radio" :disabled (not on-oikeus?) :checked (laskutukseen-perustuen? laskutukseen-perustuen toimenpide)
                 :on-change (r/partial vaihda-fn :kylla)}]
        "Kyllä"]])))

(defn suunnitellut-rahavaraukset [e! rahavaraukset valinnat]
  (if rahavaraukset
    [:div
     (for [[toimenpide-avain rahavaraus-taulukko] rahavaraukset
           :let [nakyvissa? (and (= toimenpide-avain (:toimenpide valinnat))
                                 (t/toimenpiteet-rahavarauksilla toimenpide-avain))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (-> rahavaraus-taulukko
                              (tyokalut/paivita-arvo :class (fn [luokat]
                                                              (if nakyvissa?
                                                                (disj luokat "piillotettu")
                                                                (conj luokat "piillotettu"))))
                              (tyokalut/paivita-arvo :lapset (fn [rivit]
                                                               (if nakyvissa?
                                                                 (aseta-rivien-luokat rivit rahavaraus-taulukko)
                                                                 rivit))))])]
    [yleiset/ajax-loader]))

(defn hankintakustannukset-taulukot [e! {:keys [valinnat yhteenveto toimenpiteet toimenpiteet-laskutukseen-perustuen rahavaraukset] :as kustannukset}]
  [:div
   [:h2 "Hankintakustannukset"]
   (if yhteenveto
     ^{:key "hankintakustannusten-yhteenveto"}
     [hintalaskuri {:otsikko "Yhteenveto"
                    :selite "Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestoiti"
                    :hinnat yhteenveto}]
     ^{:key "hankintakustannusten-loader"}
     [yleiset/ajax-loader "Hankintakustannusten yhteenveto..."])
   [:h3 "Suunnitellut hankinnat"]
   [hankintojen-filter e! valinnat]
   [suunnitellut-hankinnat e! toimenpiteet valinnat]
   [:span "Arivoidaanko urakassa laskutukseen perustuvia kustannuksia toimenpiteelle: "
    [:b (-> valinnat :toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)]]
   ;; TODO: Korjaa oikeus
   [arvioidaanko-laskutukseen-perustuen e! valinnat true]
   [laskutukseen-perustuvat-kustannukset e! toimenpiteet-laskutukseen-perustuen valinnat]
   [:h3 "Rahavarukset"]
   [suunnitellut-rahavaraukset e! rahavaraukset valinnat]])

(defn erillishankinnat []
  [:span "---- TODO erillishankinnat ----"])

(defn jh-toimenkuva-laskulla [e! jh-laskulla]
  (if jh-laskulla
    [p/piirra-taulukko (-> jh-laskulla
                           (assoc-in [:parametrit :id] "jh-toimenkuva-laskulla")
                           (tyokalut/paivita-arvo :lapset (fn [rivit]
                                                            (aseta-rivien-luokat rivit jh-laskulla))))]
    [yleiset/ajax-loader]))

(defn jh-toimenkuva-yhteenveto [e! jh-yhteenveto]
  (if jh-yhteenveto
    [p/piirra-taulukko (-> jh-yhteenveto
                           (assoc-in [:parametrit :id] "jh-toimenkuva-yhteenveto")
                           (tyokalut/paivita-arvo :lapset (fn [rivit]
                                                            (aseta-rivien-luokat rivit jh-yhteenveto))))]
    [yleiset/ajax-loader]))

(defn maara-kk [taulukko]
  (if taulukko
    [p/piirra-taulukko (tyokalut/paivita-arvo taulukko :lapset (fn [rivit]
                                                                 (aseta-rivien-luokat rivit taulukko)))]
    [yleiset/ajax-loader]))

(defn johto-ja-hallintokorvaus [e! jh-laskulla jh-yhteenveto toimistokulut]
  [:div#johto-ja-hallintokorvaus
   [:span "---- TODO johto- ja hallintokorvaus  yhteenveto----"]
   [jh-toimenkuva-laskulla e! jh-laskulla]
   [jh-toimenkuva-yhteenveto e! jh-yhteenveto]
   [maara-kk toimistokulut]])

(defn hoidonjohtopalkkio []
  [:span "---- TODO hoidonjohtopalkkio ----"])

(defn hallinnolliset-toimenpiteet-sisalto [e! {:keys [yhteenveto johto-ja-hallintokorvaus-laskulla johto-ja-hallintokorvaus-yhteenveto
                                                      toimistokulut]}]
  [:div
   [hintalaskuri {:otsikko "Yhteenveto"
                  :selite "Tykkään puurosta"
                  :hinnat yhteenveto}]
   [erillishankinnat]
   [johto-ja-hallintokorvaus e! johto-ja-hallintokorvaus-laskulla johto-ja-hallintokorvaus-yhteenveto toimistokulut]
   [hoidonjohtopalkkio]])

(defn kustannussuunnitelma*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->Hoitokausi))
                      (e! (t/->HaeKustannussuunnitelma (partial hankintojen-taulukko e!) (partial rahavarausten-taulukko e!)
                                                       (partial johto-ja-hallintokorvaus-laskulla-taulukko e!)
                                                       (partial johto-ja-hallintokorvaus-yhteenveto-taulukko e!)
                                                       (partial maara-kk-taulukko e! [:hallinnolliset-toimenpiteet :toimistokulut] "Toimistokulut, Pientarvikevarasto")))))
    (fn [e! {:keys [tavoitehinnat kattohinnat hankintakustannukset hallinnolliset-toimenpiteet kuluva-hoitokausi] :as app}]
      [:div.kustannussuunnitelma
       ;[debug/debug app]
       [:h1 "Kustannussuunnitelma"]
       [:div "Kun kaikki määrät on syötetty, voit seurata kustannuksia. Sampoa varten muodostetaan automaattisesti maksusuunnitelma, jotka löydät Laskutus-osiosta. Kustannussuunnitelmaa tarkennetaan joka hoitovuoden alussa."]
       [kuluva-hoitovuosi kuluva-hoitokausi]
       [haitari-laatikko
        "Tavoite- ja kattohinta lasketaan automaattisesti"
        {:alussa-auki? true
         :id "tavoite-ja-kattohinta"}
        [tavoite-ja-kattohinta tavoitehinnat kattohinnat]
        [:span#tavoite-ja-kattohinta-huomio
         "*) Vuodet ovat hoitovuosia, ei kalenterivuosia."]]
       [haitari-laatikko
        "Suunnitelmien tila"
        {:alussa-auki? true
         :otsikko-elementti :h2}
        [suunnitelmien-tila e! app]]
       [hankintakustannukset-taulukot e! hankintakustannukset]
       [hallinnolliset-toimenpiteet-sisalto e! hallinnolliset-toimenpiteet]])))

(defn kustannussuunnitelma []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])
