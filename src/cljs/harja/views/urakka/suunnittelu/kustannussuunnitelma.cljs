(ns harja.views.urakka.suunnittelu.kustannussuunnitelma
  (:require [reagent.core :as r :refer [atom]]
            [clojure.string :as clj-str]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [clojure.set :as clj-set]
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
            [harja.fmt :as fmt]
            [goog.dom :as dom])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]
                   [cljs.core.async.macros :refer [go]]))

(defn summa-formatointi [teksti]
  (let [teksti (clj-str/replace (str teksti) "," ".")]
    (if (or (nil? teksti) (= "" teksti) (js/isNaN teksti))
      "0,00"
      (fmt/desimaaliluku teksti 2 true))))

(defn summa-formatointi-aktiivinen [teksti]
  (let [teksti-ilman-pilkkua (clj-str/replace (str teksti) "," ".")]
    (cond
      (or (nil? teksti) (= "" teksti)) ""
      (re-matches #".*\.0*$" teksti-ilman-pilkkua) (apply str (fmt/desimaaliluku teksti-ilman-pilkkua nil true)
                                                          (drop 1 (re-find #".*(\.|,)(0*)" teksti)))
      :else (fmt/desimaaliluku teksti-ilman-pilkkua nil true))))

(defn virhe-datassa
  [data]
  (log "Virheellinen data:\n" (with-out-str (cljs.pprint/pprint data)))
  [:span
   [ikonit/warning]
   " Osaa ei voida näyttää.."])

(defn toimenpiteiden-jarjestys
  [toimenpide]
  (case toimenpide
    :talvihoito 0
    :liikenneympariston-hoito 1
    :sorateiden-hoito 2
    :paallystepaikkaukset 3
    :mhu-yllapito 4
    :mhu-korvausinvestointi 5))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))

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

(defn aseta-rivien-nakyvyys
  [taulukko]
  (p/paivita-arvo taulukko :lapset
                  (fn [rivit]
                    (mapv (fn [rivi]
                            (case (p/rivin-skeema taulukko rivi)
                              :laajenna-lapsilla (p/paivita-arvo rivi :lapset
                                                                 (fn [rivit]
                                                                   (mapv (fn [rivi]
                                                                           (if (empty? (::t/piillotettu rivi))
                                                                             (p/paivita-arvo rivi :class disj "piillotettu")
                                                                             (p/paivita-arvo rivi :class conj "piillotettu")))
                                                                         rivit)))
                              (if (empty? (::t/piillotettu rivi))
                                (p/paivita-arvo rivi :class disj "piillotettu")
                                (p/paivita-arvo rivi :class conj "piillotettu"))))
                          rivit))))

(defn aseta-rivien-taustavari
  ([taulukko] (aseta-rivien-taustavari taulukko 0))
  ([taulukko rivista-eteenpain]
   (p/paivita-arvo taulukko :lapset
                   (fn [rivit]
                     (let [rivien-luokat (fn rivien-luokat [rivit i]
                                           (loop [[rivi & rivit] rivit
                                                  lopputulos []
                                                  i i]
                                             (if (nil? rivi)
                                               [i lopputulos]
                                               (let [konttirivi? (satisfies? p/Jana (first (p/arvo rivi :lapset)))
                                                     piillotettu-rivi? (contains? (p/arvo rivi :class) "piillotettu")
                                                     rivii (if konttirivi?
                                                             (rivien-luokat (p/arvo rivi :lapset) i)
                                                             rivi)]
                                                 (recur rivit
                                                        (if konttirivi?
                                                          (conj lopputulos
                                                                (p/aseta-arvo rivi :lapset (second rivii)))
                                                          (conj lopputulos
                                                                (if piillotettu-rivi?
                                                                  rivi
                                                                  (-> rivi
                                                                      (p/paivita-arvo :class conj (if (odd? i)
                                                                                                    "table-default-odd"
                                                                                                    "table-default-even"))
                                                                      (p/paivita-arvo :class disj (if (odd? i)
                                                                                                    "table-default-even"
                                                                                                    "table-default-odd"))))))
                                                        (cond
                                                          piillotettu-rivi? i
                                                          konttirivi? (if (odd? (first rivii))
                                                                        1 0)
                                                          :else (inc i)))))))]
                       (into []
                             (concat (take rivista-eteenpain rivit)
                                     (second (rivien-luokat (drop rivista-eteenpain rivit) 0)))))))))

(defn hintalaskurisarake
  ([yla ala] [hintalaskurisarake yla ala nil])
  ([yla ala {:keys [wrapper-luokat container-luokat]}]
   ;; Tämä div ottaa sen tasasen tilan muiden sarakkeiden kanssa, jotta vuodet jakautuu tasaisesti
   [:div {:class container-luokat}
    ;; Tämä div taas pitää sisällänsä ylä- ja alarivit, niin että leveys on maksimissaan sisällön leveys.
    ;; Tämä siksi, että ylarivin sisältö voidaan keskittää alariviin nähden
    [:div.sarake-wrapper {:class wrapper-luokat}
     [:div.hintalaskurisarake-yla yla]
     [:div.hintalaskurisarake-ala ala]]]))

(defn hintalaskuri
  [{:keys [otsikko selite hinnat]} {:keys [vuosi]}]
  (if (some #(or (nil? (:summa %))
                 (js/isNaN (:summa %)))
            hinnat)
    [virhe-datassa hinnat]
    [:div.hintalaskuri
     (when otsikko
       [:h5 otsikko])
     (when selite
       [:div selite])
     [:div.hintalaskuri-vuodet
      (for [{:keys [summa hoitokausi teksti]} hinnat]
        ^{:key hoitokausi}
        [hintalaskurisarake (or teksti (str hoitokausi ". vuosi"))
         (fmt/euro summa)
         (when (= hoitokausi vuosi) {:wrapper-luokat "aktiivinen-vuosi"})])
      [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
      [hintalaskurisarake "Yhteensä" (fmt/euro (reduce #(+ %1 (:summa %2)) 0 hinnat))]]]))

(defn indeksilaskuri
  ([hinnat indeksit] [indeksilaskuri hinnat indeksit nil])
  ([hinnat indeksit dom-id]
   (let [hinnat (mapv (fn [{:keys [summa hoitokausi]}]
                        (let [{:keys [arvo vuosi hoitokausi]} (get indeksit (dec hoitokausi))
                              indeksikorjattu-summa (/ (* summa arvo)
                                                       100)]
                          {:vuosi vuosi
                           :summa indeksikorjattu-summa
                           :hoitokausi hoitokausi}))
                      hinnat)]
     [:div.hintalaskuri.indeksilaskuri {:id dom-id}
      [:span "Indeksikorjattu"]
      [:div.hintalaskuri-vuodet
       (for [{:keys [vuosi summa hoitokausi]} hinnat]
         ^{:key hoitokausi}
         [hintalaskurisarake vuosi (fmt/euro summa)])
       [hintalaskurisarake " " "=" {:container-luokat "hintalaskuri-yhtakuin"}]
       [hintalaskurisarake " " (fmt/euro (reduce #(+ %1 (:summa %2)) 0 hinnat)) {:container-luokat "hintalaskuri-yhteensa"}]]])))

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

(defn tavoite-ja-kattohinta-sisalto [{:keys [tavoitehinnat kattohinnat]} kuluva-hoitokausi indeksit]
  (if (and tavoitehinnat kattohinnat indeksit)
    [:div
     [hintalaskuri {:otsikko "Tavoitehinta"
                    :selite "Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio"
                    :hinnat (update (vec tavoitehinnat) 0 assoc :teksti "1. vuosi*")}
      kuluva-hoitokausi]
     [indeksilaskuri tavoitehinnat indeksit "tavoitehinnan-indeksikorjaus"]
     [hintalaskuri {:otsikko "Kattohinta"
                    :selite "(Hankintakustannukset + Erillishankinnat + Johto- ja hallintokorvaus + Hoidonjohtopalkkio) x 1,1"
                    :hinnat kattohinnat}
      kuluva-hoitokausi]
     [indeksilaskuri kattohinnat indeksit]]
    [yleiset/ajax-loader]))

(defn suunnitelman-selitteet [this luokat _]
  [:div#suunnitelman-selitteet {:class (apply str (interpose " " luokat))}
   [:span [ikonit/ok] "Kaikki kentätä täytetty"]
   [:span [ikonit/livicon-question] "Keskeneräinen"]
   [:span [ikonit/remove] "Suunnitelma puuttuu"]])

(defn suunnitelmien-taulukko [e!]
  (let [polku-taulukkoon [:suunnitelmien-tila-taulukko]
        osien-paivitys-fn (fn [teksti kuluva-vuosi tuleva-vuosi jakso]
                            (fn [osat]
                              (mapv (fn [osa]
                                      (let [otsikko (p/osan-id osa)]
                                        (case otsikko
                                          "Teksti" (teksti osa)
                                          "Kuluva vuosi" (kuluva-vuosi osa)
                                          "Tuleva vuosi" (tuleva-vuosi osa)
                                          "Jakso" (jakso osa))))
                                    osat)))
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :teksti "col-xs-12 col-sm-9 col-md-9 col-lg-9"
                               :kuluva-vuosi "col-xs-12 col-sm-1 col-md-1 col-lg-1"
                               :tuleva-vuosi "col-xs-12 col-sm-1 col-md-1 col-lg-1"
                               :jakso "col-xs-12 col-sm-1 col-md-1 col-lg-1"))
        kuluva-hoitokausi (t/kuluva-hoitokausi)
        viimeinen-vuosi? (= 5 (:vuosi kuluva-hoitokausi))
        mene-idlle (fn [id]
                     (.scrollIntoView (dom/getElement id)))
        otsikko-fn (fn [otsikkopohja]
                     (-> otsikkopohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :id :otsikko-selite)
                                                                  (assoc :komponentti suunnitelman-selitteet
                                                                         :komponentin-argumentit #{(sarakkeiden-leveys :teksti)})))
                                                            (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :arvo (if viimeinen-vuosi?
                                                                                        ""
                                                                                        (str (:vuosi kuluva-hoitokausi) ".vuosi"))
                                                                                :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                         (when-not viimeinen-vuosi?
                                                                                           "aktiivinen-vuosi")
                                                                                         "keskita"
                                                                                         "alas"})
                                                                  (p/paivita-arvo :id str "-otsikko")))
                                                            (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :arvo (str (min 5 (inc (:vuosi kuluva-hoitokausi))) ".vuosi")
                                                                                :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                         (when viimeinen-vuosi?
                                                                                           "aktiivinen-vuosi")
                                                                                         "keskita"
                                                                                         "alas"})
                                                                  (p/paivita-arvo :id str "-otsikko")))
                                                            (fn [osa]
                                                              (-> osa
                                                                  (p/aseta-arvo :arvo ""
                                                                                :class #{(sarakkeiden-leveys :jakso)})
                                                                  (p/paivita-arvo :id str "-otsikko")))))))
        linkkiotsikko-fn (fn [rivin-pohja teksti jakso linkki]
                           (-> rivin-pohja
                               (p/aseta-arvo :id (keyword teksti)
                                             :class #{"suunnitelma-rivi"
                                                      "table-default"})
                               (p/paivita-arvo :lapset
                                               (osien-paivitys-fn (fn [osa]
                                                                    (-> osa
                                                                        (p/aseta-arvo :arvo teksti
                                                                                      :class #{(sarakkeiden-leveys :teksti)
                                                                                               "linkki"})
                                                                        (p/paivita-arvo :id str "-" teksti "-linkkiotsikko")
                                                                        (assoc :teksti teksti)
                                                                        (assoc :toiminnot {:on-click (fn [_] (mene-idlle linkki))})))
                                                                  (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (str (gensym "linkkiotsikko"))
                                                                                  :arvo {:ikoni ikonit/remove}
                                                                                  :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                           "keskita"}))
                                                                  (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (str (gensym "linkkiotsikko"))
                                                                                  :arvo {:ikoni ikonit/remove}
                                                                                  :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                           "keskita"}))
                                                                  (fn [osa]
                                                                    (p/aseta-arvo osa
                                                                                  :id (str (gensym "linkkiotsikko"))
                                                                                  :arvo jakso
                                                                                  :class #{(sarakkeiden-leveys :jakso)}))))))
        sisaotsikko-fn (fn [rivin-pohja teksti jakso]
                         (-> rivin-pohja
                             (p/aseta-arvo :id (keyword teksti)
                                           :class #{})
                             (p/paivita-arvo :lapset
                                             (osien-paivitys-fn (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo teksti
                                                                                :class #{(sarakkeiden-leveys :teksti)}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo {:ikoni ikonit/remove}
                                                                                :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                         "keskita"}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo {:ikoni ikonit/remove}
                                                                                :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                         "keskita"}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :id (str (gensym "sisaotsikko"))
                                                                                :arvo jakso
                                                                                :class #{(sarakkeiden-leveys :jakso)}))))))
        laajenna-toimenpide-fn (fn [laajenna-toimenpide-pohja teksti jakso]
                                 (let [rivi-id (keyword (str teksti "-laajenna"))]
                                   (-> (sisaotsikko-fn laajenna-toimenpide-pohja teksti jakso)
                                       (p/aseta-arvo :id rivi-id
                                                     :class #{"toimenpide-rivi"
                                                              "suunnitelma-rivi"
                                                              "table-default"
                                                              (when viimeinen-vuosi?
                                                                "viimeinen-vuosi")})
                                       (p/paivita-arvo :lapset
                                                       (fn [osat]
                                                         (update osat 0 (fn [laajenna-osa]
                                                                          (-> laajenna-osa
                                                                              (assoc-in [:parametrit :ikoni] "triangle")
                                                                              (p/aseta-arvo :id (str (gensym "laajenna-toimenpide")))
                                                                              (p/paivita-arvo :class conj "ikoni-vasemmalle" "solu-sisenna-1")
                                                                              (assoc :aukaise-fn #(e! (t/->YhteenvetoLaajennaSoluaKlikattu polku-taulukkoon rivi-id %1 %2)))))))))))
        tyyppi->nimi (fn [tyyppi]
                       (case tyyppi
                         :kokonaishintainen-ja-lisatyo "Suunnitellut hankinnat"
                         :vahinkojen-korjaukset "Kolmansien osapuolien aiheuttamien vaurioiden korjaukset"
                         :akillinen-hoitotyo "Äkilliset hoitotyöt"
                         :muut-rahavaraukset "Rahavaraus lupaukseen 1"))
        toimenpide-osa-fn (fn [toimenpide-osa-pohja toimenpideteksti toimenpide]
                            (let [rahavarausrivit (case toimenpide
                                                    (:talvihoito :liikenneympariston-hoito :sorateiden-hoito) [:kokonaishintainen-ja-lisatyo :akillinen-hoitotyo :vahinkojen-korjaukset]
                                                    :mhu-yllapito [:kokonaishintainen-ja-lisatyo :muut-rahavaraukset]
                                                    [:kokonaishintainen-ja-lisatyo])
                                  jaksot (case toimenpide
                                           (:talvihoito :liikenneympariston-hoito :sorateiden-hoito) ["/kk**" "/kk" "/kk"]
                                           :mhu-yllapito ["/kk**" "/kk"]
                                           ["/kk**"])]
                              (map (fn [tyyppi jakso]
                                     (let [rahavarausteksti (tyyppi->nimi tyyppi)]
                                       (-> toimenpide-osa-pohja
                                           (p/aseta-arvo :id (keyword (str toimenpideteksti "-" rahavarausteksti))
                                                         :class #{"piillotettu"
                                                                  "table-default"
                                                                  "suunnitelma-rivi"})
                                           (p/paivita-arvo :lapset
                                                           (osien-paivitys-fn (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo rahavarausteksti
                                                                                              :class #{(sarakkeiden-leveys :teksti)
                                                                                                       "solu-sisenna-2"}))
                                                                              (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo {:ikoni ikonit/remove}
                                                                                              :class #{(sarakkeiden-leveys :kuluva-vuosi)
                                                                                                       "keskita"}))
                                                                              (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo {:ikoni ikonit/remove}
                                                                                              :class #{(sarakkeiden-leveys :tuleva-vuosi)
                                                                                                       "keskita"}))
                                                                              (fn [osa]
                                                                                (p/aseta-arvo osa
                                                                                              :id (str (gensym "toimenpide-osa"))
                                                                                              :arvo jakso
                                                                                              :class #{(sarakkeiden-leveys :jakso)}))))
                                           ;; Laitetaan tämä info, jotta voidaan päivittää pelkästään tarvittaessa render funktiossa
                                           (assoc :suunnitelma tyyppi))))
                                   rahavarausrivit
                                   jaksot)))
        toimenpide-fn (fn [toimenpide-pohja]
                        (map (fn [toimenpide jakso]
                               (let [toimenpideteksti (-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)]
                                 (-> toimenpide-pohja
                                     (p/aseta-arvo :id (keyword (str toimenpideteksti "-vanhempi"))
                                                   :class #{})
                                     (p/paivita-arvo :lapset
                                                     (fn [rivit]
                                                       (into []
                                                             (reduce (fn [rivit rivin-pohja]
                                                                       (let [rivin-tyyppi (p/janan-id rivin-pohja)]
                                                                         (concat
                                                                           rivit
                                                                           (case rivin-tyyppi
                                                                             :laajenna-toimenpide [(laajenna-toimenpide-fn rivin-pohja toimenpideteksti jakso)]
                                                                             :toimenpide-osa (toimenpide-osa-fn rivin-pohja toimenpideteksti toimenpide)))))
                                                                     [] rivit))))
                                     ;; Laitetaan tämä info, jotta voidaan päivittää pelkästään tarvittaessa render funktiossa
                                     (assoc :toimenpide toimenpide))))
                             (sort-by toimenpiteiden-jarjestys t/toimenpiteet)
                             (repeat (count t/toimenpiteet) "/vuosi")))
        hankintakustannukset-fn (fn [hankintakustannukset-pohja]
                                  (-> hankintakustannukset-pohja
                                      (p/aseta-arvo :id (keyword (str "hankintakustannukset-vanhempi"))
                                                    :class #{})
                                      (p/paivita-arvo :lapset
                                                      (fn [rivit]
                                                        (into []
                                                              (reduce (fn [rivit rivin-pohja]
                                                                        (let [rivin-tyyppi (p/janan-id rivin-pohja)]
                                                                          (concat
                                                                            rivit
                                                                            (case rivin-tyyppi
                                                                              :linkkiotsikko [(linkkiotsikko-fn rivin-pohja "Hankintakustannukset" "/vuosi*" "hankintakustannukset")]
                                                                              :toimenpide (toimenpide-fn rivin-pohja)))))
                                                                      [] rivit))))))
        hallinnollinen-toimenpide-fn (fn [hallinnollinen-toimenpide-pohja]
                                       (map (fn [teksti id jakso]
                                              (-> (linkkiotsikko-fn hallinnollinen-toimenpide-pohja teksti jakso id)
                                                  (p/aseta-arvo :id (keyword (str teksti)))
                                                  (p/paivita-arvo :lapset
                                                                  (fn [osat]
                                                                    (update osat 0 (fn [laajenna-osa]
                                                                                     (p/paivita-arvo laajenna-osa :class conj "solu-sisenna-2")))))
                                                  (assoc :halllinto-id id)))
                                            ["Erillishankinnat"
                                             "Johto- ja hallintokorvaus"
                                             "Toimistokulut"
                                             "Hoidonjohtopalkkio"]
                                            [(:erillishankinnat t/hallinnollisten-idt)
                                             (:johto-ja-hallintokorvaus t/hallinnollisten-idt)
                                             (:toimistokulut-taulukko t/hallinnollisten-idt)
                                             (:hoidonjohtopalkkio t/hallinnollisten-idt)]
                                            (repeat 4 "/kk")))
        hallinnollisetkustannukset-fn (fn [hallinnollisetkustannukset-pohja]
                                        (-> hallinnollisetkustannukset-pohja
                                            (p/aseta-arvo :id (keyword (str "hallinnollisetkustannukset-vanhempi"))
                                                          :class #{})
                                            (p/paivita-arvo :lapset
                                                            (fn [rivit]
                                                              (let [rivin-pohja (first rivit)]
                                                                (into []
                                                                      (concat
                                                                        [(linkkiotsikko-fn rivin-pohja "Hallinnolliset toimenpiteet" "/vuosi" "hallinnolliset-toimenpiteet")]
                                                                        (hallinnollinen-toimenpide-fn rivin-pohja))))))))]
    (muodosta-taulukko :suunnitelmien-taulukko
                       {:otsikko {:janan-tyyppi jana/Rivi
                                  :osat [osa/Komponentti
                                         osa/Teksti
                                         osa/Teksti
                                         osa/Teksti]}
                        :linkkiotsikko {:janan-tyyppi jana/Rivi
                                        :osat [osa/Nappi
                                               osa/Ikoni
                                               osa/Ikoni
                                               osa/Teksti]}
                        :laajenna-toimenpide {:janan-tyyppi jana/Rivi
                                              :osat [osa/Laajenna
                                                     osa/Ikoni
                                                     osa/Ikoni
                                                     osa/Teksti]}
                        :toimenpide-osa {:janan-tyyppi jana/Rivi
                                         :osat [osa/Teksti
                                                osa/Ikoni
                                                osa/Ikoni
                                                osa/Teksti]}
                        :toimenpide {:janan-tyyppi jana/RiviLapsilla
                                     :janat [:laajenna-toimenpide :toimenpide-osa]}
                        :hankintakustannukset {:janan-tyyppi jana/RiviLapsilla
                                               :janat [:linkkiotsikko :toimenpide]}
                        :hallinnollisetkustannukset {:janan-tyyppi jana/RiviLapsilla
                                                     :janat [:linkkiotsikko]}}
                       ["Teksti" "Kuluva vuosi" "Tuleva vuosi" "Jakso"]
                       [:otsikko otsikko-fn :hankintakustannukset hankintakustannukset-fn :hallinnollisetkustannukset hallinnollisetkustannukset-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                        :id "suunnitelmien-taulukko"
                        :class #{"suunnitelma-ikonien-varit"}})))

(defn suunnitelman-paivitettavat-osat
  [edelliset-taulukot vanhat-hankintakustannukset uudet-hankintakustannukset
   vanhat-hallinnolliset-toimenpiteet uudet-hallinnolliset-toimenpiteet]
  (let [toimenpide-muutokset (fn [hankinnat uudet-toimenpiteet vanhat-toimenpiteet muutoksen-avain]
                               (into {}
                                     (map (fn [[avain uusi] [_ vanha]]
                                            [avain {muutoksen-avain (or (get-in hankinnat [avain muutoksen-avain])
                                                                        (not= uusi vanha))}])
                                          uudet-toimenpiteet
                                          vanhat-toimenpiteet)))
        hallinnolliset-muutokset (fn [hallinnolliset uudet-hallinnolliset vanhat-hallinnolliset muutoksen-avain]
                                   (or (get hallinnolliset muutoksen-avain)
                                       (not= uudet-hallinnolliset vanhat-hallinnolliset)))]
    (-> edelliset-taulukot
        (update :hankinnat (fn [hankinnat]
                             (merge-with merge
                                         (toimenpide-muutokset hankinnat
                                                               (:toimenpiteet uudet-hankintakustannukset)
                                                               (:toimenpiteet vanhat-hankintakustannukset)
                                                               :kokonaishintainen)
                                         (toimenpide-muutokset hankinnat
                                                               (:toimenpiteet-laskutukseen-perustuen uudet-hankintakustannukset)
                                                               (:toimenpiteet-laskutukseen-perustuen vanhat-hankintakustannukset)
                                                               :lisatyo)
                                         (toimenpide-muutokset hankinnat
                                                               (:rahavaraukset uudet-hankintakustannukset)
                                                               (:rahavaraukset vanhat-hankintakustannukset)
                                                               :rahavaraukset)
                                         (into {}
                                               (map (fn [toimenpide]
                                                      [toimenpide {:laskutukseen-perustuen-valinta (or (get-in hankinnat [toimenpide :laskutukseen-perustuen-valinta])
                                                                                                       (and (not= (:laskutukseen-perustuen (:valinnat uudet-hankintakustannukset))
                                                                                                                  (:laskutukseen-perustuen (:valinnat vanhat-hankintakustannukset)))
                                                                                                            (not (contains? (clj-set/intersection (:laskutukseen-perustuen (:valinnat uudet-hankintakustannukset))
                                                                                                                                                  (:laskutukseen-perustuen (:valinnat vanhat-hankintakustannukset)))
                                                                                                                            toimenpide))))}])
                                                    t/toimenpiteet)))))
        (update :hallinnolliset (fn [hallinnolliset]
                                  (merge-with (fn [vanha-muuttunut? uusi-muuttunut?]
                                                (or vanha-muuttunut? uusi-muuttunut?))
                                              hallinnolliset
                                              {(:erillishankinnat t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                   (:erillishankinnat uudet-hallinnolliset-toimenpiteet)
                                                                                                                   (:erillishankinnat vanhat-hallinnolliset-toimenpiteet)
                                                                                                                   :erillishankinnat)
                                               (:johto-ja-hallintokorvaus t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                           (:johto-ja-hallintokorvaus-yhteenveto uudet-hallinnolliset-toimenpiteet)
                                                                                                                           (:johto-ja-hallintokorvaus-yhteenveto vanhat-hallinnolliset-toimenpiteet)
                                                                                                                           :johto-ja-hallintokorvaus-yhteenveto)
                                               (:toimistokulut-taulukko t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                         (:toimistokulut uudet-hallinnolliset-toimenpiteet)
                                                                                                                         (:toimistokulut vanhat-hallinnolliset-toimenpiteet)
                                                                                                                         :toimistokulut)
                                               (:hoidonjohtopalkkio t/hallinnollisten-idt) (hallinnolliset-muutokset hallinnolliset
                                                                                                                     (:johtopalkkio uudet-hallinnolliset-toimenpiteet)
                                                                                                                     (:johtopalkkio vanhat-hallinnolliset-toimenpiteet)
                                                                                                                     :johtopalkkio)}))))))

(defn suunnitelmien-tila
  [e! kaskytyskanava suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat-luotu-kerran? kirjoitusoikeus? hankintakustannukset hallinnolliset-toimenpiteet]
  (let [paivitetyt-taulukot (cljs.core/atom {})]
    (komp/luo
      (komp/piirretty (fn [this]
                        (let [suunnitelmien-taulukko-alkutila (suunnitelmien-taulukko e!)]
                          (e! (tuck-apurit/->MuutaTila [:suunnitelmien-tila-taulukko] suunnitelmien-taulukko-alkutila)))))
      {:should-component-update (fn [this old-argv new-argv]
                                  ;; Tätä argumenttien tarkkailua ei tulisi tehdä tässä, mutta nykyinen reagentin versio tukee vain
                                  ;; :component-will-receive-props metodia, joka olisi toki sopivampi tähän tarkoitukseen,
                                  ;; mutta React on deprecoinut tuon ja se tulee hajottamaan tulevan koodin.
                                  (let [vanhat-hankintakustannukset (last (butlast old-argv))
                                        uudet-hankintakustannukset (last (butlast new-argv))
                                        vanhat-hallinnolliset-toimenpiteet (last old-argv)
                                        uudet-hallinnolliset-toimenpiteet (last new-argv)]
                                    (swap! paivitetyt-taulukot (fn [edelliset-taulukot]
                                                                 (suunnitelman-paivitettavat-osat edelliset-taulukot vanhat-hankintakustannukset uudet-hankintakustannukset
                                                                                                  vanhat-hallinnolliset-toimenpiteet uudet-hallinnolliset-toimenpiteet))))
                                  (not= old-argv new-argv))}
      (fn [e! kaskytyskanava suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat-luotu-kerran? kirjoitusoikeus? hankintakustannukset hallinnolliset-toimenpiteet]
        (when (and (:toimenpiteet hankintakustannukset) (:johtopalkkio hallinnolliset-toimenpiteet))
          (go (>! kaskytyskanava [:suunnitelmien-tila-render (t/->PaivitaSuunnitelmienTila paivitetyt-taulukot)])))
        (if (and suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat-luotu-kerran?)
          [p/piirra-taulukko (aseta-rivien-taustavari suunnitelmien-tila-taulukko 1)]
          [yleiset/ajax-loader])))))

(defn maksetaan-filter [_ _]
  (let [kausi-tekstiksi (fn [kausi]
                          (case kausi
                            :kesakausi "Kesäkaudella"
                            :talvikausi "Talvikaudella"
                            :molemmat "Kesä- ja talvikaudella"))]
    (fn [valitse-kausi maksetaan]
      [:div.maksu-filter
       [:div
        [:span "Maksetaan"]
        [yleiset/livi-pudotusvalikko {:valinta maksetaan
                                      :valitse-fn valitse-kausi
                                      :format-fn kausi-tekstiksi
                                      :vayla-tyyli? true}
         [:kesakausi :talvikausi :molemmat]]]])))

(defn hankintojen-filter [e! _]
  (let [toimenpide-tekstiksi (fn [toimenpide]
                               (-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/upper-case))
        valitse-toimenpide (fn [toimenpide]
                             (e! (tuck-apurit/->MuutaTila [:hankintakustannukset :valinnat :toimenpide] toimenpide)))
        valitse-kausi (fn [kausi]
                        (e! (tuck-apurit/->MuutaTila [:hankintakustannukset :valinnat :maksetaan] kausi))
                        (e! (t/->MaksukausiValittu)))
        vaihda-fn (fn [event]
                    (.preventDefault event)
                    (e! (tuck-apurit/->PaivitaTila [:hankintakustannukset :valinnat :kopioidaan-tuleville-vuosille?] not)))]
    (fn [_ {:keys [toimenpide maksetaan kopioidaan-tuleville-vuosille?]}]
      (let [toimenpide (toimenpide-tekstiksi toimenpide)]
        [:div
         [:div.kustannussuunnitelma-filter
          [:div
           [:span "Toimenpide"]
           [yleiset/livi-pudotusvalikko {:valinta toimenpide
                                         :valitse-fn valitse-toimenpide
                                         :format-fn toimenpide-tekstiksi
                                         :vayla-tyyli? true}
            (sort-by toimenpiteiden-jarjestys t/toimenpiteet)]]
          [maksetaan-filter valitse-kausi maksetaan]]
         [:input#kopioi-tuleville-hoitovuosille.vayla-checkbox
          {:type "checkbox" :checked kopioidaan-tuleville-vuosille?
           :on-change (r/partial vaihda-fn)}]
         [:label {:for "kopioi-tuleville-hoitovuosille"}
          "Kopioi kuluvan hoitovuoden summat tuleville vuosille samoille kuukausille"]]))))

(defn hankintasuunnitelmien-syotto
  "Käytännössä input kenttä, mutta sillä lisäominaisuudella, että fokusoituna, tulee
   'Täytä alas' nappi päälle."
  [this {:keys [input-luokat kaskytyskanava nimi e! laskutuksen-perusteella-taulukko? polku-taulukkoon toimenpide-avain]} value]
  (let [on-change (fn [arvo]
                    (when arvo
                      (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :value arvo))))))
        on-blur (fn [event]
                  (let [klikattu-elementti (.-relatedTarget event)
                        klikattu-nappia? (and (not (nil? klikattu-elementti))
                                              (or (.getAttribute klikattu-elementti "data-kopioi-allaoleviin")
                                                  (= (str (.getAttribute klikattu-elementti "data-id"))
                                                     (str (p/osan-id this)))))]
                    (e! (t/->PaivitaToimenpideTaulukko (::tama-komponentti osa/*this*) polku-taulukkoon))
                    (e! (t/->PaivitaKustannussuunnitelmanYhteenvedot))
                    ;; Tarkastetaan onko klikattu elementti nappi tai tämä elementti itsessään. Jos on, ei piilloteta nappia.
                    (when-not klikattu-nappia?
                      (e! (t/->TallennaHankintasuunnitelma toimenpide-avain (::tama-komponentti osa/*this*) polku-taulukkoon false laskutuksen-perusteella-taulukko?))
                      (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :nappi-nakyvilla? false))))
                      (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))))
        on-focus (fn [_]
                   (e! (t/->PaivitaTaulukonOsa (::tama-komponentti osa/*this*) polku-taulukkoon
                                               (fn [komponentin-tila]
                                                 (assoc komponentin-tila :nappi-nakyvilla? true)))))
        on-key-down (fn [event]
                      (when (= "Enter" (.. event -key))
                        (.. event -target blur)))
        input-osa (-> (osa/->Syote (keyword (str nimi "-maara-kk"))
                                   {:on-change on-change
                                    :on-blur on-blur
                                    :on-focus on-focus
                                    :on-key-down on-key-down}
                                   {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                {:eventin-arvo {:f poista-tyhjat}}]}
                                   {:class input-luokat
                                    :type "text"
                                    :value value})
                      (p/lisaa-fmt summa-formatointi)
                      (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen))
        tayta-alas! (fn [this _]
                      (e! (t/->PaivitaTaulukonOsa this polku-taulukkoon
                                                  (fn [komponentin-tila]
                                                    (assoc komponentin-tila :nappi-nakyvilla? false))))
                      (e! (t/->TaytaAlas this polku-taulukkoon))
                      (e! (t/->TallennaHankintasuunnitelma toimenpide-avain this polku-taulukkoon true laskutuksen-perusteella-taulukko?))
                      (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)])))]
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
                         (p/aseta-arvo :arvo value)
                         (assoc ::tama-komponentti this))]])))

(defn osien-paivitys-fn [nimi maara yhteensa indeksikorjattu]
  (fn [osat]
    (mapv (fn [osa]
            (let [otsikko (p/osan-id osa)]
              (case otsikko
                "Nimi" (nimi osa)
                "Määrä" (maara osa)
                "Yhteensä" (yhteensa osa)
                "Indeksikorjattu" (indeksikorjattu osa))))
          osat)))

(defn hankintojen-taulukko [e! kaskytyskanava toimenpiteet
                            {laskutukseen-perustuen :laskutukseen-perustuen
                             valittu-toimenpide :toimenpide}
                            toimenpide-avain
                            on-oikeus? laskutuksen-perusteella-taulukko?
                            indeksit]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-6 col-md-6 col-lg-6"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :indeksikorjattu "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        polku-taulukkoon (if laskutuksen-perusteella-taulukko?
                           [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen toimenpide-avain]
                           [:hankintakustannukset :toimenpiteet toimenpide-avain])
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        laskutuksen-perusteella? (and (= toimenpide-avain valittu-toimenpide)
                                      (contains? laskutukseen-perustuen toimenpide-avain))
        kuluva-hoitovuosi (:vuosi (t/kuluva-hoitokausi))
        nyt (pvm/nyt)
        hankinnat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (:pvm %))
                                            toimenpiteet)
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo (cond
                                                                                    laskutuksen-perusteella-taulukko? "Laskutuksen perusteella"
                                                                                    laskutuksen-perusteella? "Kiinteät"
                                                                                    :else " ")
                                                                            :class #{(sarakkeiden-leveys :nimi)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Määrä €/kk"
                                                                            :class #{(sarakkeiden-leveys :maara-kk)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä"
                                                                            :class #{(sarakkeiden-leveys :yhteensa)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Indeksikorjattu"
                                                                            :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                     "harmaa-teksti"}))))))
        paarivi-laajenna (fn [rivin-pohja rivin-id hoitokausi yhteensa]
                           (let [{:keys [arvo hoitokausi]} (get indeksit (dec hoitokausi))
                                 indeksikorjattu (/ (* yhteensa arvo)
                                                          100)]
                             (-> rivin-pohja
                                 (p/aseta-arvo :id rivin-id
                                               :class #{"table-default"})
                                 (p/paivita-arvo :lapset
                                                 (osien-paivitys-fn (fn [osa]
                                                                      (p/aseta-arvo osa
                                                                                    :id (keyword (str rivin-id "-nimi"))
                                                                                    :arvo (if (< hoitokausi kuluva-hoitovuosi)
                                                                                            (str hoitokausi ". hoitovuosi (mennyt)")
                                                                                            (str hoitokausi ". hoitovuosi"))
                                                                                    :class #{(sarakkeiden-leveys :nimi)
                                                                                             "lihavoitu"}))
                                                                    (fn [osa]
                                                                      (p/aseta-arvo osa
                                                                                    :id (keyword (str rivin-id "-maara-kk"))
                                                                                    :arvo ""
                                                                                    :class #{(sarakkeiden-leveys :maara-kk)}))
                                                                    (fn [osa]
                                                                      (-> osa
                                                                          (p/aseta-arvo :id (keyword (str rivin-id "-yhteensa"))
                                                                                        :arvo yhteensa
                                                                                        :class #{(sarakkeiden-leveys :yhteensa)
                                                                                                 "lihavoitu"})
                                                                          (p/lisaa-fmt summa-formatointi)))
                                                                    (fn [osa]
                                                                      (let [osa (-> osa
                                                                                    (p/aseta-arvo :id (keyword (str rivin-id "-indeksikorjattu"))
                                                                                                  :arvo indeksikorjattu
                                                                                                  :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                                           "lihavoitu"
                                                                                                           "harmaa-teksti"})
                                                                                    p/luo-tila!
                                                                                    (p/lisaa-fmt summa-formatointi)
                                                                                    (assoc :aukaise-fn #(e! (t/->LaajennaSoluaKlikattu polku-taulukkoon rivin-id %1 %2))))]
                                                                        (when (= hoitokausi kuluva-hoitovuosi)
                                                                          (p/aseta-tila! osa true))
                                                                        osa)))))))
        lapsirivi (fn [rivin-pohja paivamaara maara hoitokausi]
                    (-> rivin-pohja
                        (p/aseta-arvo :id (keyword (pvm/pvm paivamaara))
                                      :class #{"table-default"})
                        (assoc ::t/piillotettu (if (= hoitokausi kuluva-hoitovuosi)
                                                 #{} #{:laajenna-kiinni}))
                        (p/paivita-arvo :lapset
                                        (osien-paivitys-fn (fn [osa]
                                                             (-> osa
                                                                 (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-nimi"))
                                                                               :arvo paivamaara
                                                                               :class #{(sarakkeiden-leveys :nimi) "solu-sisenna-1"})
                                                                 (p/lisaa-fmt (fn [paivamaara]
                                                                                (let [teksti (-> paivamaara pvm/kuukausi pvm/kuukauden-lyhyt-nimi (str "/" (pvm/vuosi paivamaara)))
                                                                                      mennyt? (and (pvm/ennen? paivamaara nyt)
                                                                                                   (or (not= (pvm/kuukausi nyt) (pvm/kuukausi paivamaara))
                                                                                                       (not= (pvm/vuosi nyt) (pvm/vuosi paivamaara))))]
                                                                                  (if mennyt?
                                                                                    (str teksti " (mennyt)")
                                                                                    teksti))))))
                                                           (fn [osa]
                                                             (if on-oikeus?
                                                               (-> osa
                                                                   (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-maara"))
                                                                                 :arvo {:value (str maara)})
                                                                   (assoc :komponentti hankintasuunnitelmien-syotto
                                                                          :komponentin-argumentit {:e! e!
                                                                                                   :nimi (pvm/pvm paivamaara)
                                                                                                   :laskutuksen-perusteella-taulukko? laskutuksen-perusteella-taulukko?
                                                                                                   :toimenpide-avain toimenpide-avain
                                                                                                   :on-oikeus? on-oikeus?
                                                                                                   :polku-taulukkoon polku-taulukkoon
                                                                                                   :kaskytyskanava kaskytyskanava
                                                                                                   :luokat #{(sarakkeiden-leveys :maara-kk)}
                                                                                                   :input-luokat #{"input-default" "komponentin-input"}}))
                                                               (-> osa
                                                                   (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-maara"))
                                                                                 :arvo maara
                                                                                 :class #{(sarakkeiden-leveys :maara-kk)})
                                                                   (p/lisaa-fmt summa-formatointi))))
                                                           (fn [osa]
                                                             (-> osa
                                                                 (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-yhteensa"))
                                                                               :arvo maara
                                                                               :class #{(sarakkeiden-leveys :yhteensa)})
                                                                 (p/lisaa-fmt summa-formatointi)))
                                                           (fn [osa]
                                                             (-> osa
                                                                 (p/aseta-arvo :id (keyword (str (pvm/pvm paivamaara) "-indeksikorjattu"))
                                                                               :arvo ""
                                                                               :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                        "harmaa-teksti"})))))))
        laajenna-lapsille-fn (fn [laajenna-lapsille-pohja]
                               (map-indexed (fn [index [_ hoitokauden-hankinnat]]
                                              (let [hoitokausi (inc index)
                                                    laajenna-rivin-id (str hoitokausi)]
                                                (-> laajenna-lapsille-pohja
                                                    (p/aseta-arvo :id laajenna-rivin-id)
                                                    (p/paivita-arvo :lapset
                                                                    (fn [rivit]
                                                                      (into []
                                                                            (reduce (fn [rivit rivin-pohja]
                                                                                      (let [rivin-tyyppi (p/janan-id rivin-pohja)]
                                                                                        (concat
                                                                                          rivit
                                                                                          (case rivin-tyyppi
                                                                                            :laajenna [(paarivi-laajenna rivin-pohja (str laajenna-rivin-id "-paa") hoitokausi nil)]
                                                                                            :lapset (map (fn [hankinta]
                                                                                                           (lapsirivi rivin-pohja (:pvm hankinta) (:summa hankinta) hoitokausi))
                                                                                                         hoitokauden-hankinnat)))))
                                                                                    [] rivit))))
                                                    (assoc :hoitokausi hoitokausi))))
                                            hankinnat-hoitokausittain))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (p/aseta-arvo :id :yhteensa
                                        :class #{"table-default" "table-default-sum"})
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-nimi
                                                                             :arvo "Yhteensä"
                                                                             :class #{(sarakkeiden-leveys :nimi)}))
                                                             (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-maara-kk
                                                                             :arvo ""
                                                                             :class #{(sarakkeiden-leveys :maara-kk)}))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-yhteensa
                                                                                 :class #{(sarakkeiden-leveys :yhteensa)})
                                                                   (p/lisaa-fmt summa-formatointi)))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                                                                 :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                          "harmaa-teksti"})
                                                                   (p/lisaa-fmt summa-formatointi)))))))]
    (if on-oikeus?
      (muodosta-taulukko (if laskutuksen-perusteella-taulukko?
                           :hankinnat-taulukko-laskutukseen-perustuen
                           :hankinnat-taulukko)
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :laajenna {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Laajenna]}
                          :lapset {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Komponentti
                                          osa/Teksti
                                          osa/Teksti]}
                          :laajenna-lapsilla {:janan-tyyppi jana/RiviLapsilla
                                              :janat [:laajenna :lapset]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :laajenna-lapsilla laajenna-lapsille-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}})
      (muodosta-taulukko (if laskutuksen-perusteella-taulukko?
                           :hankinnat-taulukko-laskutukseen-perustuen
                           :hankinnat-taulukko)
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :laajenna {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Laajenna]}
                          :lapset {:janan-tyyppi jana/Rivi
                                   :osat [osa/Teksti
                                          osa/Teksti
                                          osa/Teksti
                                          osa/Teksti]}
                          :laajenna-lapsilla {:janan-tyyppi jana/RiviLapsilla
                                              :janat [:laajenna :lapset]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :laajenna-lapsilla laajenna-lapsille-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}}))))

(defn rahavarausten-taulukko [e! kaskytyskanava toimenpiteet
                              {valittu-toimenpide :toimenpide}
                              toimenpide-avain
                              on-oikeus? indeksit]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-6 col-md-6 col-lg-6"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :indeksikorjattu "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        polku-taulukkoon [:hankintakustannukset :rahavaraukset toimenpide-avain]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        kuluva-hoitovuosi (:vuosi (t/kuluva-hoitokausi))
        tyyppi->nimi (fn [tyyppi]
                       (case tyyppi
                         "vahinkojen-korjaukset" "Kolmansien osapuolien aih. vaurioiden korjaukset"
                         "akillinen-hoitotyo" "Äkilliset hoitotyöt"
                         "muut-rahavaraukset" "Rahavaraus lupaukseen 1"))
        tyyppi->tallennettava-asia (fn [tyyppi]
                                     (case tyyppi
                                       "vahinkojen-korjaukset" :kolmansien-osapuolten-aiheuttamat-vahingot
                                       "akillinen-hoitotyo" :akilliset-hoitotyot
                                       "muut-rahavaraukset" :rahavaraus-lupaukseen-1))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo (-> toimenpide-avain name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)
                                                                            :class #{(sarakkeiden-leveys :nimi)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Määrä €/kk"
                                                                            :class #{(sarakkeiden-leveys :maara-kk)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä"
                                                                            :class #{(sarakkeiden-leveys :yhteensa)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Indeksikorjattu"
                                                                            :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                     "harmaa-teksti"}))))))
        syottorivi-fn (fn [syotto-pohja]
                        (mapv (fn [{:keys [summa tyyppi]}]
                                (-> syotto-pohja
                                    (p/aseta-arvo :class #{"table-default"}
                                                  :id (keyword tyyppi))
                                    ;; Laitetaan tämä info, jotta voidaan päivittää suunnitelma yhteenveto pelkästään tarvittaessa render funktiossa
                                    (assoc :suunnitelma (keyword tyyppi))
                                    (p/paivita-arvo :lapset
                                                    (osien-paivitys-fn (fn [osa]
                                                                         (p/aseta-arvo osa
                                                                                       :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                       :arvo (tyyppi->nimi tyyppi)
                                                                                       :class #{(sarakkeiden-leveys :nimi)}))
                                                                       (fn [osa]
                                                                         (-> osa
                                                                             (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                           :arvo summa
                                                                                           :class #{(sarakkeiden-leveys :maara-kk)
                                                                                                    "input-default"})
                                                                             (p/lisaa-fmt summa-formatointi)
                                                                             (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                             (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                             (when arvo
                                                                                                               (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                :on-blur (fn [arvo]
                                                                                                           (when arvo
                                                                                                             (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                                             (e! (t/->TallennaKustannusarvoituTyo (tyyppi->tallennettava-asia tyyppi) toimenpide-avain arvo nil))
                                                                                                             (e! (t/->PaivitaKustannussuunnitelmanYhteenvedot))
                                                                                                             (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                                :on-key-down (fn [event]
                                                                                                               (when (= "Enter" (.. event -key))
                                                                                                                 (.. event -target blur)))}
                                                                                    :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                 {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                     :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})))
                                                                       (fn [osa]
                                                                         (-> osa
                                                                             (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                           :arvo (* summa 12)
                                                                                           :class #{(sarakkeiden-leveys :yhteensa)})
                                                                             (p/lisaa-fmt summa-formatointi)))
                                                                       (fn [osa]
                                                                         (let [{:keys [arvo]} (get indeksit (dec kuluva-hoitovuosi))
                                                                               indeksikorjattu (/ (* summa 12 arvo)
                                                                                                  100)]
                                                                           (-> osa
                                                                               (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                             :arvo indeksikorjattu
                                                                                             :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                                      "harmaa-teksti"})
                                                                               (p/lisaa-fmt summa-formatointi))))))))
                              toimenpiteet))
        rivi-fn (fn [rivi-pohja]
                  (mapv (fn [{:keys [summa tyyppi]}]
                          (-> rivi-pohja
                              (p/aseta-arvo :class #{"table-default"}
                                            :id (keyword tyyppi))
                              ;; Laitetaan tämä info, jotta voidaan päivittää suunnitelma yhteenveto pelkästään tarvittaessa render funktiossa
                              (assoc :suunnitelma (keyword tyyppi))
                              (p/paivita-arvo :lapset
                                              (osien-paivitys-fn (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                 :arvo (tyyppi->nimi tyyppi)
                                                                                 :class #{(sarakkeiden-leveys :nimi)}))
                                                                 (fn [osa]
                                                                   (-> osa
                                                                       (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                     :arvo summa
                                                                                     :class #{(sarakkeiden-leveys :maara-kk)})
                                                                       (p/lisaa-fmt summa-formatointi)))
                                                                 (fn [osa]
                                                                   (-> osa
                                                                       (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                     :arvo (* summa 12)
                                                                                     :class #{(sarakkeiden-leveys :yhteensa)})
                                                                       (p/lisaa-fmt summa-formatointi)))
                                                                 (fn [osa]
                                                                   (let [{:keys [arvo]} (get indeksit (dec kuluva-hoitovuosi))
                                                                         indeksikorjattu (/ (* summa 12 arvo)
                                                                                            100)]
                                                                     (-> osa
                                                                         (p/aseta-arvo :id (keyword (str tyyppi "-" (p/osan-id osa)))
                                                                                       :arvo indeksikorjattu
                                                                                       :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                                "harmaa-teksti"})
                                                                         (p/lisaa-fmt summa-formatointi))))))))
                        toimenpiteet))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (p/aseta-arvo :id :yhteensa
                                        :class #{"table-default" "table-default-sum"})
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-nimi
                                                                             :arvo "Yhteensä"
                                                                             :class #{(sarakkeiden-leveys :nimi)}))
                                                             (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-maara-kk
                                                                             :arvo ""
                                                                             :class #{(sarakkeiden-leveys :maara-kk)}))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-yhteensa
                                                                                 :class #{(sarakkeiden-leveys :yhteensa)})
                                                                   (p/lisaa-fmt summa-formatointi)))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                                                                 :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                          "harmaa-teksti"})
                                                                   (p/lisaa-fmt summa-formatointi)))))))]
    (if on-oikeus?
      (muodosta-taulukko :rahavaraukset-taulukko
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :syottorivi {:janan-tyyppi jana/Rivi
                                       :osat [osa/Teksti
                                              osa/Syote
                                              osa/Teksti
                                              osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :syottorivi syottorivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}})
      (muodosta-taulukko :rahavaraukset-taulukko
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :rivi {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :rivi rivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}}))))

(defn jh-korvauksen-rivit
  [{:keys [toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
  ;; Tässä voi rikastaa kannasta tulevaa tietoa tai lisätä rivejä datan perusteella
  (let [arvot-nolliksi (fn [arvot alku-nollia? n]
                         (into []
                               (concat
                                 (if alku-nollia?
                                   (repeat 0 n)
                                   (take n arvot))
                                 (if alku-nollia?
                                   (drop n arvot)
                                   (repeat 0 (- (count arvot) n))))))
        hoitokausikohtaiset-rivit (cond
                                    ;; Hankintavastaavalla mahdollisesti eri tiedot ensimmäisenä vuonna kuin 2-5
                                    (and (= toimenkuva "hankintavastaava") (= kk-v 12)) [(-> jh-korvaus
                                                                                             (assoc :hoitokaudet #{1})
                                                                                             (update :tunnit-kk arvot-nolliksi false 1)
                                                                                             (update :tuntipalkka arvot-nolliksi false 1)
                                                                                             (update :yhteensa-kk arvot-nolliksi false 1))
                                                                                         (-> jh-korvaus
                                                                                             (update :hoitokaudet disj 1)
                                                                                             (update :tunnit-kk arvot-nolliksi true 1)
                                                                                             (update :tuntipalkka arvot-nolliksi true 1)
                                                                                             (update :yhteensa-kk arvot-nolliksi true 1))]
                                    :else [jh-korvaus])
        toimenkuvan-maksukausiteksti-suluissa (case maksukausi
                                                :kesa "kesäkausi"
                                                :talvi "talvikausi"
                                                nil)
        toimenkuvan-hoitokausiteksti-suluissa (cond
                                                (> (count hoitokausikohtaiset-rivit) 1) (mapv (fn [{:keys [hoitokaudet]}]
                                                                                                (if (= 1 (count hoitokaudet))
                                                                                                  (str (first hoitokaudet) ". sopimusvuosi")
                                                                                                  (str (apply min hoitokaudet) ".-" (apply max hoitokaudet) ". sopimusvuosi")))
                                                                                              hoitokausikohtaiset-rivit)
                                                (= hoitokaudet #{0}) ["ennen urakkaa"]
                                                :else nil)
        sulkutekstit (map (fn [index]
                            (remove nil? [toimenkuvan-maksukausiteksti-suluissa (get toimenkuvan-hoitokausiteksti-suluissa index)]))
                          (range (count hoitokausikohtaiset-rivit)))]
    (map-indexed (fn [index jh-korvaus]
                   (assoc jh-korvaus :muokattu-toimenkuva (if (empty? (nth sulkutekstit index))
                                                            toimenkuva
                                                            (str toimenkuva " (" (apply str (interpose ", " (nth sulkutekstit index))) ")"))))
                 hoitokausikohtaiset-rivit)))

(defn johto-ja-hallintokorvaus-laskulla-taulukko
  [e! kaskytyskanava jh-korvaukset on-oikeus?]
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
        {kuluva-hoitovuosi :vuosi} (t/kuluva-hoitokausi)
        polku-taulukkoon [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-laskulla]
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"
                                                "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Toimenkuva"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Tunnit/kk, h"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Tuntipalkka, €"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä/kk"))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "kk/v"))))))
        disabloidaanko-rivi? (fn [hoitokaudet]
                               (and (every? false? (map #(>= % kuluva-hoitovuosi) hoitokaudet))
                                    (not (and (= 1 kuluva-hoitovuosi)
                                              (contains? hoitokaudet 0)))))
        syottorivi-fn (fn [syotto-pohja]
                        (into []
                              (mapcat (fn [jh-korvaus]
                                        (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                               (-> syotto-pohja
                                                   (p/aseta-arvo :class #{"table-default"
                                                                          "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"}
                                                                 :id (keyword muokattu-toimenkuva))
                                                   (assoc ::p/lisatty-data {:toimenkuva toimenkuva
                                                                            :maksukausi maksukausi
                                                                            :hoitokaudet hoitokaudet})
                                                   (p/paivita-arvo :lapset
                                                                   (osien-paivitys-fn (fn [osa]
                                                                                        (-> osa
                                                                                            (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                          :arvo muokattu-toimenkuva)
                                                                                            (p/lisaa-fmt clj-str/capitalize)))
                                                                                      (fn [osa]
                                                                                        (p/aseta-arvo
                                                                                          (-> osa
                                                                                              (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                                              (when arvo
                                                                                                                                (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                                 :on-blur (fn [arvo]
                                                                                                                            (when arvo
                                                                                                                              (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))
                                                                                                                              (e! (t/->TallennaJohtoJaHallintokorvaukset osa/*this* polku-taulukkoon))
                                                                                                                              (e! (t/->PaivitaJHRivit osa/*this*))
                                                                                                                              (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                                                 :on-key-down (fn [event]
                                                                                                                                (when (= "Enter" (.. event -key))
                                                                                                                                  (.. event -target blur)))}
                                                                                                     :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                  {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                      :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})
                                                                                              (p/lisaa-fmt summa-formatointi)
                                                                                              (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                                              (update :parametrit (fn [parametrit]
                                                                                                                    (assoc parametrit :size 2
                                                                                                                                      :disabled? (disabloidaanko-rivi? hoitokaudet)))))
                                                                                          :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                          :arvo (get tunnit-kk (dec kuluva-hoitovuosi))
                                                                                          :class #{"input-default"}))
                                                                                      (fn [osa]
                                                                                        (p/aseta-arvo
                                                                                          (-> osa
                                                                                              (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                                              (when arvo
                                                                                                                                (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                                                 :on-blur (fn [arvo]
                                                                                                                            (when arvo
                                                                                                                              (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))
                                                                                                                              (e! (t/->TallennaJohtoJaHallintokorvaukset osa/*this* polku-taulukkoon))
                                                                                                                              (e! (t/->PaivitaJHRivit osa/*this*))))
                                                                                                                 :on-key-down (fn [event]
                                                                                                                                (when (= "Enter" (.. event -key))
                                                                                                                                  (.. event -target blur)))}
                                                                                                     :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                  {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                      :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})
                                                                                              (p/lisaa-fmt summa-formatointi)
                                                                                              (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                                              (update :parametrit (fn [parametrit]
                                                                                                                    (assoc parametrit :size 2
                                                                                                                                      :disabled? (disabloidaanko-rivi? hoitokaudet)))))
                                                                                          :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                          :arvo (get tuntipalkka (dec kuluva-hoitovuosi))
                                                                                          :class #{"input-default"}))
                                                                                      (fn [osa]
                                                                                        (-> osa
                                                                                            (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                          :arvo (get yhteensa-kk (dec kuluva-hoitovuosi)))
                                                                                            (p/lisaa-fmt summa-formatointi)))
                                                                                      (fn [osa]
                                                                                        (p/aseta-arvo osa
                                                                                                      :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                      :arvo kk-v))))))
                                             (jh-korvauksen-rivit jh-korvaus)))
                                    jh-korvaukset)))
        rivi-fn (fn [rivi-pohja]
                  (into []
                        (mapcat (fn [jh-korvaus]
                                  (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                         (-> rivi-pohja
                                             (p/aseta-arvo :class #{"table-default"
                                                                    "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"}
                                                           :id (keyword muokattu-toimenkuva))
                                             (assoc ::p/lisatty-data {:toimenkuva toimenkuva
                                                                      :maksukausi maksukausi
                                                                      :hoitokaudet hoitokaudet})
                                             (p/paivita-arvo :lapset
                                                             (osien-paivitys-fn (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo muokattu-toimenkuva)
                                                                                      (p/lisaa-fmt clj-str/capitalize)))
                                                                                (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo (get tunnit-kk (dec kuluva-hoitovuosi)))
                                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                                (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo (get tuntipalkka (dec kuluva-hoitovuosi)))
                                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                                (fn [osa]
                                                                                  (-> osa
                                                                                      (p/aseta-arvo :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                    :arvo (get yhteensa-kk (dec kuluva-hoitovuosi)))
                                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                                (fn [osa]
                                                                                  (p/aseta-arvo osa
                                                                                                :id (keyword (str muokattu-toimenkuva "-" (p/osan-id osa)))
                                                                                                :arvo kk-v))))))
                                       (jh-korvauksen-rivit jh-korvaus)))
                                jh-korvaukset)))]
    (if on-oikeus?
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
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}})
      (muodosta-taulukko :jh-laskulla
                         {:otsikko {:janan-tyyppi jana/Rivi
                                    :osat [osa/Teksti
                                           osa/Teksti
                                           osa/Teksti
                                           osa/Teksti
                                           osa/Teksti]}
                          :rivi {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti]}}
                         ["Toimenkuva" "Tunnit/kk" "Tuntipalkka" "Yhteensä/kk" "kk/v"]
                         [:otsikko otsikko-fn :rivi rivi-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}}))))

(defn johto-ja-hallintokorvaus-yhteenveto-taulukko
  [e! jh-korvaukset indeksit]
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
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        rivi-fn (fn [rivi-pohja]
                  (into []
                        (cons
                          (-> rivi-pohja
                              (p/aseta-arvo :id :otsikko-rivi
                                            :class #{"table-default" "table-default-header"
                                                     "col-xs-12" "col-sm-12" "col-md-12" "col-lg-12"})
                              (p/paivita-arvo :lapset
                                              (osien-paivitys-fn (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "Toimenkuva"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "kk/v"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "1.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "2.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "3.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "4.vuosi/€"))
                                                                 (fn [osa]
                                                                   (p/aseta-arvo osa
                                                                                 :arvo "5.vuosi/€")))))
                          (mapcat (fn [jh-korvaus]
                                    (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                           (let [hoitokauden-arvo (fn [hoitokausi]
                                                                    (if (or (contains? hoitokaudet hoitokausi)
                                                                            (and (= 1 hoitokausi)
                                                                                 (contains? hoitokaudet 0)))
                                                                      (* (get yhteensa-kk (dec hoitokausi)) kk-v)
                                                                      ""))]
                                             (-> rivi-pohja
                                                 (p/aseta-arvo :id (keyword muokattu-toimenkuva)
                                                               :class #{"table-default"})
                                                 (assoc :vuodet hoitokaudet)
                                                 (p/paivita-arvo :lapset
                                                                 (osien-paivitys-fn (fn [osa]
                                                                                      (-> osa
                                                                                          (p/aseta-arvo :arvo muokattu-toimenkuva)
                                                                                          (p/lisaa-fmt clj-str/capitalize)))
                                                                                    (fn [osa]
                                                                                      (p/aseta-arvo osa :arvo kk-v))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 1))
                                                                                              (or (contains? hoitokaudet 1)
                                                                                                  (contains? hoitokaudet 0)) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 2))
                                                                                              (contains? hoitokaudet 2) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 3))
                                                                                              (contains? hoitokaudet 3) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 4))
                                                                                              (contains? hoitokaudet 4) (p/lisaa-fmt summa-formatointi)))
                                                                                    (fn [osa]
                                                                                      (cond-> osa
                                                                                              true (p/aseta-arvo :arvo (hoitokauden-arvo 5))
                                                                                              (contains? hoitokaudet 5) (p/lisaa-fmt summa-formatointi))))))))
                                         (jh-korvauksen-rivit jh-korvaus)))
                                  jh-korvaukset))))
        yhteensa-fn (fn [yhteensa-pohja]
                      (let [{:keys [hoitokausi-1 hoitokausi-2 hoitokausi-3 hoitokausi-4 hoitokausi-5]}
                            (clj-set/rename-keys
                              (transduce
                                (comp (mapcat jh-korvauksen-rivit)
                                      (map (fn [{:keys [toimenkuva muokattu-toimenkuva maksukausi tunnit-kk tuntipalkka yhteensa-kk kk-v hoitokaudet] :as jh-korvaus}]
                                             (into {}
                                                   (map (fn [hoitokausi]
                                                          (let [hoitokauden-index (if (= 0 hoitokausi)
                                                                                    0 (dec hoitokausi))]
                                                            [(if (= 0 hoitokausi) 1 hoitokausi)
                                                             (* (get tunnit-kk hoitokauden-index) (get tuntipalkka hoitokauden-index) kk-v)]))
                                                        hoitokaudet)))))
                                (partial merge-with +) {} jh-korvaukset)
                              {1 :hoitokausi-1
                               2 :hoitokausi-2
                               3 :hoitokausi-3
                               4 :hoitokausi-4
                               5 :hoitokausi-5})]
                        [(-> yhteensa-pohja
                             (p/aseta-arvo :id :yhteensa
                                           :class #{"table-default" "table-default-sum"})
                             (p/paivita-arvo :lapset
                                             (osien-paivitys-fn (fn [osa]
                                                                  (p/aseta-arvo osa :arvo "Yhteensä"))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa :arvo ""))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-1)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-2)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-3)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-4)
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-5)
                                                                      (p/lisaa-fmt summa-formatointi))))))
                         (-> yhteensa-pohja
                             (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                           :class #{"table-default" "table-default-sum"})
                             (p/paivita-arvo :lapset
                                             (osien-paivitys-fn (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :arvo "Indeksikorjattu"
                                                                                :class #{"harmaa-teksti"}))
                                                                (fn [osa]
                                                                  (p/aseta-arvo osa
                                                                                :arvo ""
                                                                                :class #{"harmaa-teksti"}))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-1
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-2
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-3
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-4
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi)))
                                                                (fn [osa]
                                                                  (-> osa
                                                                      (p/aseta-arvo :arvo hoitokausi-5
                                                                                    :class #{"harmaa-teksti"})
                                                                      (p/lisaa-fmt summa-formatointi))))))]))]
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
                       [:rivi rivi-fn :rivi yhteensa-fn]
                       {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                        :class #{}})))

(defn maara-kk-taulukko [e! kaskytyskanava polku-taulukkoon rivin-nimi taulukko-elementin-id
                         {:keys [maara-kk yhteensa]} tallennettava-asia on-oikeus? indeksit]
  (let [sarakkeiden-leveys (fn [sarake]
                             (case sarake
                               :nimi "col-xs-12 col-sm-6 col-md-6 col-lg-6"
                               :maara-kk "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :yhteensa "col-xs-12 col-sm-2 col-md-2 col-lg-2"
                               :indeksikorjattu "col-xs-12 col-sm-2 col-md-2 col-lg-2"))
        taulukon-paivitys-fn! (fn [paivitetty-taulukko]
                                (swap! tila/suunnittelu-kustannussuunnitelma assoc-in polku-taulukkoon paivitetty-taulukko))
        kuluva-hoitovuosi (:vuosi (t/kuluva-hoitokausi))
        indeksikorjaa (fn [x]
                        (let [{:keys [arvo]} (get indeksit (dec kuluva-hoitovuosi))]
                          (/ (* x arvo)
                             100)))
        otsikko-fn (fn [otsikko-pohja]
                     (-> otsikko-pohja
                         (p/aseta-arvo :id :otsikko-rivi
                                       :class #{"table-default" "table-default-header"})
                         (p/paivita-arvo :lapset
                                         (osien-paivitys-fn (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo " "
                                                                            :class #{(sarakkeiden-leveys :nimi)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Määrä €/kk"
                                                                            :class #{(sarakkeiden-leveys :maara-kk)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Yhteensä"
                                                                            :class #{(sarakkeiden-leveys :yhteensa)}))
                                                            (fn [osa]
                                                              (p/aseta-arvo osa
                                                                            :arvo "Indeksikorjattu"
                                                                            :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                     "harmaa-teksti"}))))))
        syottorivi-fn (fn [syotto-pohja]
                        (-> syotto-pohja
                            (p/aseta-arvo :class #{"table-default"}
                                          :id (str rivin-nimi "-rivi"))
                            (p/paivita-arvo :lapset
                                            (osien-paivitys-fn (fn [osa]
                                                                 (p/aseta-arvo osa
                                                                               :id (keyword (p/osan-id osa))
                                                                               :arvo rivin-nimi
                                                                               :class #{(sarakkeiden-leveys :nimi)}))
                                                               (fn [osa]
                                                                 (-> osa
                                                                     (assoc-in [:parametrit :size] 2) ;; size laitettu satunnaisesti, jotta input kentän koko voi muuttua ruudun koon muuttuessa
                                                                     (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                                   :arvo maara-kk
                                                                                   :class #{(sarakkeiden-leveys :maara-kk)
                                                                                            "input-default"})
                                                                     (p/lisaa-fmt summa-formatointi)
                                                                     (p/lisaa-fmt-aktiiviselle summa-formatointi-aktiivinen)
                                                                     (assoc :toiminnot {:on-change (fn [arvo]
                                                                                                     (when arvo
                                                                                                       (e! (t/->MuutaTaulukonOsa osa/*this* polku-taulukkoon arvo))))
                                                                                        :on-blur (fn [arvo]
                                                                                                   (when arvo
                                                                                                     (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Yhteensä" polku-taulukkoon (str (* 12 arvo))))
                                                                                                     (e! (t/->MuutaTaulukonOsanSisarta osa/*this* "Indeksikorjattu" polku-taulukkoon (str (indeksikorjaa (* 12 arvo)))))
                                                                                                     (e! (t/->TallennaKustannusarvoituTyo tallennettava-asia :mhu-johto arvo nil))
                                                                                                     (go (>! kaskytyskanava [:tavoite-ja-kattohinta (t/->TallennaJaPaivitaTavoiteSekaKattohinta)]))))
                                                                                        :on-key-down (fn [event]
                                                                                                       (when (= "Enter" (.. event -key))
                                                                                                         (.. event -target blur)))}
                                                                            :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                         {:eventin-arvo {:f poista-tyhjat}}]
                                                                                             :on-blur [:str->number :numero-pisteella :positiivinen-numero {:eventin-arvo {:f poista-tyhjat}}]})))
                                                               (fn [osa]
                                                                 (-> osa
                                                                     (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                                   :arvo yhteensa
                                                                                   :class #{(sarakkeiden-leveys :yhteensa)})
                                                                     (p/lisaa-fmt summa-formatointi)))
                                                               (fn [osa]
                                                                 (let [indeksikorjattu (indeksikorjaa yhteensa)]
                                                                   (-> osa
                                                                       (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                                     :arvo indeksikorjattu
                                                                                     :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                              "harmaa-teksti"})
                                                                       (p/lisaa-fmt summa-formatointi))))))))
        rivi-fn (fn [rivi-pohja]
                  (-> rivi-pohja
                      (p/aseta-arvo :id :rivi
                                    :class #{"table-default"})
                      (p/paivita-arvo :lapset
                                      (osien-paivitys-fn (fn [osa]
                                                           (p/aseta-arvo osa
                                                                         :id (keyword (p/osan-id osa))
                                                                         :arvo rivin-nimi
                                                                         :class #{(sarakkeiden-leveys :nimi)}))
                                                         (fn [osa]
                                                           (-> osa
                                                               (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                             :arvo maara-kk
                                                                             :class #{(sarakkeiden-leveys :maara-kk)})
                                                               (p/lisaa-fmt summa-formatointi)))
                                                         (fn [osa]
                                                           (-> osa
                                                               (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                             :arvo yhteensa
                                                                             :class #{(sarakkeiden-leveys :yhteensa)})
                                                               (p/lisaa-fmt summa-formatointi)))
                                                         (fn [osa]
                                                           (let [indeksikorjattu (indeksikorjaa (* yhteensa 12))]
                                                             (-> osa
                                                                 (p/aseta-arvo :id (keyword (p/osan-id osa))
                                                                               :arvo indeksikorjattu
                                                                               :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                        "harmaa-teksti"})
                                                                 (p/lisaa-fmt summa-formatointi))))))))
        yhteensa-fn (fn [yhteensa-pohja]
                      (-> yhteensa-pohja
                          (p/aseta-arvo :id :yhteensa
                                        :class #{"table-default" "table-default-sum"})
                          (p/paivita-arvo :lapset
                                          (osien-paivitys-fn (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-nimi
                                                                             :arvo "Yhteensä"
                                                                             :class #{(sarakkeiden-leveys :nimi)}))
                                                             (fn [osa]
                                                               (p/aseta-arvo osa
                                                                             :id :yhteensa-maara-kk
                                                                             :arvo ""
                                                                             :class #{(sarakkeiden-leveys :maara-kk)}))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-yhteensa
                                                                                 :class #{(sarakkeiden-leveys :yhteensa)})
                                                                   (p/lisaa-fmt summa-formatointi)))
                                                             (fn [osa]
                                                               (-> osa
                                                                   (p/aseta-arvo :id :yhteensa-indeksikorjattu
                                                                                 :class #{(sarakkeiden-leveys :indeksikorjattu)
                                                                                          "harmaa-teksti"})
                                                                   (p/lisaa-fmt summa-formatointi)))))))]
    (if on-oikeus?
      (muodosta-taulukko (str rivin-nimi "-taulukko")
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :syottorivi {:janan-tyyppi jana/Rivi
                                       :osat [osa/Teksti
                                              osa/Syote
                                              osa/Teksti
                                              osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :syottorivi syottorivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}
                          :id taulukko-elementin-id})
      (muodosta-taulukko (str rivin-nimi "-taulukko")
                         {:normaali {:janan-tyyppi jana/Rivi
                                     :osat [osa/Teksti
                                            osa/Teksti
                                            osa/Teksti
                                            osa/Teksti]}
                          :rivi {:janan-tyyppi jana/Rivi
                                 :osat [osa/Teksti
                                        osa/Teksti
                                        osa/Teksti
                                        osa/Teksti]}}
                         ["Nimi" "Määrä" "Yhteensä" "Indeksikorjattu"]
                         [:normaali otsikko-fn :rivi rivi-fn :normaali yhteensa-fn]
                         {:taulukon-paivitys-fn! taulukon-paivitys-fn!
                          :class #{}
                          :id taulukko-elementin-id}))))

(defn suunnitellut-hankinnat [e! toimenpiteet valinnat]
  (if toimenpiteet
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet
           :let [nakyvissa? (= toimenpide-avain (:toimenpide valinnat))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (cond-> toimenpide-taulukko
                              true (p/paivita-arvo :class (fn [luokat]
                                                       (if nakyvissa?
                                                         (disj luokat "piillotettu")
                                                         (conj luokat "piillotettu"))))
                              nakyvissa? aseta-rivien-nakyvyys
                              nakyvissa? aseta-rivien-taustavari)])]
    [yleiset/ajax-loader]))

(defn laskutukseen-perustuvat-kustannukset [e! toimenpiteet-laskutukseen-perustuen valinnat]
  (if toimenpiteet-laskutukseen-perustuen
    [:div
     (for [[toimenpide-avain toimenpide-taulukko] toimenpiteet-laskutukseen-perustuen
           :let [nakyvissa? (and (= toimenpide-avain (:toimenpide valinnat))
                                 (contains? (:laskutukseen-perustuen valinnat) toimenpide-avain))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (cond-> toimenpide-taulukko
                                  true (p/paivita-arvo :class (fn [luokat]
                                                                (if nakyvissa?
                                                                  (disj luokat "piillotettu")
                                                                  (conj luokat "piillotettu"))))
                                  nakyvissa? aseta-rivien-nakyvyys
                                  nakyvissa? aseta-rivien-taustavari)])]
    [yleiset/ajax-loader]))

(defn arvioidaanko-laskutukseen-perustuen [e! _ _]
  (let [laskutukseen-perustuen? (fn [laskutukseen-perustuen toimenpide]
                                  (contains? laskutukseen-perustuen toimenpide))
        vaihda-fn (fn [toimenpide event]
                    (let [valittu? (.. event -target -checked)]
                      (e! (tuck-apurit/->PaivitaTila [:hankintakustannukset :valinnat]
                                                     (fn [valinnat]
                                                       (if valittu?
                                                         (update valinnat :laskutukseen-perustuen conj toimenpide)
                                                         (update valinnat :laskutukseen-perustuen disj toimenpide)))))
                      (e! (t/->ToggleHankintakustannuksetOtsikko valittu?))))]
    (fn [e! {:keys [laskutukseen-perustuen toimenpide]} on-oikeus?]
      [:div#laskutukseen-perustuen-filter
       [:input#lakutukseen-perustuen.vayla-checkbox
        {:type "checkbox" :checked (laskutukseen-perustuen? laskutukseen-perustuen toimenpide)
         :on-change (r/partial vaihda-fn toimenpide) :disabled (not on-oikeus?)}]
       [:label {:for "lakutukseen-perustuen"}
        "Haluan suunnitella myös määrämitattavia töitä toimenpiteelle: "
        [:b (-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)]]])))

(defn suunnitellut-rahavaraukset [e! rahavaraukset valinnat]
  (if rahavaraukset
    [:div
     (for [[toimenpide-avain rahavaraus-taulukko] rahavaraukset
           :let [nakyvissa? (and (= toimenpide-avain (:toimenpide valinnat))
                                 (t/toimenpiteet-rahavarauksilla toimenpide-avain))]]
       ^{:key toimenpide-avain}
       [p/piirra-taulukko (cond-> rahavaraus-taulukko
                                  true (p/paivita-arvo :class (fn [luokat]
                                                                (if nakyvissa?
                                                                  (disj luokat "piillotettu")
                                                                  (conj luokat "piillotettu"))))
                                  nakyvissa? aseta-rivien-nakyvyys
                                  nakyvissa? aseta-rivien-taustavari)])]
    [yleiset/ajax-loader]))

(defn hankintakustannukset-taulukot [e! {:keys [valinnat yhteenveto toimenpiteet toimenpiteet-laskutukseen-perustuen rahavaraukset] :as kustannukset}
                                     kuluva-hoitokausi kirjoitusoikeus? indeksit]
  [:div
   [:h2#hankintakustannukset "Hankintakustannukset"]
   (if yhteenveto
     ^{:key "hankintakustannusten-yhteenveto"}
     [:div.summa-ja-indeksilaskuri
      [hintalaskuri {:otsikko "Yhteenveto"
                     :selite "Talvihoito + Liikenneympäristön hoito + Sorateiden hoito + Päällystepaikkaukset + MHU Ylläpito + MHU Korvausinvestoiti"
                     :hinnat yhteenveto}
       kuluva-hoitokausi]
      [indeksilaskuri yhteenveto indeksit]]
     ^{:key "hankintakustannusten-loader"}
     [yleiset/ajax-loader "Hankintakustannusten yhteenveto..."])
   [:h3 "Suunnitellut hankinnat"]
   [hankintojen-filter e! valinnat]
   [suunnitellut-hankinnat e! toimenpiteet valinnat]
   ;; TODO: Korjaa oikeus
   [arvioidaanko-laskutukseen-perustuen e! valinnat kirjoitusoikeus?]
   [laskutukseen-perustuvat-kustannukset e! toimenpiteet-laskutukseen-perustuen valinnat]
   (when (t/toimenpiteet-rahavarauksilla (:toimenpide valinnat))
     ^{:key "rahavaraukset-otsikko"}
     [:h3 "Rahavarukset"])
   [suunnitellut-rahavaraukset e! rahavaraukset valinnat]])

(defn jh-toimenkuva-laskulla [jh-laskulla]
  (if jh-laskulla
    [p/piirra-taulukko (-> jh-laskulla
                           (assoc-in [:parametrit :id] "jh-toimenkuva-laskulla")
                           aseta-rivien-nakyvyys
                           aseta-rivien-taustavari)]
    [yleiset/ajax-loader]))

(defn jh-toimenkuva-yhteenveto [jh-yhteenveto]
  (if jh-yhteenveto
    [p/piirra-taulukko (-> jh-yhteenveto
                           (assoc-in [:parametrit :id] "jh-toimenkuva-yhteenveto")
                           aseta-rivien-nakyvyys
                           aseta-rivien-taustavari)]
    [yleiset/ajax-loader]))

(defn maara-kk [taulukko]
  (if taulukko
    [p/piirra-taulukko (-> taulukko
                           aseta-rivien-nakyvyys
                           aseta-rivien-taustavari)]
    [yleiset/ajax-loader]))

(defn erillishankinnat-yhteenveto
  [erillishankinnat menneet-suunnitelmat {:keys [vuosi] :as kuluva-hoitokausi} indeksit]
  (if erillishankinnat
    (let [summarivin-index 1
          tamavuosi-summa (p/arvo (tyokalut/hae-asia-taulukosta erillishankinnat [summarivin-index "Yhteensä"])
                              :arvo)
          hinnat (map (fn [hoitokausi]
                        (if (>= hoitokausi vuosi)
                          {:summa tamavuosi-summa
                           :hoitokausi hoitokausi}
                          {:summa (* (get-in menneet-suunnitelmat [(dec hoitokausi) :maara-kk]) 12)
                           :hoitokausi hoitokausi}))
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn erillishankinnat [erillishankinnat]
  [maara-kk erillishankinnat])

(defn erillishankinnat-sisalto [erillishankinnat-taulukko menneet-suunnitelmat kuluva-hoitokausi indeksit]
  [:<>
   [:h3 {:id (:erillishankinnat t/hallinnollisten-idt)} "Erillishankinnat"]
   [erillishankinnat-yhteenveto erillishankinnat-taulukko menneet-suunnitelmat kuluva-hoitokausi indeksit]
   [erillishankinnat erillishankinnat-taulukko]
   [:span "Yhteenlaskettu kk-määrä: Hoitourakan tarvitsemat kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät (mm. ajantasainen seuranta, suolan automaattinen seuranta)"]])

(defn johto-ja-hallintokorvaus-yhteenveto
  [jh-yhteenveto toimistokulut menneet-toimistokulut {:keys [vuosi] :as kuluva-hoitokausi} indeksit]
  (if (and jh-yhteenveto toimistokulut)
    (let [tamavuosi-toimistokulutsumma (p/arvo (tyokalut/hae-asia-taulukosta toimistokulut [1 "Yhteensä"])
                                               :arvo)
          hinnat (map (fn [hoitokausi]
                        {:summa (+ (p/arvo (tyokalut/hae-asia-taulukosta jh-yhteenveto [last (str hoitokausi ".vuosi/€")])
                                           :arvo)
                                   (if (>= hoitokausi vuosi)
                                     tamavuosi-toimistokulutsumma
                                     (* (get-in menneet-toimistokulut [(dec hoitokausi) :maara-kk]) 12)))
                         :hoitokausi hoitokausi})
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite "Palkat + Toimitilat + Kelikeskus- ja keliennustepalvelut + Seurantajärjestelmät"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn johto-ja-hallintokorvaus [jh-laskulla jh-yhteenveto toimistokulut menneet-toimistokulusuunnitelmat kuluva-hoitokausi indeksit]
  [:<>
   [:h3 {:id (:johto-ja-hallintokorvaus t/hallinnollisten-idt)} "Johto- ja hallintokorvaus"]
   [johto-ja-hallintokorvaus-yhteenveto jh-yhteenveto toimistokulut menneet-toimistokulusuunnitelmat kuluva-hoitokausi indeksit]
   [jh-toimenkuva-laskulla jh-laskulla]
   [jh-toimenkuva-yhteenveto jh-yhteenveto]
   [maara-kk toimistokulut]
   [:span
    "Yhteenlaskettu kk-määrä: Toimisto- ja ICT-kulut, tiedotus, opastus, kokousten ja vierailujen järjestäminen sekä tarjoilukulut + Hoito- ja korjaustöiden pientarvikevarasto (työkalut, mutterit, lankut, naulat jne.)"]])

(defn hoidonjohtopalkkio-yhteenveto
  [johtopalkkio menneet-suunnitelmat {:keys [vuosi] :as kuluva-hoitokausi} indeksit]
  (if johtopalkkio
    (let [summarivin-index 1
          tamavuosi-summa (p/arvo (tyokalut/hae-asia-taulukosta johtopalkkio [summarivin-index "Yhteensä"])
                                  :arvo)
          hinnat (map (fn [hoitokausi]
                        (if (>= hoitokausi vuosi)
                          {:summa tamavuosi-summa
                           :hoitokausi hoitokausi}
                          {:summa (* (get-in menneet-suunnitelmat [(dec hoitokausi) :maara-kk]) 12)
                           :hoitokausi hoitokausi}))
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko nil
                      :selite nil
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hoidonjohtopalkkio [johtopalkkio]
  [maara-kk johtopalkkio])

(defn hoidonjohtopalkkio-sisalto [johtopalkkio menneet-suunnitelmat kuluva-hoitokausi indeksit]
  [:<>
   [:h3 {:id (:hoidonjohtopalkkio t/hallinnollisten-idt)} "Hoidonjohtopalkkio"]
   [hoidonjohtopalkkio-yhteenveto johtopalkkio menneet-suunnitelmat kuluva-hoitokausi indeksit]
   [hoidonjohtopalkkio johtopalkkio]])

(defn hallinnolliset-toimenpiteet-yhteensa [erillishankinnat jh-yhteenveto johtopalkkio kuluva-hoitokausi indeksit]
  (if (and erillishankinnat jh-yhteenveto johtopalkkio)
    (let [hinnat (map (fn [hoitokausi]
                        (let [eh (p/arvo (tyokalut/hae-asia-taulukosta erillishankinnat [1 "Yhteensä"])
                                         :arvo)
                              jh (p/arvo (tyokalut/hae-asia-taulukosta jh-yhteenveto [last (str hoitokausi ".vuosi/€")])
                                         :arvo)
                              jp (p/arvo (tyokalut/hae-asia-taulukosta johtopalkkio [1 "Yhteensä"])
                                         :arvo)]
                          {:summa (+ eh jh jp)
                           :hoitokausi hoitokausi}))
                      (range 1 6))]
      [:div.summa-ja-indeksilaskuri
       [hintalaskuri {:otsikko "Yhteenveto"
                      :selite "Erillishankinnat + Johto-ja hallintokorvaus + Hoidonjohtopalkkio"
                      :hinnat hinnat}
        kuluva-hoitokausi]
       [indeksilaskuri hinnat indeksit]])
    [yleiset/ajax-loader]))

(defn hallinnolliset-toimenpiteet-sisalto [e! {:keys [johto-ja-hallintokorvaus-laskulla johto-ja-hallintokorvaus-yhteenveto
                                                      toimistokulut johtopalkkio erillishankinnat menneet-vuodet] :as hallinnolliset-toimenpiteet}
                                           kuluva-hoitokausi indeksit]
  [:<>
   [:h2#hallinnolliset-toimenpiteet "Hallinnolliset toimenpiteet"]
   [hallinnolliset-toimenpiteet-yhteensa erillishankinnat johto-ja-hallintokorvaus-yhteenveto johtopalkkio kuluva-hoitokausi indeksit]
   [erillishankinnat-sisalto erillishankinnat (:erillishankinnat menneet-vuodet) kuluva-hoitokausi indeksit]
   [johto-ja-hallintokorvaus johto-ja-hallintokorvaus-laskulla johto-ja-hallintokorvaus-yhteenveto toimistokulut (:toimistokulut menneet-vuodet) kuluva-hoitokausi indeksit]
   [hoidonjohtopalkkio-sisalto johtopalkkio (:johtopalkkio menneet-vuodet) kuluva-hoitokausi indeksit]])

(defn kustannussuunnitelma*
  [e! app]
  (komp/luo
    (komp/piirretty (fn [_]
                      (e! (t/->Hoitokausi))
                      (e! (t/->Oikeudet))
                      (e! (tuck-apurit/->AloitaViivastettyjenEventtienKuuntelu 1000 (:kaskytyskanava app)))
                      (e! (t/->HaeKustannussuunnitelma (partial hankintojen-taulukko e! (:kaskytyskanava app))
                                                       (partial rahavarausten-taulukko e! (:kaskytyskanava app))
                                                       (partial johto-ja-hallintokorvaus-laskulla-taulukko e! (:kaskytyskanava app))
                                                       (partial johto-ja-hallintokorvaus-yhteenveto-taulukko e!)
                                                       (partial maara-kk-taulukko e! (:kaskytyskanava app) [:hallinnolliset-toimenpiteet :erillishankinnat] "Erillishankinnat" "erillishankinnat-taulukko")
                                                       (partial maara-kk-taulukko e! (:kaskytyskanava app) [:hallinnolliset-toimenpiteet :toimistokulut] "Toimistokulut, Pientarvikevarasto" (:toimistokulut-taulukko t/hallinnollisten-idt))
                                                       (partial maara-kk-taulukko e! (:kaskytyskanava app) [:hallinnolliset-toimenpiteet :johtopalkkio] "Hoidonjohtopalkkio" "hoidonjohtopalkkio-taulukko")))))
    (fn [e! {:keys [suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat-luotu-kerran? tavoite-ja-kattohinta
                    hankintakustannukset hallinnolliset-toimenpiteet kuluva-hoitokausi kaskytyskanava
                    kirjoitusoikeus? indeksit] :as app}]
      [:div#kustannussuunnitelma
       ;[debug/debug app]
       [:h1 "Kustannussuunnitelma"]
       [:div "Kun kaikki määrät on syötetty, voit seurata kustannuksia. Sampoa varten muodostetaan automaattisesti maksusuunnitelma, jotka löydät Laskutus-osiosta. Kustannussuunnitelmaa tarkennetaan joka hoitovuoden alussa."]
       [kuluva-hoitovuosi kuluva-hoitokausi]
       [haitari-laatikko
        "Tavoite- ja kattohinta lasketaan automaattisesti"
        {:alussa-auki? true
         :id "tavoite-ja-kattohinta"}
        [tavoite-ja-kattohinta-sisalto tavoite-ja-kattohinta kuluva-hoitokausi indeksit]
        [:span#tavoite-ja-kattohinta-huomio
         "*) Vuodet ovat hoitovuosia, ei kalenterivuosia."]]
       [:span.viiva-alas]
       [haitari-laatikko
        "Suunnitelmien tila"
        {:alussa-auki? true
         :otsikko-elementti :h2}
        [suunnitelmien-tila e! kaskytyskanava suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat-luotu-kerran? kirjoitusoikeus? hankintakustannukset hallinnolliset-toimenpiteet]]
       [:span.viiva-alas]
       [hankintakustannukset-taulukot e! hankintakustannukset kuluva-hoitokausi kirjoitusoikeus? indeksit]
       [:span.viiva-alas]
       [hallinnolliset-toimenpiteet-sisalto e! hallinnolliset-toimenpiteet kuluva-hoitokausi indeksit]])))

(defn kustannussuunnitelma []
  [tuck/tuck tila/suunnittelu-kustannussuunnitelma kustannussuunnitelma*])
