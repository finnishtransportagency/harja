(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [clojure.string :as clj-str]
            [clojure.set :as clj-set]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.tyokalut :as tyokalut]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.domain.oikeudet :as oikeudet]
            [cljs.spec.alpha :as s]
            [harja.loki :refer [warn]]
            [harja.tyokalut.regex :as re]
            [goog.dom :as dom]
            [reagent.core :as r])
  (:require-macros [harja.tyokalut.tuck :refer [varmista-kasittelyjen-jarjestys]]
                   [harja.ui.taulukko.grid :refer [jarjesta-data triggeroi-seurannat]]
                   [cljs.core.async.macros :refer [go]]))

(s/def ::maara #(re-matches (re-pattern (re/positiivinen-numero-re)) (str %)))
(s/def ::aika #(pvm/pvm? %))

(s/def ::hoidonjohtopalkkio (s/keys :req-un [::aika]
                                    :opt-un [::maara]))
(s/def ::hoidonjohtopalkkiot-vuodelle (s/coll-of ::hoidonjohtopalkkio :type vector? :min-count 0))
(s/def ::hoidonjohtopalkkiot-urakalle (s/coll-of ::hoidonjohtopalkkiot-vuodelle :type vector?))

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(def toimenpiteet-rahavarauksilla #{:talvihoito
                                    :liikenneympariston-hoito
                                    :sorateiden-hoito
                                    :mhu-yllapito})

(def ^{:doc "Teksti, joka näytetään käyttäjälle yhteenveto-osiossa, kun
             määrät on suunniteltu kuukausitasolla"}
  vaihtelua-teksti "vaihtelua/kk")

(def rahavaraukset-jarjestys {"muut-rahavaraukset" 1
                              "vahinkojen-korjaukset" 1
                              "akillinen-hoitotyo" 2})

(defn aakkosta [sana]
  (get {"kesakausi" "kesäkausi"
        "liikenneympariston hoito" "liikenneympäristön hoito"
        "mhu yllapito" "mhu-ylläpito"
        "paallystepaikkaukset" "päällystepaikkaukset"
        "akillinen hoitotyo" "äkillinen hoitotyö"}
       sana
       sana))

(def toimenpiteiden-avaimet
  {:paallystepaikkaukset "Päällysteiden paikkaus (hoidon ylläpito)"
   :mhu-yllapito "MHU Ylläpito"
   :talvihoito "Talvihoito laaja TPI"
   :liikenneympariston-hoito "Liikenneympäristön hoito laaja TPI"
   :sorateiden-hoito "Soratien hoito laaja TPI"
   :mhu-korvausinvestointi "MHU Korvausinvestointi"})

(def hallinnollisten-idt
  {:erillishankinnat "erillishankinnat"
   :johto-ja-hallintokorvaus "johto-ja-hallintokorvaus"
   :toimistokulut-taulukko "toimistokulut-taulukko"
   :hoidonjohtopalkkio "hoidonjohtopalkkio"})

(def filteroitavat-tallennettavat-asiat
  #{:hoidonjohtopalkkio
    :toimistokulut
    :erillishankinnat
    :rahavaraus-lupaukseen-1
    :kolmansien-osapuolten-aiheuttamat-vahingot
    :akilliset-hoitotyot})

(def jh-korvaukset
  [{:toimenkuva "sopimusvastaava" :kk-v 12}
   {:toimenkuva "vastuunalainen työnjohtaja" :kk-v 12}
   {:toimenkuva "päätoiminen apulainen" :kk-v 7}
   {:toimenkuva "päätoiminen apulainen" :kk-v 5}
   {:toimenkuva "apulainen/työnjohtaja" :kk-v 7}
   {:toimenkuva "apulainen/työnjohtaja" :kk-v 5}
   {:toimenkuva "viherhoidosta vastaava henkilö" :kk-v 5}
   {:toimenkuva "hankintavastaava" :kk-v 4.5}
   {:toimenkuva "hankintavastaava" :kk-v 12}
   {:toimenkuva "harjoittelija" :kk-v 4}])

(def talvikausi [10 11 12 1 2 3 4])
(def kesakausi (into [] (range 5 10)))
(def hoitokausi (concat talvikausi kesakausi))

(def kaudet {:kesa kesakausi
             :talvi talvikausi
             :kaikki hoitokausi})

(defn indeksikorjaa
  ([hinta] (indeksikorjaa hinta (-> @tiedot/suunnittelu-kustannussuunnitelma :kuluva-hoitokausi :vuosi)))
  ([hinta hoitokauden-numero]
   (let [{:keys [indeksit]} @tiedot/suunnittelu-kustannussuunnitelma
         {:keys [indeksikerroin]} (get indeksit (dec hoitokauden-numero))]
     (when indeksikerroin
       (* hinta indeksikerroin)))))

(defn kuluva-hoitokausi []
  (let [hoitovuoden-pvmt (pvm/paivamaaran-hoitokausi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (-> @tiedot/yleiset :urakka :alkupvm))
        kuluva-urakan-vuosi (- (pvm/vuosi (second hoitovuoden-pvmt)) urakan-aloitusvuosi)]
    (if (> kuluva-urakan-vuosi 5)
      ;; Mikäli on jo loppunut projekti, näytetään viimeisen vuoden tietoja
      {:vuosi 5
       :pvmt (pvm/paivamaaran-hoitokausi (-> @tiedot/yleiset :urakka :loppupvm))}
      {:vuosi kuluva-urakan-vuosi
       :pvmt hoitovuoden-pvmt})))

(def suunnittellut-hankinnat-rajapinta (merge {:otsikot any?
                                               :yhteensa any?

                                               :aseta-suunnittellut-hankinnat! any?
                                               :aseta-yhteenveto! any?}
                                              (reduce (fn [rajapinnat hoitokauden-numero]
                                                        (merge rajapinnat
                                                               {(keyword (str "yhteenveto-" hoitokauden-numero)) any?
                                                                (keyword (str "suunnittellut-hankinnat-" hoitokauden-numero)) any?}))
                                                      {}
                                                      (range 1 6))))

(defn suunnittellut-hankinnat-dr []
  (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                          suunnittellut-hankinnat-rajapinta
                          (merge {:otsikot {:polut [[:gridit :suunnittellut-hankinnat :otsikot]]
                                            :haku identity}
                                  :yhteensa {:polut [[:gridit :suunnittellut-hankinnat :yhteensa :data]
                                                     [:gridit :suunnittellut-hankinnat :yhteensa :nimi]]
                                             :haku (fn [data nimi]
                                                     (assoc data :nimi nimi))}}
                                 (reduce (fn [haut hoitokauden-numero]
                                           (merge haut
                                                  {(keyword (str "yhteenveto-" hoitokauden-numero)) {:polut [[:gridit :suunnittellut-hankinnat :yhteenveto :data (dec hoitokauden-numero)]]
                                                                                                     :haku (fn [data]
                                                                                                             (assoc data :nimi (str hoitokauden-numero ". hoitovuosi")))}
                                                   (keyword (str "suunnittellut-hankinnat-" hoitokauden-numero)) {:polut [[:domain :suunnittellut-hankinnat]
                                                                                                                          [:suodattimet :hankinnat :toimenpide]
                                                                                                                          [:gridit :suunnittellut-hankinnat :hankinnat (dec hoitokauden-numero)]]
                                                                                                                  :haku (fn [suunnittellut-hankinnat valittu-toimenpide johdetut-arvot]
                                                                                                                          (let [arvot (mapv (fn [m]
                                                                                                                                              (select-keys m #{:maara :aika :yhteensa}))
                                                                                                                                            (get-in suunnittellut-hankinnat [valittu-toimenpide (dec hoitokauden-numero)]))
                                                                                                                                johdetut-arvot (if (nil? johdetut-arvot)
                                                                                                                                                 (mapv (fn [{maara :maara}]
                                                                                                                                                         {:yhteensa maara})
                                                                                                                                                       arvot)
                                                                                                                                                 (mapv (fn [ja a]
                                                                                                                                                         (update ja :yhteensa #(or % (get a :maara))))
                                                                                                                                                       johdetut-arvot
                                                                                                                                                       arvot))]
                                                                                                                            (if (nil? johdetut-arvot)
                                                                                                                              arvot
                                                                                                                              (do
                                                                                                                                (when-not (= (count arvot) (count johdetut-arvot))
                                                                                                                                  (warn "JOHDETUT ARVOT EI OLE YHTÄ PITKÄ KUIN ARVOT\n"
                                                                                                                                        "-> ARVOT\n"
                                                                                                                                        (with-out-str (cljs.pprint/pprint arvot))
                                                                                                                                        "-> JOHDETUT ARVOT\n"
                                                                                                                                        (with-out-str (cljs.pprint/pprint johdetut-arvot)))
                                                                                                                                  arvot)
                                                                                                                                (vec
                                                                                                                                  (map merge
                                                                                                                                       arvot
                                                                                                                                       johdetut-arvot))))))}}))
                                         {}
                                         (range 1 6)))
                          {:aseta-suunnittellut-hankinnat! (fn [tila arvo {:keys [aika osa osan-paikka]} paivitetaan-domain? hoitokauden-numero]
                                                             (let [arvo (if (re-matches #"\d*,\d+" arvo)
                                                                          (clj-str/replace arvo #"," ".")
                                                                          arvo)
                                                                   valittu-toimenpide (get-in tila [:suodattimet :hankinnat :toimenpide])
                                                                   paattyy-desimaalierottajaan? (re-matches #"\d*(,|\.)$" arvo)
                                                                   paivita-gridit (fn [tila]
                                                                                    (update-in tila [:gridit :suunnittellut-hankinnat :hankinnat (dec hoitokauden-numero)]
                                                                                               (fn [hoitokauden-hankinnat]
                                                                                                 (let [hoitokauden-hankinnat (if (nil? hoitokauden-hankinnat)
                                                                                                                               (vec (repeat 12 {}))
                                                                                                                               hoitokauden-hankinnat)]
                                                                                                   (assoc-in hoitokauden-hankinnat [(first osan-paikka) osa] arvo)))))
                                                                   paivita-domain (fn [tila]
                                                                                    (update-in tila [:domain :suunnittellut-hankinnat valittu-toimenpide (dec hoitokauden-numero)]
                                                                                               (fn [hoitokauden-hankinnat]
                                                                                                 (assoc-in hoitokauden-hankinnat [(first osan-paikka) osa] (js/Number arvo)))))]
                                                               ;; Halutaan pitää data atomissa olevat arvot numeroina kun taasen käyttöliittymässä sen täytyy olla string (desimaalierottajan takia)
                                                               (if (and paivitetaan-domain?
                                                                        (not paattyy-desimaalierottajaan?))
                                                                 (-> tila paivita-domain paivita-gridit)
                                                                 (paivita-gridit tila))))
                           :aseta-yhteenveto! (fn [tila arvo tunniste hoitokauden-numero]
                                                (let [arvo (if (re-matches #"\d*,\d+" arvo)
                                                             (clj-str/replace arvo #"," ".")
                                                             arvo)
                                                      paivita-gridit (fn [tila]
                                                                       (update-in tila [:gridit :suunnittellut-hankinnat :yhteenveto :data (dec hoitokauden-numero) tunniste]
                                                                                  (fn [tunnisteen-arvo]
                                                                                    arvo)))]
                                                  (if hoitokauden-numero
                                                    (paivita-gridit tila)
                                                    tila)))}
                          (merge
                            {:hankinnat-yhteensa-seuranta {:polut [[:gridit :suunnittellut-hankinnat :yhteenveto :data]]
                                                           :init (fn [tila]
                                                                   (assoc-in tila [:gridit :suunnittellut-hankinnat :yhteensa :data] nil))
                                                           :aseta (fn [tila data]
                                                                    (let [hoidonjohtopalkkiot-yhteensa (apply + (map :yhteensa data))]
                                                                      (assoc-in tila [:gridit :suunnittellut-hankinnat :yhteensa :data] {:yhteensa hoidonjohtopalkkiot-yhteensa
                                                                                                                                         :indeksikorjattu (indeksikorjaa hoidonjohtopalkkiot-yhteensa)})))}
                             :valittu-toimenpide-seuranta {:polut [[:suodattimet :hankinnat :toimenpide]]
                                                           :aseta (fn [tila _]
                                                                    (update-in tila [:gridit :suunnittellut-hankinnat :hankinnat]
                                                                               (fn [kaikki-hankinnat]
                                                                                 (mapv (fn [hoitokauden-hankinnat]
                                                                                         (vec (repeat 12 {})))
                                                                                       kaikki-hankinnat))))}}
                            (doall
                              (reduce (fn [seurannat hoitokauden-numero]
                                        (merge seurannat
                                               {(keyword (str "hankinnat-yhteenveto-seuranta-" hoitokauden-numero)) {:polut [[:domain :suunnittellut-hankinnat]
                                                                                                                             [:suodattimet :hankinnat :toimenpide]]
                                                                                                                     :init (fn [tila]
                                                                                                                             (update-in tila [:gridit :suunnittellut-hankinnat :yhteenveto :data] (fn [data]
                                                                                                                                                                                                    (if (nil? data)
                                                                                                                                                                                                      (vec (repeat 5 nil))
                                                                                                                                                                                                      data))))
                                                                                                                     :aseta (fn [tila vuoden-hoidonjohtopalkkio valittu-toimenpide]
                                                                                                                              (let [vuoden-hoidonjohtopalkkiot-yhteensa (reduce (fn [yhteensa {maara :maara}]
                                                                                                                                                                                  (+ yhteensa maara))
                                                                                                                                                                                0
                                                                                                                                                                                (get-in vuoden-hoidonjohtopalkkio [valittu-toimenpide (dec hoitokauden-numero)]))]
                                                                                                                                (assoc-in tila [:gridit :suunnittellut-hankinnat :yhteenveto :data (dec hoitokauden-numero)] {:yhteensa vuoden-hoidonjohtopalkkiot-yhteensa
                                                                                                                                                                                                                              :indeksikorjattu (indeksikorjaa vuoden-hoidonjohtopalkkiot-yhteensa)})))}}))
                                      {}
                                      (range 1 6))))))

(def rahavarausten-rajapinta {:rahavaraukset-otsikot any?
                              :rahavaraukset-yhteensa any?
                              :rahavaraukset any?
                              :rahavaraukset-kuukausitasolla? any?

                              :aseta-rahavaraukset! any?})

(defn rahavarausten-dr []
  (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                          rahavarausten-rajapinta
                          {:rahavaraukset-otsikot {:polut [[:gridit :rahavaraukset :otsikot]]
                                                   :haku identity}
                           :rahavaraukset-yhteensa {:polut [[:gridit :rahavaraukset :yhteensa :data]
                                                            [:gridit :rahavaraukset :yhteensa :nimi]]
                                                    :haku (fn [data nimi]
                                                            (assoc data :nimi nimi))}
                           :rahavaraukset-kuukausitasolla? {:polut [[:gridit :rahavaraukset :kuukausitasolla?]]
                                                            :haku identity}
                           :rahavaraukset {:polut [[:domain :rahavaraukset]
                                                   [:suodattimet :hankinnat :toimenpide]
                                                   [:suodattimet :hoitokauden-numero]]
                                           :haku (fn [rahavaraukset valittu-toimenpide hoitokauden-numero]
                                                   (let [arvot (into {}
                                                                     (mapv (fn [[tyyppi data]]
                                                                             [tyyppi (mapv #(select-keys % #{:maara :aika :yhteensa})
                                                                                           (get data (dec hoitokauden-numero)))])
                                                                           (get rahavaraukset valittu-toimenpide)))]
                                                     (with-meta arvot
                                                                {:valittu-toimenpide valittu-toimenpide})))}
                           :rahavaraukset-yhteenveto {:polut [[:gridit :rahavaraukset :seurannat]
                                                              [:suodattimet :hankinnat :toimenpide]]
                                                      :luonti (fn [seurannat valittu-toimenpide]
                                                                (vec
                                                                  (map (fn [[tyyppi _]]
                                                                         ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                                                         {(keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide)) [[:gridit :rahavaraukset :seurannat tyyppi]]})
                                                                       seurannat)))
                                                      :haku identity}
                           :rahavaraukset-data {:polut [[:domain :rahavaraukset]
                                                        [:suodattimet :hankinnat :toimenpide]
                                                        [:suodattimet :hoitokauden-numero]]
                                                :luonti (fn [rahavaraukset valittu-toimenpide hoitokauden-numero]
                                                          (let [toimenpiteen-rahavaraukset (get rahavaraukset valittu-toimenpide)]
                                                            (when (not (nil? (ffirst toimenpiteen-rahavaraukset)))
                                                              (mapv (fn [[tyyppi data]]
                                                                      {(keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide)) [[:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                                                                                                            [:gridit :rahavaraukset :varaukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]]})
                                                                    toimenpiteen-rahavaraukset))))
                                                :haku (fn [rahavaraukset johdetut-arvot]
                                                        (let [arvot (if (nil? johdetut-arvot)
                                                                      (mapv #(assoc (select-keys % #{:aika :maara})
                                                                                    :yhteensa (:maara %))
                                                                            rahavaraukset)
                                                                      (mapv (fn [ja a]
                                                                              (-> a
                                                                                  (update :maara #(or (get ja :maara) %))
                                                                                  (assoc :yhteensa (:maara a))
                                                                                  (select-keys #{:aika :maara :yhteensa})))
                                                                            johdetut-arvot
                                                                            rahavaraukset))]
                                                          arvot))}}
                          ;{:talvihoito {"vahinkojen-korjaukset" [[{:maara 3} {:maara 2} ...] [{:maara 3} {:maara 2} ...]]
                          ;              "akillinen-hoitotyo" [{:maara 1}]}

                          {:aseta-rahavaraukset! (fn [tila arvo {:keys [osa osan-paikka tyyppi]} paivitetaan-domain?]
                                                   (let [hoitokauden-numero (get-in tila [:domain :hoitokausi :hoitokauden-numero])
                                                         valittu-toimenpide (get-in tila [:suodattimet :hankinnat :toimenpide])
                                                         arvo (if (re-matches #"\d*,\d+" arvo)
                                                                (clj-str/replace arvo #"," ".")
                                                                arvo)
                                                         paattyy-desimaalierottajaan? (re-matches #"\d*(,|\.)$" arvo)
                                                         paivita-gridit (fn [tila]
                                                                          (update-in tila [:gridit :rahavaraukset :varaukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                                                     (fn [hoitokauden-varaukset]
                                                                                       (let [hoitokauden-varaukset (if (nil? hoitokauden-varaukset)
                                                                                                                     (vec (repeat 12 {}))
                                                                                                                     hoitokauden-varaukset)]
                                                                                         (if osan-paikka
                                                                                           (assoc-in hoitokauden-varaukset [(first osan-paikka) osa] arvo)
                                                                                           (mapv (fn [varaus]
                                                                                                   (assoc varaus osa arvo))
                                                                                                 hoitokauden-varaukset))))))
                                                         paivita-domain (fn [tila]
                                                                          (update-in tila [:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                                                     (fn [hoitokauden-varaukset]
                                                                                       (if osan-paikka
                                                                                         (assoc-in hoitokauden-varaukset [(first osan-paikka) osa] (js/Number arvo))
                                                                                         (mapv (fn [varaus]
                                                                                                 (assoc varaus osa (js/Number arvo)))
                                                                                               hoitokauden-varaukset)))))]
                                                     ;; Halutaan pitää data atomissa olevat arvot numeroina kun taasen käyttöliittymässä sen täytyy olla string (desimaalierottajan takia)
                                                     (if hoitokauden-numero
                                                       (if (and paivitetaan-domain?
                                                                (not paattyy-desimaalierottajaan?))
                                                         (-> tila paivita-domain paivita-gridit)
                                                         (paivita-gridit tila))
                                                       tila)))
                           :aseta-rahavaraukset-yhteenveto! (fn [tila arvo {:keys [osa tyyppi]}]
                                                              (let [arvo (cond
                                                                           ;; Voi olla nil on-focus eventin jälkeen, kun "vaihtelua/kk" teksti otetaan pois
                                                                           (nil? arvo) nil
                                                                           (re-matches #"\d*,\d+" arvo) (clj-str/replace arvo #"," ".")
                                                                           :else arvo)]
                                                                ;; Halutaan pitää data atomissa olevat arvot numeroina kun taasen käyttöliittymässä sen täytyy olla string (desimaalierottajan takia)
                                                                (update-in tila [:gridit :rahavaraukset :seurannat tyyppi]
                                                                           (fn [yhteenveto]
                                                                             (assoc yhteenveto osa arvo)))))}
                          {:otsikon-asettaminen {:polut [[:suodattimet :hankinnat :toimenpide]]
                                                 :aseta (fn [tila valittu-toimenpide]
                                                          (if valittu-toimenpide
                                                            (assoc-in tila [:gridit :rahavaraukset :otsikot :nimi] (-> valittu-toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize))
                                                            tila))}
                           :rahavaraukset-yhteenveto-asettaminen {:polut [[:domain :rahavaraukset]
                                                                          [:suodattimet :hankinnat :toimenpide]
                                                                          [:suodattimet :hoitokauden-numero]
                                                                          [:gridit :rahavaraukset :kuukausitasolla?]]
                                                                  :luonti (fn [rahavaraukset valittu-toimenpide hoitokauden-numero]
                                                                            (let [toimenpiteen-rahavaraukset (get rahavaraukset valittu-toimenpide)]
                                                                              (when (not (nil? (ffirst toimenpiteen-rahavaraukset)))
                                                                                (vec
                                                                                  (mapcat (fn [[tyyppi data]]
                                                                                            ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                                                                            [{(keyword (str "rahavaraukset-yhteenveto-" valittu-toimenpide "-" tyyppi "-" (dec hoitokauden-numero))) ^{:args [tyyppi]} [[:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                                                                                                                                                                                        [:suodattimet :hankinnat :toimenpide]]}]
                                                                                            #_(map (fn [index]
                                                                                                     ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                                                                                     {(keyword (str "rahavaraukset-yhteenveto-" valittu-toimenpide "-" tyyppi "-" index)) ^{:args [tyyppi]} [[:domain :rahavaraukset valittu-toimenpide tyyppi index]
                                                                                                                                                                                                             [:suodattimet :hankinnat :toimenpide]]})
                                                                                                   (range (count data))))
                                                                                          toimenpiteen-rahavaraukset)))))
                                                                  :aseta (fn [tila maarat valittu-toimenpide tyyppi]
                                                                           (let [yhteensa (reduce (fn [yhteensa {maara :maara}]
                                                                                                    (+ yhteensa maara))
                                                                                                  0
                                                                                                  maarat)]
                                                                             (assoc-in tila
                                                                                       [:gridit :rahavaraukset :seurannat tyyppi]
                                                                                       {:nimi (-> tyyppi (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)
                                                                                        :yhteensa yhteensa
                                                                                        :indeksikorjattu (indeksikorjaa yhteensa)
                                                                                        :maara (if (not (every? #(= (:maara (first maarat)) (:maara %))
                                                                                                                maarat))
                                                                                                 vaihtelua-teksti
                                                                                                 (:maara (first maarat)))})))}
                           :rahavaraukset-yhteensa-seuranta {:polut [[:gridit :rahavaraukset :seurannat]]
                                                             :init (fn [tila]
                                                                     (assoc-in tila [:gridit :rahavaraukset :yhteensa :data] nil))
                                                             :aseta (fn [tila data]
                                                                      (let [rahavaraukset-yhteensa (reduce (fn [summa [_ {yhteensa :yhteensa}]]
                                                                                                                   (+ summa yhteensa))
                                                                                                                 0
                                                                                                                 data)]
                                                                        (assoc-in tila [:gridit :rahavaraukset :yhteensa :data] {:yhteensa rahavaraukset-yhteensa
                                                                                                                                 :indeksikorjattu (indeksikorjaa rahavaraukset-yhteensa)})))}}))

(def hoidonjohtopalkkion-rajapinta {:otsikot any?
                                    ;; {:nimi string? :maara vector :yhteensa vector :indeksikorjattu vector}
                                    :yhteenveto any?
                                    :kuukausitasolla? any?
                                    :yhteensa any?
                                    ; vector-of-vector-of-maps
                                    :hoidonjohtopalkkio ::hoidonjohtopalkkiot-vuodelle

                                    :aseta-hoidonjohtopalkkio! any? #_(s/cat :arvo integer? :polku vector?)
                                    :aseta-yhteenveto! any?})

(defn hoidonjohtopalkkion-dr []
  (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                          hoidonjohtopalkkion-rajapinta
                          {:otsikot {:polut [[:gridit :hoidonjohtopalkkio :otsikot]]
                                     :haku identity}
                           :yhteenveto {:polut [[:gridit :hoidonjohtopalkkio :yhteenveto]
                                                [:domain :hoitokausi :hoitokauden-numero]]
                                        :haku (fn [{:keys [nimi maara yhteensa indeksikorjattu]}
                                                   hoitokauden-numero]
                                                (let [i (dec hoitokauden-numero)
                                                      yhteensa (get yhteensa i)
                                                      indeksikorjattu (get indeksikorjattu i)
                                                      maara (get maara i)]
                                                  {:nimi nimi :maara maara :yhteensa yhteensa :indeksikorjattu indeksikorjattu}))}
                           :kuukausitasolla? {:polut [[:gridit :hoidonjohtopalkkio :kuukausitasolla?]]
                                              :haku identity}
                           :hoidonjohtopalkkio {:polut [[:domain :hoidonjohtopalkkio] ;; [[{:maara int :aika timestamp} ...] [] ...]
                                                        [:domain :hoitokausi :hoitokauden-numero]
                                                        ;; Seurataan [:gridit :hoidonjohtopalkkio :palkkiot], koska arvo voi olla tyyliinsä "34," (eli loppuu pilkkuun)
                                                        [:gridit :hoidonjohtopalkkio :palkkiot]
                                                        [:gridit :hoidonjohtopalkkio :kuukausitasolla?]]
                                                :haku (fn [hoidonjohtopalkkio hoitokauden-numero johdetut-arvot kuukausitasolla?]
                                                        (let [arvot (if hoitokauden-numero
                                                                      (get hoidonjohtopalkkio (dec hoitokauden-numero))
                                                                      [])
                                                              arvot (mapv (fn [m]
                                                                            (if kuukausitasolla?
                                                                              (select-keys m #{:maara :aika})
                                                                              (select-keys m #{:aika})))
                                                                          arvot)
                                                              johdetut-arvot (get johdetut-arvot (dec hoitokauden-numero))]
                                                          (if (nil? johdetut-arvot)
                                                            arvot
                                                            (do
                                                              (when-not (= (count arvot) (count johdetut-arvot))
                                                                (warn "JOHDETUT ARVOT EI OLE YHTÄ PITKÄ KUIN ARVOT\n"
                                                                      "-> ARVOT\n"
                                                                      (with-out-str (cljs.pprint/pprint arvot))
                                                                      "-> JOHDETUT ARVOT\n"
                                                                      (with-out-str (cljs.pprint/pprint johdetut-arvot)))
                                                                arvot)
                                                              (vec
                                                                (map merge
                                                                     arvot
                                                                     johdetut-arvot))))))}
                           :yhteensa {:polut [[:gridit :hoidonjohtopalkkio :yhteensa]
                                              [:domain :hoitokausi :hoitokauden-numero]]
                                      :haku (fn [{:keys [nimi maara yhteensa indeksikorjattu]}
                                                 hoitokauden-numero]
                                              (merge {:nimi nimi :maara (get maara (dec hoitokauden-numero)) :yhteensa (get yhteensa (dec hoitokauden-numero)) :indeksikorjattu (get indeksikorjattu (dec hoitokauden-numero))}))}}
                          {:aseta-hoidonjohtopalkkio! (fn [tila arvo {:keys [aika osa osan-paikka]} paivitetaan-domain?]
                                                        (let [hoitokauden-numero (get-in tila [:domain :hoitokausi :hoitokauden-numero])
                                                              arvo (if (re-matches #"\d*,\d+" arvo)
                                                                     (clj-str/replace arvo #"," ".")
                                                                     arvo)
                                                              paattyy-desimaalierottajaan? (re-matches #"\d*(,|\.)$" arvo)
                                                              paivita-gridit (fn [tila]
                                                                               (update-in tila [:gridit :hoidonjohtopalkkio :palkkiot (dec hoitokauden-numero)]
                                                                                          (fn [hoitokauden-palkkiot]
                                                                                            (let [hoitokauden-palkkiot (if (nil? hoitokauden-palkkiot)
                                                                                                                         (vec (repeat 12 {}))
                                                                                                                         hoitokauden-palkkiot)]
                                                                                              (assoc-in hoitokauden-palkkiot [(first osan-paikka) osa] arvo)))))
                                                              paivita-domain (fn [tila]
                                                                               (update-in tila [:domain :hoidonjohtopalkkio (dec hoitokauden-numero)]
                                                                                          (fn [hoitokauden-palkkiot]
                                                                                            (assoc-in hoitokauden-palkkiot [(first osan-paikka) osa] (js/Number arvo)))))]
                                                          ;; Halutaan pitää data atomissa olevat arvot numeroina kun taasen käyttöliittymässä sen täytyy olla string (desimaalierottajan takia)
                                                          (if hoitokauden-numero
                                                            (if (and paivitetaan-domain?
                                                                     (not paattyy-desimaalierottajaan?))
                                                              (-> tila paivita-domain paivita-gridit)
                                                              (paivita-gridit tila))
                                                            tila)))
                           :aseta-yhteenveto! (fn [tila arvo tunniste]
                                                (let [hoitokauden-numero (get-in tila [:domain :hoitokausi :hoitokauden-numero])
                                                      arvo (if (re-matches #"\d*,\d+" arvo)
                                                             (clj-str/replace arvo #"," ".")
                                                             arvo)
                                                      paivita-gridit (fn [tila]
                                                                       (update-in tila [:gridit :hoidonjohtopalkkio :yhteenveto tunniste (dec hoitokauden-numero)]
                                                                                  (fn [tunnisteen-arvo]
                                                                                    arvo)))]
                                                  (if hoitokauden-numero
                                                    (paivita-gridit tila)
                                                    tila)))}
                          {:yhteenveto-seuranta {:polut [[:domain :hoidonjohtopalkkio]
                                                         [:domain :hoitokausi :hoitokauden-numero]
                                                         [:gridit :hoidonjohtopalkkio :kuukausitasolla?]]
                                                 :init (fn [tila]
                                                         (-> tila
                                                             (assoc-in [:gridit :hoidonjohtopalkkio :yhteenveto :yhteensa] (vec (repeat 5 nil)))
                                                             (assoc-in [:gridit :hoidonjohtopalkkio :yhteenveto :indeksikorjattu] (vec (repeat 5 nil)))))
                                                 :aseta (fn [tila hoidonjohtopalkkio hoitokauden-numero kuukausitasolla?]
                                                          (let [valitun-vuoden-hoidonjohtopalkkiot (get hoidonjohtopalkkio (dec hoitokauden-numero))
                                                                vuoden-hoidonjohtopalkkiot-yhteensa (reduce (fn [summa {maara :maara}]
                                                                                                              (+ summa maara))
                                                                                                            0
                                                                                                            valitun-vuoden-hoidonjohtopalkkiot)
                                                                maarat-samoja? (apply = (map :maara valitun-vuoden-hoidonjohtopalkkiot))
                                                                maara (cond
                                                                        kuukausitasolla? vaihtelua-teksti
                                                                        maarat-samoja? (get-in valitun-vuoden-hoidonjohtopalkkiot [0 :maara])
                                                                        :else "")]
                                                            (-> tila
                                                                (assoc-in [:gridit :hoidonjohtopalkkio :yhteenveto :maara (dec hoitokauden-numero)] maara)
                                                                (assoc-in [:gridit :hoidonjohtopalkkio :yhteenveto :yhteensa (dec hoitokauden-numero)] vuoden-hoidonjohtopalkkiot-yhteensa)
                                                                (assoc-in [:gridit :hoidonjohtopalkkio :yhteenveto :indeksikorjattu (dec hoitokauden-numero)] (indeksikorjaa vuoden-hoidonjohtopalkkiot-yhteensa)))))}
                           :yhteensa-seuranta {:polut [[:gridit :hoidonjohtopalkkio :yhteenveto :yhteensa]
                                                       [:domain :hoitokausi :hoitokauden-numero]]
                                               :init (fn [tila]
                                                       (-> tila
                                                           (assoc-in [:gridit :hoidonjohtopalkkio :yhteensa :yhteensa] (vec (repeat 5 nil)))
                                                           (assoc-in [:gridit :hoidonjohtopalkkio :yhteensa :indeksikorjattu] (vec (repeat 5 nil)))))
                                               :aseta (fn [tila yhteensa hoitokauden-numero]
                                                        (let [hoidonjohtopalkkiot-yhteensa (apply + yhteensa)]
                                                          (-> tila
                                                              (assoc-in [:gridit :hoidonjohtopalkkio :yhteensa :yhteensa (dec hoitokauden-numero)] hoidonjohtopalkkiot-yhteensa)
                                                              (assoc-in [:gridit :hoidonjohtopalkkio :yhteensa :indeksikorjattu (dec hoitokauden-numero)] (indeksikorjaa hoidonjohtopalkkiot-yhteensa)))))}}))

(defn paivita-solun-arvo [{:keys [paivitettava-asia arvo solu jarjesta-data triggeroi-seuranta?]
                           :or {jarjesta-data false triggeroi-seuranta? false}}
                          & args]
  (jarjesta-data jarjesta-data
    (triggeroi-seurannat triggeroi-seuranta?
      (case paivitettava-asia
        :hoidonjohtopalkkio (grid/aseta-rajapinnan-data! (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                         :aseta-hoidonjohtopalkkio!
                                                         arvo
                                                         (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                                         (first args))
        :hoidonjohtopalkkio-yhteenveto (grid/aseta-rajapinnan-data! (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                                    :aseta-yhteenveto!
                                                                    arvo
                                                                    (grid/solun-asia solu :tunniste-rajapinnan-dataan))
        :aseta-suunnittellut-hankinnat! (apply grid/aseta-rajapinnan-data!
                                               (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                               :aseta-suunnittellut-hankinnat!
                                               arvo
                                               (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                               args)
        :aseta-rahavaraukset! (apply grid/aseta-rajapinnan-data!
                                     (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                     :aseta-rahavaraukset!
                                     arvo
                                     (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                     args)
        :aseta-rahavaraukset-yhteenveto! (grid/aseta-rajapinnan-data!
                                                (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                :aseta-rahavaraukset-yhteenveto!
                                                arvo
                                                (grid/solun-asia solu :tunniste-rajapinnan-dataan))))))

(defn triggeroi-seuranta [solu seurannan-nimi]
  (grid/triggeroi-seuranta! (grid/osien-yhteinen-asia solu :datan-kasittelija) seurannan-nimi))

(defn paivita-raidat! [g]
  (let [paivita-luokat (fn [luokat odd?]
                         (if odd?
                           (-> luokat
                               (conj "table-default-odd")
                               (disj "table-default-even"))
                           (-> luokat
                               (conj "table-default-even")
                               (disj "table-default-odd"))))]
    (loop [[rivi & loput-rivit] (grid/nakyvat-rivit g)
           index 0]
      (when rivi
        (let [rivin-nimi (grid/hae-osa rivi :nimi)]
          (grid/paivita-grid! rivi
                              :parametrit
                              (fn [parametrit]
                                (update parametrit :class (fn [luokat]
                                                            (if (= ::valinta rivin-nimi)
                                                              (paivita-luokat luokat (not (odd? index)))
                                                              (paivita-luokat luokat (odd? index)))))))
          (recur loput-rivit
                 (if (= ::valinta rivin-nimi)
                   index
                   (inc index))))))))

(defn laajenna-solua-klikattu
  ([solu auki? dom-id polku-dataan] (laajenna-solua-klikattu solu auki? dom-id polku-dataan nil))
  ([solu auki? dom-id polku-dataan {:keys [aukeamis-polku sulkemis-polku]
                       :or {aukeamis-polku [:.. :.. 1]
                            sulkemis-polku [:.. :.. 1]}}]
   (if auki?
     (do (grid/nayta! (grid/osa-polusta solu aukeamis-polku))
         (paivita-raidat! (grid/osa-polusta (grid/root solu) polku-dataan))
         (r/flush)
         (r/after-render
           (fn []
             (.scrollIntoView (dom/getElement dom-id) #js {"block" "end" "inline" "nearest" "behavior" "smooth"}))))
     (grid/piillota! (grid/osa-polusta solu sulkemis-polku)))))


(defn yhteensa-yhteenveto [paivitetty-hoitokausi app]
  (+
    ;; Hankintakustannukset
    (apply +
           (map (fn [taulukko]
                  (let [yhteensa-sarake-index (p/otsikon-index taulukko "Yhteensä")]
                    (transduce
                      (comp (filter (fn [rivi]
                                      (= :laajenna-lapsilla (p/rivin-skeema taulukko rivi))))
                            (filter (fn [laajenna-lapsilla-rivi]
                                      (= (:hoitokausi laajenna-lapsilla-rivi) paivitetty-hoitokausi)))
                            (mapcat (fn [laajenna-lapsilla-rivi]
                                      (rest (p/arvo laajenna-lapsilla-rivi :lapset))))
                            (map (fn [kk-rivi]
                                   (get (p/arvo kk-rivi :lapset) yhteensa-sarake-index)))
                            (map (fn [kk-solu]
                                   (let [arvo (p/arvo kk-solu :arvo)]
                                     (if (number? arvo)
                                       arvo 0)))))
                      + 0 (p/arvo taulukko :lapset))))
                (concat (vals (get-in app [:hankintakustannukset :toimenpiteet]))
                        (vals (get-in app [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen])))))
    ;; Rahavaraukset
    (apply + (map (fn [taulukko]
                    (let [yhteensa-sarake-index (p/otsikon-index taulukko "Yhteensä")]
                      (transduce
                        (comp (filter (fn [rivi]
                                        (= :syottorivi (p/rivin-skeema taulukko rivi))))
                              (map (fn [syottorivi]
                                     (get (p/arvo syottorivi :lapset) yhteensa-sarake-index)))
                              (map (fn [yhteensa-solu]
                                     (let [arvo (p/arvo yhteensa-solu :arvo)]
                                       (if (number? arvo)
                                         arvo 0)))))
                        + 0 (p/arvo taulukko :lapset))))
                  (vals (get-in app [:hankintakustannukset :rahavaraukset]))))))

(defn tallennettavien-hankintojen-ajat
  [osa taulukko tayta-alas? kopioidaan-tuleville-vuosille? maksetaan]
  (let [[polku-container-riviin polku-riviin _] (p/osan-polku-taulukossa taulukko osa)
        polku-taulukosta-riviin (into [] (concat polku-container-riviin polku-riviin))
        aika-sarakkeen-index (p/otsikon-index taulukko "Nimi")

        aika (-> taulukko (get-in polku-taulukosta-riviin) (p/arvo :lapset) (get aika-sarakkeen-index) (p/arvo :arvo))
        v-kk {:vuosi (pvm/vuosi aika) :kuukausi (pvm/kuukausi aika)}

        muokattu-hoitokausi (:hoitokausi (get-in taulukko polku-container-riviin))
        tulevien-vuosien-ajat (when kopioidaan-tuleville-vuosille?
                                (keep-indexed (fn [index rivi]
                                                (when (> (:hoitokausi rivi) muokattu-hoitokausi)
                                                  {:vuosi (+ (:vuosi v-kk) (- (:hoitokausi rivi) muokattu-hoitokausi))
                                                   :kuukausi (:kuukausi v-kk)}))
                                              (p/arvo taulukko :lapset)))
        muokatun-summan-ajat-vuosittain (if tulevien-vuosien-ajat
                                          (into [] (cons v-kk tulevien-vuosien-ajat))
                                          [v-kk])]
    (reduce (fn [ajat {:keys [vuosi] :as tuleva-v-kk}]
              (let [muokattavan-rivin-aika (pvm/luo-pvm vuosi (dec (:kuukausi v-kk)) 15)
                    muokatun-hoitokauden-paattymisvuosi (pvm/vuosi (second (pvm/paivamaaran-hoitokausi muokattavan-rivin-aika)))
                    kauden-viimeinen-aika (case maksetaan
                                            :kesakausi (pvm/luo-pvm muokatun-hoitokauden-paattymisvuosi 8 30)
                                            :talvikausi (pvm/luo-pvm muokatun-hoitokauden-paattymisvuosi 3 30)
                                            :molemmat (pvm/luo-pvm muokatun-hoitokauden-paattymisvuosi 8 30))]
                (if tayta-alas?
                  (into []
                        (concat ajat
                                (keep (fn [[kuun-ensimmainen-pvm _]]
                                        (let [kuukausi (pvm/kuukausi kuun-ensimmainen-pvm)
                                              vuosi (pvm/vuosi kuun-ensimmainen-pvm)]
                                          {:vuosi vuosi
                                           :kuukausi kuukausi}))
                                      (pvm/aikavalin-kuukausivalit [muokattavan-rivin-aika kauden-viimeinen-aika]))))
                  (conj ajat tuleva-v-kk))))
            [] muokatun-summan-ajat-vuosittain)))

(defrecord TaulukoidenVakioarvot [])
(defrecord FiltereidenAloitusarvot [])

(defrecord Hoitokausi [])
(defrecord YleisSuodatinArvot [])
(defrecord HaeIndeksitOnnistui [vastaus])
(defrecord HaeIndeksitEpaonnistui [vastaus])
(defrecord Oikeudet [])
(defrecord HaeKustannussuunnitelma [hankintojen-taulukko rahavarausten-taulukko
                                    johto-ja-hallintokorvaus-laskulla-taulukko johto-ja-hallintokorvaus-yhteenveto-taulukko
                                    erillishankinnat-taulukko toimistokulut-taulukko
                                    johtopalkkio-taulukko])
(defrecord HaeTavoiteJaKattohintaOnnistui [vastaus])
(defrecord HaeTavoiteJaKattohintaEpaonnistui [vastaus])
(defrecord HaeHankintakustannuksetOnnistui [vastaus hankintojen-taulukko rahavarausten-taulukko
                                            johto-ja-hallintokorvaus-laskulla-taulukko johto-ja-hallintokorvaus-yhteenveto-taulukko
                                            erillishankinnat-taulukko toimistokulut-taulukko
                                            johtopalkkio-taulukko])
(defrecord HaeHankintakustannuksetEpaonnistui [vastaus])
(defrecord LaajennaSoluaKlikattu [polku-taulukkoon rivin-id this auki?])
(defrecord YhteenvetoLaajennaSoluaKlikattu [polku-taulukkoon rivin-id this auki?])
(defrecord MuutaTaulukonOsa [osa polku-taulukkoon arvo])
(defrecord MuutaTaulukonOsanSisarta [osa sisaren-tunniste polku-taulukkoon arvo])
(defrecord PaivitaTaulukonOsa [osa polku-taulukkoon paivitys-fn])
(defrecord PaivitaKustannussuunnitelmanYhteenvedot [])
(defrecord PaivitaToimenpideTaulukko [maara-solu polku-taulukkoon])
(defrecord TaytaAlas [maara-solu polku-taulukkoon])
(defrecord ToggleHankintakustannuksetOtsikko [kylla?])
(defrecord PaivitaJHRivit [paivitetty-osa])
(defrecord PaivitaSuunnitelmienTila [paivitetyt-taulukot])
(defrecord MaksukausiValittu [])
(defrecord TallennaHankintasuunnitelma [toimenpide-avain osa polku-taulukkoon tayta-alas? laskutuksen-perusteella-taulukko?])
(defrecord TallennaHankintasuunnitelmaOnnistui [vastaus])
(defrecord TallennaHankintasuunnitelmaEpaonnistui [vastaus])
(defrecord TallennaKiinteahintainenTyo [toimenpide-avain osa polku-taulukkoon tayta-alas?])
(defrecord TallennaKiinteahintainenTyoOnnistui [vastaus])
(defrecord TallennaKiinteahintainenTyoEpaonnistui [vastaus])
(defrecord TallennaKustannusarvoituTyo [tallennettava-asia toimenpide-avain arvo ajat])
(defrecord TallennaKustannusarvoituTyoOnnistui [vastaus])
(defrecord TallennaKustannusarvoituTyoEpaonnistui [vastaus])
(defrecord TallennaJohtoJaHallintokorvaukset [osa polku-taulukkoon])
(defrecord TallennaJohtoJaHallintokorvauksetOnnistui [vastaus])
(defrecord TallennaJohtoJaHallintokorvauksetEpaonnistui [vastaus])
(defrecord TallennaJaPaivitaTavoiteSekaKattohinta [])
(defrecord TallennaJaPaivitaTavoiteSekaKattohintaOnnistui [vastaus])
(defrecord TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui [vastaus])

(defrecord PaivitaHoidonjohtopalkkiot [arvo])

;; UUSI GRID

(defrecord ASDLaajennaSoluaKlikattu [solu auki?])
(defrecord ASDPaivitaSolunArvo [solu arvo])

(defn urakan-ajat []
  (let [urakan-aloitus-pvm (-> @tiedot/tila :yleiset :urakka :alkupvm)]
    (into []
          (drop 9
                (drop-last 3
                           (mapcat (fn [vuosi]
                                     (map #(identity
                                             {:vuosi vuosi
                                              :kuukausi %})
                                          (range 1 13)))
                                   (range (pvm/vuosi urakan-aloitus-pvm) (+ (pvm/vuosi urakan-aloitus-pvm) 6))))))))

(defn tarkista-datan-validius! [hankinnat hankinnat-laskutukseen-perustuen]
  (let [[nil-pvm-hankinnat hankinnat] (reduce (fn [[nil-pvmt pvmt] {:keys [vuosi kuukausi] :as hankinta}]
                                                (if (and vuosi kuukausi)
                                                  [nil-pvmt (conj pvmt (assoc hankinta :pvm (pvm/luo-pvm vuosi (dec kuukausi) 15)))]
                                                  [(conj nil-pvmt hankinta) pvmt]))
                                              [[] []] (concat hankinnat hankinnat-laskutukseen-perustuen))
        hankintojen-vuodet (sort (map pvm/vuosi (flatten (keys (group-by #(pvm/paivamaaran-hoitokausi (:pvm %)) hankinnat)))))
        [urakan-aloitus-vuosi urakan-lopetus-vuosi] [(pvm/vuosi (-> @tiedot/yleiset :urakka :alkupvm))
                                                     (pvm/vuosi (-> @tiedot/yleiset :urakka :loppupvm))]
        hankintoja-urakan-hoitokausien-ulkopuolella? (or (and (first hankintojen-vuodet)
                                                              (< (first hankintojen-vuodet) urakan-aloitus-vuosi))
                                                         (and (second hankintojen-vuodet)
                                                              (> (second hankintojen-vuodet) urakan-lopetus-vuosi)))
        nil-pvm-hankintoja? (> (count nil-pvm-hankinnat) 0)
        hoitokausien-ulkopuolella-teksti (str "Urakalle on merkattu vuodet " urakan-aloitus-vuosi " - " urakan-lopetus-vuosi
                                              ", mutta urakalle on merkattu hankintoja vuosille " (first hankintojen-vuodet) " - " (last hankintojen-vuodet) ".")
        nil-hankintoja-teksti (str "Urakalle on merkattu " (count nil-pvm-hankinnat) " hankintaa ilman päivämäärää.")]
    (when (or hankintoja-urakan-hoitokausien-ulkopuolella? nil-pvm-hankintoja?)
      (viesti/nayta! (cond-> ""
                             hankintoja-urakan-hoitokausien-ulkopuolella? (str hoitokausien-ulkopuolella-teksti "\n")
                             nil-pvm-hankintoja? (str nil-hankintoja-teksti))
                     :warning viesti/viestin-nayttoaika-pitka))))

(defn paivita-rahavaraus-summat-automaattisesti [taulukko polku-taulukkoon app]
  (let [yhteensa-otsikon-index (p/otsikon-index taulukko "Yhteensä")
        indeksikorjattu-otsikon-index (p/otsikon-index taulukko "Indeksikorjattu")
        yhteensa-rivin-index (-> taulukko (p/arvo :lapset) count dec)
        yhteensa-vuodet (fn [this {yhteenlasku-osat :uusi}]
                          (apply + (map (fn [osa]
                                          (let [arvo (p/arvo osa :arvo)]
                                            (if (number? arvo)
                                              arvo 0)))
                                        (vals yhteenlasku-osat))))
        indeksikorjattu-vuodet (fn [this arvot]
                                 (indeksikorjaa (yhteensa-vuodet this arvot)))
        paivita-osa-automaattisesti! (fn [taulukko polku f]
                                       (tyokalut/paivita-asiat-taulukossa taulukko
                                                                          polku
                                                                          (fn [taulukko taulukon-asioiden-polut]
                                                                            (let [[rivit rivi osat osa] taulukon-asioiden-polut
                                                                                  rivit (get-in taulukko rivit)
                                                                                  osa (get-in taulukko osa)
                                                                                  polut-yhteenlasku-osiin (keep (fn [rivi]
                                                                                                                  (when (= :syottorivi (p/rivin-skeema taulukko rivi))
                                                                                                                    (into [] (apply concat polku-taulukkoon
                                                                                                                                    (p/osan-polku-taulukossa taulukko (nth (p/arvo rivi :lapset) yhteensa-otsikon-index))))))
                                                                                                                (butlast (rest rivit)))]
                                                                              (-> osa
                                                                                  (p/lisaa-renderointi-derefable! tiedot/suunnittelu-kustannussuunnitelma polut-yhteenlasku-osiin app)
                                                                                  (p/lisaa-muodosta-arvo f))))))]
    (-> taulukko
        (paivita-osa-automaattisesti! [yhteensa-rivin-index yhteensa-otsikon-index] yhteensa-vuodet)
        (paivita-osa-automaattisesti! [yhteensa-rivin-index indeksikorjattu-otsikon-index] indeksikorjattu-vuodet))))

(defn paivita-maara-kk-taulukon-summat-automaattisesti [taulukko polku-taulukkoon app]
  (let [yhteensa-otsikon-index (p/otsikon-index taulukko "Yhteensä")
        indeksikorjattu-otsikon-index (p/otsikon-index taulukko "Indeksikorjattu")
        yhteensa-rivin-index (-> taulukko (p/arvo :lapset) count dec)
        {kuluva-hoitovuosi :vuosi} (:kuluva-hoitokausi app)
        vuodet-yhteensa (fn [this {maara-kk-osat :uusi}]
                          (let [yhteensa-osan-arvo (-> maara-kk-osat vals first (p/arvo :arvo))]
                            (if (number? yhteensa-osan-arvo)
                              (* 5 yhteensa-osan-arvo)
                              0)))
        vuodet-yhteensa-indeksikorjattuna (fn [this {maara-kk-osat :uusi}]
                                            (let [yhteensa-osan-arvo (-> maara-kk-osat vals first (p/arvo :arvo))]
                                              (if (number? yhteensa-osan-arvo)
                                                (apply + (map (fn [hoitokauden-numero]
                                                                (indeksikorjaa yhteensa-osan-arvo hoitokauden-numero))
                                                              (range 1 6)))
                                                0)))
        paivita-osa-automaattisesti! (fn [taulukko osan-index f]
                                       (tyokalut/paivita-asiat-taulukossa taulukko
                                                                          [yhteensa-rivin-index osan-index]
                                                                          (fn [taulukko taulukon-asioiden-polut]
                                                                            (let [[rivit rivi osat osan-polku] taulukon-asioiden-polut
                                                                                  osa (get-in taulukko osan-polku)
                                                                                  rivit (get-in taulukko rivit)
                                                                                  maara-yhteensa-solun-polku (apply concat
                                                                                                                    (p/osan-polku-taulukossa taulukko
                                                                                                                                             (nth (p/arvo (second rivit) :lapset)
                                                                                                                                                  yhteensa-otsikon-index)))
                                                                                  polku-muokkausrivin-yhteensa-osaan (vec (concat
                                                                                                                            polku-taulukkoon
                                                                                                                            maara-yhteensa-solun-polku))]
                                                                              (-> osa
                                                                                  (p/lisaa-renderointi-derefable! tiedot/suunnittelu-kustannussuunnitelma [polku-muokkausrivin-yhteensa-osaan] app)
                                                                                  (p/lisaa-muodosta-arvo f))))))]
    (-> taulukko
        (paivita-osa-automaattisesti! yhteensa-otsikon-index vuodet-yhteensa)
        (paivita-osa-automaattisesti! indeksikorjattu-otsikon-index vuodet-yhteensa-indeksikorjattuna))))

(defn paivita-hankinta-summat-automaattisesti [taulukko polku-taulukkoon app]
  (let [yhteensa-otsikon-index (p/otsikon-index taulukko "Yhteensä")
        indeksikorjattu-otsikon-index (p/otsikon-index taulukko "Indeksikorjattu")
        summa-rivin-index (-> taulukko (p/arvo :lapset) count dec)
        paivita-laajenna-osa-automaattisesti! (fn [taulukko polku f]
                                                (tyokalut/paivita-asiat-taulukossa taulukko
                                                                                   polku
                                                                                   (fn [taulukko taulukon-asioiden-polut]
                                                                                     (let [[rivit laajenna-lapsilla-rivi laajenna-lapsilla-rivit paarivi osat osa] taulukon-asioiden-polut
                                                                                           laajenna-lapsilla-rivit (get-in taulukko laajenna-lapsilla-rivit)
                                                                                           osa (get-in taulukko osa)

                                                                                           polut-summa-osiin (map (fn [laajenna-rivi]
                                                                                                                    (into []
                                                                                                                          (apply concat polku-taulukkoon
                                                                                                                                 (p/osan-polku-taulukossa taulukko
                                                                                                                                                          (nth (p/arvo laajenna-rivi :lapset)
                                                                                                                                                               yhteensa-otsikon-index)))))
                                                                                                                  (rest laajenna-lapsilla-rivit))]
                                                                                       (-> osa
                                                                                           (p/lisaa-renderointi-derefable! tiedot/suunnittelu-kustannussuunnitelma polut-summa-osiin app)
                                                                                           (p/lisaa-muodosta-arvo f))))))
        paivita-summarivin-osa-automaattisesti! (fn [taulukko polku f]
                                                  (tyokalut/paivita-asiat-taulukossa taulukko
                                                                                     polku
                                                                                     (fn [taulukko taulukon-asioiden-polut]
                                                                                       (let [[rivit rivi osat osa] taulukon-asioiden-polut
                                                                                             rivit (get-in taulukko rivit)
                                                                                             osa (get-in taulukko osa)
                                                                                             polut-yhteenlasku-osiin (apply concat
                                                                                                                            (keep (fn [rivi]
                                                                                                                                    (when (= :laajenna-lapsilla (p/rivin-skeema taulukko rivi))
                                                                                                                                      (map (fn [index]
                                                                                                                                             (into [] (apply concat polku-taulukkoon
                                                                                                                                                             (p/osan-polku-taulukossa taulukko (tyokalut/get-in-riviryhma rivi [index yhteensa-otsikon-index])))))
                                                                                                                                           (range 1 (count (p/arvo rivi :lapset))))))
                                                                                                                                  rivit))]
                                                                                         (-> osa
                                                                                             (p/lisaa-renderointi-derefable! tiedot/suunnittelu-kustannussuunnitelma polut-yhteenlasku-osiin app)
                                                                                             (p/lisaa-muodosta-arvo f))))))
        hoitokausi-yhteensa (fn [this {summa-osat :uusi}]
                              (apply + (map (fn [osa]
                                              (let [arvo (p/arvo osa :arvo)]
                                                (if (number? arvo)
                                                  arvo 0)))
                                            (vals summa-osat))))
        indeksikorjattu-yhteensa (fn [this arvot]
                                   (indeksikorjaa (hoitokausi-yhteensa this arvot) (get-in this [::p/lisatty-data :hoitokausi])))
        indeksikorjattu-kaikki-kaudet-yhteensa (fn [this {summa-osat :uusi}]
                                                 (let [arvot-hoitokausittain (group-by (fn [[polku data]]
                                                                                         (->> polku (drop-last 4) last))
                                                                                       (seq summa-osat))]
                                                   (reduce (fn [summa [hoitokausi summa-osat]]
                                                             (+ summa
                                                                (indeksikorjaa (hoitokausi-yhteensa this {:uusi (into {} summa-osat)}) hoitokausi)))
                                                           0
                                                           arvot-hoitokausittain)))
        ;; Sama funktio, mutta parametrien sisältö on eri.
        hoitokaudet-yhteensa hoitokausi-yhteensa
        indeksikorjatut-yhteensa indeksikorjattu-yhteensa]
    (-> taulukko
        (paivita-laajenna-osa-automaattisesti! [:laajenna-lapsilla 0 yhteensa-otsikon-index] hoitokausi-yhteensa)
        (paivita-laajenna-osa-automaattisesti! [:laajenna-lapsilla 0 indeksikorjattu-otsikon-index] indeksikorjattu-yhteensa)
        (paivita-summarivin-osa-automaattisesti! [summa-rivin-index yhteensa-otsikon-index] hoitokaudet-yhteensa)
        (paivita-summarivin-osa-automaattisesti! [summa-rivin-index indeksikorjattu-otsikon-index] indeksikorjattu-kaikki-kaudet-yhteensa))))

(defn suunnitelman-osat
  [paivitetyt-taulukot-instanssi kuluva-hoitokausi suunnitelmien-tila-taulukko
   entiset-tilat
   {:keys [valinnat toimenpiteet toimenpiteet-laskutukseen-perustuen rahavaraukset]}
   {:keys [johto-ja-hallintokorvaus-yhteenveto toimistokulut johtopalkkio erillishankinnat]}]
  (let [seuraava-hoitokausi (if (not= 5 (:vuosi kuluva-hoitokausi))
                              (pvm/paivamaaran-hoitokausi (pvm/lisaa-vuosia (pvm/nyt) 1)) nil)
        toimenpiteiden-tila (fn [toimenpide toimenpiteet toimenpiteet-laskutukseen-perustuen valinnat hoitokausi]
                              (let [taytetyt-rivit (tyokalut/taulukko->data (get toimenpiteet toimenpide)
                                                                            #{:lapset})
                                    laskutukseen-perustuen? ((:laskutukseen-perustuen valinnat) toimenpide)
                                    taytetyt-rivit (if laskutukseen-perustuen?
                                                     (concat taytetyt-rivit
                                                             (tyokalut/taulukko->data (get toimenpiteet-laskutukseen-perustuen toimenpide)
                                                                                      #{:lapset}))
                                                     taytetyt-rivit)
                                    taytetty?-hoitokauden-rivit (keep (fn [data]
                                                                        (when (pvm/valissa? (get data "Nimi")
                                                                                            (first hoitokausi)
                                                                                            (second hoitokausi))
                                                                          (let [maara (get data "Yhteensä")]
                                                                            (and (not= 0 maara)
                                                                                 (not (nil? maara))))))
                                                                      taytetyt-rivit)]
                                (cond
                                  (every? true? taytetty?-hoitokauden-rivit) :valmis
                                  (some true? taytetty?-hoitokauden-rivit) :kesken
                                  :else :ei-tehty)))
        rahavarausten-tila (fn [toimenpide rahavaraukset suunnitelma]
                             (let [rivi-taytetty? (first (keep (fn [rivi]
                                                                 (when (= (:suunnitelma rivi) suunnitelma)
                                                                   (let [yhteensa (p/arvo (last (p/arvo rivi :lapset)) :arvo)]
                                                                     (and (not= 0 yhteensa)
                                                                          (not (nil? yhteensa))))))
                                                               (p/arvo (get rahavaraukset toimenpide) :lapset)))]
                               (if rivi-taytetty?
                                 :valmis
                                 :ei-tehty)))
        hallinnollisten-yksirivisten-tila (fn [taulukko]
                                            (let [arvo (-> taulukko (tyokalut/taulukko->data #{:rivi :syottorivi}) first (get "Yhteensä"))
                                                  taytetty? (and (not= 0 arvo)
                                                                 (not (nil? arvo)))
                                                  tila (if taytetty?
                                                         :valmis
                                                         :ei-tehty)]
                                              (if (not= 5 (:vuosi kuluva-hoitokausi))
                                                {:kuluva-vuosi tila
                                                 :tuleva-vuosi tila}
                                                {:kuluva-vuosi tila})))
        johto-ja-hallintokorvausten-tila (fn [taulukko]
                                           (let [arvorivit (-> taulukko tyokalut/taulukko->data rest butlast)
                                                 vuodet (->> (p/arvo taulukko :lapset) (map :vuodet) rest butlast)
                                                 data (map (fn [arvot vuodet]
                                                             (assoc arvot :vuodet vuodet))
                                                           arvorivit vuodet)
                                                 kuluva-hoitovuosi (:vuosi kuluva-hoitokausi)
                                                 taman-vuoden-otsikko (str kuluva-hoitovuosi ".vuosi/€")
                                                 tulevan-vuoden-otsikko (when-not (= 5 kuluva-hoitovuosi)
                                                                          (str (inc kuluva-hoitovuosi) ".vuosi/€"))
                                                 kuluvan-vuoden-arvot (keep #(when (contains? (:vuodet %) kuluva-hoitovuosi)
                                                                               (get % taman-vuoden-otsikko))
                                                                            data)
                                                 tulevan-vuoden-arvot (keep #(when (and tulevan-vuoden-otsikko (contains? (:vuodet %) (inc kuluva-hoitovuosi)))
                                                                               (get % tulevan-vuoden-otsikko))
                                                                            data)
                                                 arvo-loytyy? (fn [arvo]
                                                                (and (not= 0 arvo)
                                                                     (not (nil? arvo))))
                                                 kuluvan-vuoden-tila (cond
                                                                       (every? arvo-loytyy? kuluvan-vuoden-arvot) :valmis
                                                                       (some arvo-loytyy? kuluvan-vuoden-arvot) :kesken
                                                                       :else :ei-tehty)
                                                 tulevan-vuoden-tila (cond
                                                                       (empty? tulevan-vuoden-arvot) nil
                                                                       (every? arvo-loytyy? tulevan-vuoden-arvot) :valmis
                                                                       (some arvo-loytyy? tulevan-vuoden-arvot) :kesken
                                                                       :else :ei-tehty)]
                                             {:kuluva-vuosi kuluvan-vuoden-tila
                                              :tuleva-vuosi tulevan-vuoden-tila}))

        hankintakustannukset (some (fn [rivi]
                                     (when (= (p/rivin-skeema suunnitelmien-tila-taulukko rivi) :hankintakustannukset)
                                       rivi))
                                   (p/arvo suunnitelmien-tila-taulukko :lapset))
        hankintakustannusten-tila (reduce (fn [tilat toimenpiderivi-lapsilla]
                                            (let [toimenpide (:toimenpide toimenpiderivi-lapsilla)
                                                  muuttunut-tehtava (some (fn [[tehtava muuttunut?]]
                                                                            (when muuttunut?
                                                                              tehtava))
                                                                          (get-in paivitetyt-taulukot-instanssi [:hankinnat toimenpide]))
                                                  entinen-toimenpide-tila (get-in entiset-tilat [:hankintakustannukset toimenpide])]
                                              (if toimenpide
                                                (assoc tilat toimenpide
                                                             (if (and (nil? muuttunut-tehtava) entinen-toimenpide-tila)
                                                               entinen-toimenpide-tila
                                                               (into {}
                                                                     (keep (fn [suunnitelmarivi]
                                                                             (let [suunnitelma (:suunnitelma suunnitelmarivi)
                                                                                   entinen-suunnitelma (get entinen-toimenpide-tila suunnitelma)]
                                                                               (when suunnitelma
                                                                                 (if (or (and (#{:kokonaishintainen :lisatyo :laskutukseen-perustuen-valinta} muuttunut-tehtava)
                                                                                              (= :kokonaishintainen-ja-lisatyo suunnitelma))
                                                                                         (#{:akillinen-hoitotyo :vahinkojen-korjaukset :muut-rahavaraukset} suunnitelma)
                                                                                         (nil? entinen-suunnitelma))
                                                                                   [suunnitelma (if (= :kokonaishintainen-ja-lisatyo suunnitelma)
                                                                                                  {:kuluva-vuosi (toimenpiteiden-tila toimenpide toimenpiteet toimenpiteet-laskutukseen-perustuen valinnat (:pvmt kuluva-hoitokausi))
                                                                                                   :tuleva-vuosi (toimenpiteiden-tila toimenpide toimenpiteet toimenpiteet-laskutukseen-perustuen valinnat seuraava-hoitokausi)}
                                                                                                  {:kuluva-vuosi (rahavarausten-tila toimenpide rahavaraukset suunnitelma)
                                                                                                   :tuleva-vuosi (rahavarausten-tila toimenpide rahavaraukset suunnitelma)})]
                                                                                   [suunnitelma entinen-suunnitelma]))))
                                                                           (p/arvo toimenpiderivi-lapsilla :lapset)))))
                                                tilat)))
                                          {} (p/arvo hankintakustannukset :lapset))
        hallinnollisten-tila {(:erillishankinnat hallinnollisten-idt) (if (or (get-in paivitetyt-taulukot-instanssi [:hallinnolliset (:erillishankinnat hallinnollisten-idt)])
                                                                              (nil? entiset-tilat))
                                                                        (hallinnollisten-yksirivisten-tila erillishankinnat)
                                                                        (get-in entiset-tilat [:hallinnolliset (:erillishankinnat hallinnollisten-idt)]))
                              (:johto-ja-hallintokorvaus hallinnollisten-idt) (if (or (get-in paivitetyt-taulukot-instanssi [:hallinnolliset (:johto-ja-hallintokorvaus hallinnollisten-idt)])
                                                                                      (nil? entiset-tilat))
                                                                                (johto-ja-hallintokorvausten-tila johto-ja-hallintokorvaus-yhteenveto)
                                                                                (get-in entiset-tilat [:hallinnolliset (:johto-ja-hallintokorvaus hallinnollisten-idt)]))
                              (:toimistokulut-taulukko hallinnollisten-idt) (if (or (get-in paivitetyt-taulukot-instanssi [:hallinnolliset (:toimistokulut-taulukko hallinnollisten-idt)])
                                                                                    (nil? entiset-tilat))
                                                                              (hallinnollisten-yksirivisten-tila toimistokulut)
                                                                              (get-in entiset-tilat [:hallinnolliset (:toimistokulut-taulukko hallinnollisten-idt)]))
                              (:hoidonjohtopalkkio hallinnollisten-idt) (if (or (get-in paivitetyt-taulukot-instanssi [:hallinnolliset (:hoidonjohtopalkkio hallinnollisten-idt)])
                                                                                (nil? entiset-tilat))
                                                                          (hallinnollisten-yksirivisten-tila johtopalkkio)
                                                                          (get-in entiset-tilat [:hallinnolliset (:hoidonjohtopalkkio hallinnollisten-idt)]))}]
    {:hankintakustannukset hankintakustannusten-tila
     :hallinnolliset hallinnollisten-tila}))

(defn paivita-suunnitelmien-tila-taulukko
  [suunnitelmien-tila-taulukko paivitetyt-taulukot-instanssi tilat kuluva-hoitokausi]
  (let [taman-vuoden-otsikon-index (if (not= 5 (:vuosi kuluva-hoitokausi))
                                     1 2)
        seuraavan-vuoden-otsikon-index (if (not= 5 (:vuosi kuluva-hoitokausi))
                                         2 nil)
        tilan-ikoni (fn [tila]
                      (case tila
                        :valmis ikonit/ok
                        :kesken ikonit/livicon-question
                        :ei-tehty ikonit/remove))
        paivita-vetotoimenpiderivi (fn [osa tilat toimenpide ajankohta]
                                     (p/paivita-arvo osa :arvo
                                                     (fn [ikoni-ja-teksti]
                                                       (let [toimenpiteen-tilat (map (fn [[suunnitelma ajankohdan-tila]]
                                                                                       (get ajankohdan-tila ajankohta))
                                                                                     (get tilat toimenpide))
                                                             tila (cond
                                                                    (every? #(= :valmis %) toimenpiteen-tilat) :valmis
                                                                    (every? #(= :ei-tehty %) toimenpiteen-tilat) :ei-tehty
                                                                    :else :kesken)]
                                                         (assoc ikoni-ja-teksti :ikoni (case tila
                                                                                         :valmis ikonit/ok
                                                                                         :kesken ikonit/livicon-question
                                                                                         :ei-tehty ikonit/remove))))))
        paivita-linkkitoimenpiderivi (fn [osa tilat ajankohta]
                                       (p/paivita-arvo osa :arvo
                                                       (fn [ikoni-ja-teksti]
                                                         (let [toimenpiteen-tilat (mapcat (fn [[toimenpide suunnitelmien-tilat]]
                                                                                            (map (fn [[suunnitelma ajankohdan-tila]]
                                                                                                   (get ajankohdan-tila ajankohta))
                                                                                                 suunnitelmien-tilat))
                                                                                          tilat)
                                                               tila (cond
                                                                      (every? #(= :valmis %) toimenpiteen-tilat) :valmis
                                                                      (every? #(= :ei-tehty %) toimenpiteen-tilat) :ei-tehty
                                                                      :else :kesken)]
                                                           (assoc ikoni-ja-teksti :ikoni (case tila
                                                                                           :valmis ikonit/ok
                                                                                           :kesken ikonit/livicon-question
                                                                                           :ei-tehty ikonit/remove))))))
        paivita-hankintakustannusten-suunnitelmien-tila (fn [suunnitelmien-tila-taulukko
                                                             toimenpide suunnitelma]
                                                          (p/paivita-arvo suunnitelmien-tila-taulukko :lapset
                                                                          (fn [rivit]
                                                                            (mapv (fn [rivi]
                                                                                    (if (p/janan-id? rivi :hankintakustannukset-vanhempi)
                                                                                      (p/paivita-arvo rivi :lapset
                                                                                                      (fn [rivit]
                                                                                                        (mapv (fn [toimenpiderivi-lapsilla]
                                                                                                                (cond
                                                                                                                  (= (:toimenpide toimenpiderivi-lapsilla) toimenpide)
                                                                                                                  (p/paivita-arvo toimenpiderivi-lapsilla :lapset
                                                                                                                                  (fn [rivit]
                                                                                                                                    (mapv (fn [suunnitelmarivi]
                                                                                                                                            (let [rivin-tilat (get-in tilat [:hankintakustannukset toimenpide suunnitelma])]
                                                                                                                                              (cond
                                                                                                                                                (= (:suunnitelma suunnitelmarivi) suunnitelma)
                                                                                                                                                (p/paivita-arvo suunnitelmarivi :lapset
                                                                                                                                                                (fn [osat]
                                                                                                                                                                  (tyokalut/mapv-indexed (fn [index osa]
                                                                                                                                                                                           (cond
                                                                                                                                                                                             (= taman-vuoden-otsikon-index index) (p/paivita-arvo osa :arvo
                                                                                                                                                                                                                                                  (fn [ikoni-ja-teksti]
                                                                                                                                                                                                                                                    (assoc ikoni-ja-teksti :ikoni (tilan-ikoni (get rivin-tilat :kuluva-vuosi)))))
                                                                                                                                                                                             (= seuraavan-vuoden-otsikon-index index) (p/paivita-arvo osa :arvo
                                                                                                                                                                                                                                                      (fn [ikoni-ja-teksti]
                                                                                                                                                                                                                                                        (assoc ikoni-ja-teksti :ikoni (tilan-ikoni (get rivin-tilat :tuleva-vuosi)))))
                                                                                                                                                                                             :else osa))
                                                                                                                                                                                         osat)))
                                                                                                                                                ;; Tämä on aggregaattirivi
                                                                                                                                                (= :laajenna-toimenpide (p/rivin-skeema suunnitelmien-tila-taulukko suunnitelmarivi))
                                                                                                                                                (p/paivita-arvo suunnitelmarivi :lapset
                                                                                                                                                                (fn [osat]
                                                                                                                                                                  (tyokalut/mapv-indexed (fn [index osa]
                                                                                                                                                                                           (cond
                                                                                                                                                                                             (= taman-vuoden-otsikon-index index) (paivita-vetotoimenpiderivi osa (:hankintakustannukset tilat) toimenpide :kuluva-vuosi)
                                                                                                                                                                                             (= seuraavan-vuoden-otsikon-index index) (paivita-vetotoimenpiderivi osa (:hankintakustannukset tilat) toimenpide :tuleva-vuosi)
                                                                                                                                                                                             :else osa))
                                                                                                                                                                                         osat)))
                                                                                                                                                :else suunnitelmarivi)))
                                                                                                                                          rivit)))
                                                                                                                  ;; Tämä on aggregaatti
                                                                                                                  (= :linkkiotsikko (p/rivin-skeema suunnitelmien-tila-taulukko toimenpiderivi-lapsilla))
                                                                                                                  (p/paivita-arvo toimenpiderivi-lapsilla :lapset
                                                                                                                                  (fn [osat]
                                                                                                                                    (tyokalut/mapv-indexed (fn [index osa]
                                                                                                                                                             (cond
                                                                                                                                                               (= taman-vuoden-otsikon-index index) (paivita-linkkitoimenpiderivi osa (:hankintakustannukset tilat) :kuluva-vuosi)
                                                                                                                                                               (= seuraavan-vuoden-otsikon-index index) (paivita-linkkitoimenpiderivi osa (:hankintakustannukset tilat) :tuleva-vuosi)
                                                                                                                                                               :else osa))
                                                                                                                                                           osat)))
                                                                                                                  :else toimenpiderivi-lapsilla))
                                                                                                              rivit)))
                                                                                      rivi))
                                                                                  rivit))))
        paivita-hallinnollisten-aggregaattirivi (fn [osa tilat ajan-jakso]
                                                  (let [tilat (eduction
                                                                (map val)
                                                                (map ajan-jakso)
                                                                tilat)
                                                        tila (cond
                                                               (every? #(= :valmis %) tilat) :valmis
                                                               (every? #(= :ei-tehty %) tilat) :ei-tehty
                                                               :else :kesken)]
                                                    (p/paivita-arvo osa :arvo
                                                                    (fn [ikoni-ja-teksti]
                                                                      (assoc ikoni-ja-teksti :ikoni (tilan-ikoni tila))))))
        paivita-hallintokustannusten-suunnitelmien-tila (fn [taulukko taulukko-avain]
                                                          (p/paivita-arvo taulukko :lapset
                                                                          (fn [rivit]
                                                                            (mapv (fn [rivi]
                                                                                    (if (p/janan-id? rivi :hallinnollisetkustannukset-vanhempi)
                                                                                      (p/paivita-arvo rivi :lapset
                                                                                                      (fn [rivit]
                                                                                                        (into []
                                                                                                              (cons
                                                                                                                ;; aggregaatti
                                                                                                                (p/paivita-arvo (first rivit) :lapset
                                                                                                                                (fn [osat]
                                                                                                                                  (tyokalut/mapv-indexed (fn [index osa]
                                                                                                                                                           (cond
                                                                                                                                                             (= taman-vuoden-otsikon-index index) (paivita-hallinnollisten-aggregaattirivi osa (:hallinnolliset tilat) :kuluva-vuosi)
                                                                                                                                                             (= seuraavan-vuoden-otsikon-index index) (paivita-hallinnollisten-aggregaattirivi osa (:hallinnolliset tilat) :tuleva-vuosi)
                                                                                                                                                             :else osa))
                                                                                                                                                         osat)))
                                                                                                                ;; loput
                                                                                                                (map (fn [hallinnollinen-toimenpiderivi]
                                                                                                                       (if (= (:halllinto-id hallinnollinen-toimenpiderivi) taulukko-avain)
                                                                                                                         (p/paivita-arvo hallinnollinen-toimenpiderivi :lapset
                                                                                                                                         (fn [osat]
                                                                                                                                           (tyokalut/mapv-indexed (fn [index osa]
                                                                                                                                                                    (cond
                                                                                                                                                                      (= taman-vuoden-otsikon-index index) (p/paivita-arvo osa :arvo
                                                                                                                                                                                                                           (fn [ikoni-ja-teksti]
                                                                                                                                                                                                                             (assoc ikoni-ja-teksti :ikoni (tilan-ikoni (get-in tilat [:hallinnolliset taulukko-avain :kuluva-vuosi])))))
                                                                                                                                                                      (= seuraavan-vuoden-otsikon-index index) (p/paivita-arvo osa :arvo
                                                                                                                                                                                                                               (fn [ikoni-ja-teksti]
                                                                                                                                                                                                                                 (assoc ikoni-ja-teksti :ikoni (tilan-ikoni (get-in tilat [:hallinnolliset taulukko-avain :tuleva-vuosi])))))
                                                                                                                                                                      :else osa))
                                                                                                                                                                  osat)))
                                                                                                                         hallinnollinen-toimenpiderivi))
                                                                                                                     (rest rivit))))))
                                                                                      rivi))
                                                                                  rivit))))
        suunnitelmien-tila-taulukko (reduce (fn [taulukko [toimenpide suunnitelmat]]
                                              (cond-> taulukko
                                                      (or (get suunnitelmat :kokonaishintainen)
                                                          (get suunnitelmat :lisatyo)
                                                          (get suunnitelmat :laskutukseen-perustuen-valinta)) (paivita-hankintakustannusten-suunnitelmien-tila toimenpide :kokonaishintainen-ja-lisatyo)
                                                      (get suunnitelmat :rahavaraukset) (paivita-hankintakustannusten-suunnitelmien-tila toimenpide :akillinen-hoitotyo)
                                                      (get suunnitelmat :rahavaraukset) (paivita-hankintakustannusten-suunnitelmien-tila toimenpide :vahinkojen-korjaukset)
                                                      (get suunnitelmat :rahavaraukset) (paivita-hankintakustannusten-suunnitelmien-tila toimenpide :muut-rahavaraukset)))
                                            suunnitelmien-tila-taulukko (:hankinnat paivitetyt-taulukot-instanssi))]
    (reduce (fn [taulukko [taulukko-avain muuttui?]]
              (if muuttui?
                (paivita-hallintokustannusten-suunnitelmien-tila taulukko taulukko-avain)
                taulukko))
            suunnitelmien-tila-taulukko (:hallinnolliset paivitetyt-taulukot-instanssi))))


(extend-protocol tuck/Event
  TaulukoidenVakioarvot
  (process-event [_ app]
    (-> app
        (assoc-in [:gridit :hoidonjohtopalkkio] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                                 :yhteenveto {:nimi "Hoidonjohtopalkkio"}
                                                 :yhteensa {:nimi "Yhteensä"}
                                                 :kuukausitasolla? false})
        (assoc-in [:gridit :rahavaraukset] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                            :yhteensa {:nimi "Yhteensä"}
                                            :kuukausitasolla? false})
        (assoc-in [:gridit :suunnittellut-hankinnat] {:otsikot {:nimi "Kiinteät" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                                      :yhteensa {:nimi "Yhteensä"}})))
  FiltereidenAloitusarvot
  (process-event [_ app]
    (-> app
        (assoc-in [:suodattimet :hankinnat :toimenpide] :talvihoito)))
  Hoitokausi
  (process-event [_ app]
    (let [{:keys [vuosi pvmt] :as kh} (kuluva-hoitokausi)]
      (-> app
          ;;TODO tämä rivi on wanhaa
          (assoc :kuluva-hoitokausi kh)
          (assoc-in [:domain :hoitokausi] {:hoitokauden-numero vuosi
                                           :pvmt pvmt})
          (assoc-in [:suodattimet :hoitokauden-numero] vuosi))))
  YleisSuodatinArvot
  (process-event [_ {{hoitovuosi :vuosi} :kuluva-hoitokausi :as app}]
    (assoc app :suodatin {:hoitovuosi hoitovuosi :kopioidaan-tuleville-vuosille? true}))
  HaeIndeksitOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :indeksit vastaus))
  HaeIndeksitEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Indeksien haku epäonnistui!" :warning viesti/viestin-nayttoaika-pitka)
    app)
  Oikeudet
  (process-event [_ app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)
          kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-kustannussuunnittelu urakka-id)]
      (assoc app :kirjoitusoikeus? kirjoitusoikeus?)))
  PaivitaToimenpideTaulukko
  (process-event [{:keys [maara-solu polku-taulukkoon]} app]
    (let [kopioidaan-tuleville-vuosille? (get-in app [:hankintakustannukset :valinnat :kopioidaan-tuleville-vuosille?])
          taulukko (get-in app polku-taulukkoon)
          arvo (-> maara-solu (p/arvo :arvo) :value (clj-str/replace #"," "."))
          [polku-container-riviin polku-riviin polku-soluun] (p/osan-polku-taulukossa taulukko maara-solu)
          muokattu-hoitokausi (:hoitokausi (get-in taulukko polku-container-riviin))
          tulevien-vuosien-rivien-indexit (when kopioidaan-tuleville-vuosille?
                                            (keep-indexed (fn [index rivi]
                                                            (when (> (:hoitokausi rivi) muokattu-hoitokausi)
                                                              index))
                                                          (p/arvo taulukko :lapset)))
          yhteensa-otsikon-index (p/otsikon-index taulukko "Yhteensä")
          maara-otsikon-index (p/otsikon-index taulukko "Määrä")
          uusi-taulukko (tyokalut/paivita-asiat-taulukossa taulukko [:laajenna-lapsilla (last polku-riviin)]
                                                           (fn [taulukko taulukon-asioiden-polut]
                                                             (let [[rivit hoitokauden-container laajenna-lapsilla-rivit rivi] taulukon-asioiden-polut
                                                                   hoitokauden-container (get-in taulukko hoitokauden-container)
                                                                   rivi (get-in taulukko rivi)
                                                                   kasiteltavan-rivin-hoitokausi (:hoitokausi hoitokauden-container)
                                                                   arvo-paivitetaan? (or (= kasiteltavan-rivin-hoitokausi muokattu-hoitokausi)
                                                                                         (and kopioidaan-tuleville-vuosille?
                                                                                              (some #(= kasiteltavan-rivin-hoitokausi %)
                                                                                                    tulevien-vuosien-rivien-indexit)))]
                                                               (if arvo-paivitetaan?
                                                                 (p/paivita-arvo rivi :lapset
                                                                                 (fn [osat]
                                                                                   (tyokalut/mapv-indexed (fn [index osa]
                                                                                                            (cond
                                                                                                              (= index yhteensa-otsikon-index) (p/aseta-arvo osa :arvo arvo)
                                                                                                              (= index maara-otsikon-index) (p/paivita-arvo osa :arvo assoc :value arvo)
                                                                                                              :else osa))
                                                                                                          osat)))
                                                                 rivi))))]
      (assoc-in app polku-taulukkoon uusi-taulukko)))

  PaivitaKustannussuunnitelmanYhteenvedot
  (process-event [_ app]
    (update-in app [:hankintakustannukset :yhteenveto]
               (fn [yhteenvedot]
                 (reduce (fn [yhteenvedot hoitokausi]
                           (assoc-in yhteenvedot [(dec hoitokausi) :summa] (yhteensa-yhteenveto hoitokausi app)))
                         yhteenvedot (range 1 6)))))
  HaeKustannussuunnitelma
  (process-event [{:keys [hankintojen-taulukko rahavarausten-taulukko johto-ja-hallintokorvaus-laskulla-taulukko
                          johto-ja-hallintokorvaus-yhteenveto-taulukko erillishankinnat-taulukko toimistokulut-taulukko
                          johtopalkkio-taulukko]} app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)]
      (varmista-kasittelyjen-jarjestys
        (tuck-apurit/post! app :budjettisuunnittelun-indeksit
                           {:urakka-id urakka-id}
                           {:onnistui ->HaeIndeksitOnnistui
                            :epaonnistui ->HaeIndeksitEpaonnistui
                            :paasta-virhe-lapi? true})
        (tuck-apurit/post! app :budjettitavoite
                           {:urakka-id urakka-id}
                           {:onnistui ->HaeTavoiteJaKattohintaOnnistui
                            :epaonnistui ->HaeTavoiteJaKattohintaEpaonnistui
                            :paasta-virhe-lapi? true})
        (tuck-apurit/post! app :budjetoidut-tyot
                           {:urakka-id urakka-id}
                           {:onnistui ->HaeHankintakustannuksetOnnistui
                            :onnistui-parametrit [hankintojen-taulukko rahavarausten-taulukko
                                                  johto-ja-hallintokorvaus-laskulla-taulukko johto-ja-hallintokorvaus-yhteenveto-taulukko
                                                  erillishankinnat-taulukko toimistokulut-taulukko
                                                  johtopalkkio-taulukko]
                            :epaonnistui ->HaeHankintakustannuksetEpaonnistui
                            :paasta-virhe-lapi? true}))
      app))
  HaeTavoiteJaKattohintaOnnistui
  (process-event [{vastaus :vastaus} app]
    (log "HAE TAVOITE JA KATTOHINTA ONNISTUI")
    (let [tavoite-ja-kattohintapohjadata (mapv (fn [hoitokausi]
                                                 {:hoitokausi hoitokausi})
                                               (range 1 6))
          tavoite-ja-kattohinnat (tyokalut/generoi-pohjadata vastaus
                                                             {:tavoitehinta 0
                                                              :kattohinta 0}
                                                             tavoite-ja-kattohintapohjadata)]
      (assoc app :tavoite-ja-kattohinta {:tavoitehinnat (mapv (fn [{:keys [tavoitehinta hoitokausi]}]
                                                                {:summa tavoitehinta
                                                                 :hoitokausi hoitokausi})
                                                              tavoite-ja-kattohinnat)
                                         :kattohinnat (mapv (fn [{:keys [kattohinta hoitokausi]}]
                                                              {:summa kattohinta
                                                               :hoitokausi hoitokausi})
                                                            tavoite-ja-kattohinnat)})))
  HaeTavoiteJaKattohintaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;;TODO
    (viesti/nayta! "Tavoite- ja kattohinnan haku epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
  HaeHankintakustannuksetOnnistui
  (process-event [{:keys [vastaus hankintojen-taulukko rahavarausten-taulukko johto-ja-hallintokorvaus-laskulla-taulukko
                          johto-ja-hallintokorvaus-yhteenveto-taulukko erillishankinnat-taulukko toimistokulut-taulukko
                          johtopalkkio-taulukko]}
                  {{valinnat :valinnat} :hankintakustannukset
                   {kuluva-hoitovuosi :vuosi kuluvan-hoitovuoden-pvmt :pvmt} :kuluva-hoitokausi
                   kirjoitusoikeus? :kirjoitusoikeus? :as app}]
    (log "HAE HANKINTAKUSTANNUKSET ONNISTUI")
    (let [{urakan-aloituspvm :alkupvm urakan-lopetuspvm :loppupvm urakka-id :id} (-> @tiedot/tila :yleiset :urakka)
          pohjadata (urakan-ajat)
          pohjadatan-taydennys-toimenpiteittain (fn [data toimenpiteet rikastamis-fn]
                                                  (reduce (fn [data-toimenpiteittain toimenpide]
                                                            (let [sort-fn (juxt :vuosi :kuukausi)
                                                                  data-backilta (vec (sort-by sort-fn (filter #(= (:toimenpide-avain %) toimenpide) data)))
                                                                  data (loop [[pd & pd-loput] (sort-by sort-fn pohjadata)
                                                                              muodostettu []
                                                                              i 0]
                                                                         (if (nil? pd)
                                                                           muodostettu
                                                                           (let [tarkasteltava-data (get data-backilta i)
                                                                                 loydetty-data? (and (= (:vuosi pd) (:vuosi tarkasteltava-data))
                                                                                                         (= (:kuukausi pd) (:kuukausi tarkasteltava-data)))]
                                                                             (recur pd-loput
                                                                                    (conj muodostettu
                                                                                          (if loydetty-data?
                                                                                            tarkasteltava-data
                                                                                            pd))
                                                                                    (if loydetty-data?
                                                                                      (inc i)
                                                                                      i)))))
                                                                  data (map rikastamis-fn data)]
                                                              (merge data-toimenpiteittain
                                                                     {toimenpide data})))
                                                          {}
                                                          toimenpiteet))
          hankinnat (:kiinteahintaiset-tyot vastaus)
          hankinnat-laskutukseen-perustuen (filter #(and (= (:tyyppi %) "laskutettava-tyo")
                                                         (nil? (:haettu-asia %)))
                                                   (:kustannusarvioidut-tyot vastaus))
          rahavaraukset (distinct (keep #(when (#{:rahavaraus-lupaukseen-1 :kolmansien-osapuolten-aiheuttamat-vahingot :akilliset-hoitotyot} (:haettu-asia %))
                                           (select-keys % #{:tyyppi :summa :toimenpide-avain :vuosi :kuukausi}))
                                        (:kustannusarvioidut-tyot vastaus)))
          hankinnat-toimenpiteittain (pohjadatan-taydennys-toimenpiteittain hankinnat
                                                                            toimenpiteet
                                                                            (fn [{:keys [vuosi kuukausi summa] :as data}]
                                                                              (-> data
                                                                                  (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                                         :maara summa)
                                                                                  (dissoc :summa))))
          hankinnat-hoitokausille (into {}
                                        (map (fn [[toimenpide hankinnat]]
                                               [toimenpide (vec (vals (sort-by #(-> % key first)
                                                                               (fn [aika-1 aika-2]
                                                                                 (pvm/ennen? aika-1 aika-2))
                                                                               (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                                         hankinnat))))])
                                             hankinnat-toimenpiteittain))
          hankinnat-laskutukseen-perustuen-toimenpiteittain (pohjadatan-taydennys-toimenpiteittain hankinnat-laskutukseen-perustuen
                                                                                                   toimenpiteet
                                                                                                   (fn [{:keys [vuosi kuukausi summa] :as data}]
                                                                                                     (-> data
                                                                                                         (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                                                                :maara summa)
                                                                                                         (dissoc :summa))))
          rahavaraukset-toimenpiteittain (apply merge-with
                                                (fn [a b]
                                                  (concat a b))
                                                (map (fn [tyyppi]
                                                       (let [tyypin-toimenpiteet (if (#{"vahinkojen-korjaukset" "akillinen-hoitotyo"} tyyppi)
                                                                                   #{:talvihoito
                                                                                     :liikenneympariston-hoito
                                                                                     :sorateiden-hoito}
                                                                                   #{:mhu-yllapito})
                                                             rahavaraukset-tyypille (filter #(= tyyppi (:tyyppi %)) rahavaraukset)]
                                                         (pohjadatan-taydennys-toimenpiteittain rahavaraukset-tyypille
                                                                                                tyypin-toimenpiteet
                                                                                                (fn [{:keys [vuosi kuukausi summa] :as data}]
                                                                                                  (-> data
                                                                                                      (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                                                             :maara summa
                                                                                                             :tyyppi tyyppi)
                                                                                                      (dissoc :summa))))))
                                                     (keys rahavaraukset-jarjestys)))
          ;{:talvihoito {"vahinkojen-korjaukset" [[{:maara 3} {:maara 2} ...] [{:maara 3} {:maara 2} ...]]
          ;              "akillinen-hoitotyo" [{:maara 1}]}
          rahavaraukset-hoitokausille (into {}
                                            (map (fn [[toimenpide rahavaraukset]]
                                                   [toimenpide
                                                    (into {}
                                                          (map (fn [[tyyppi rahavaraukset]]
                                                                 [tyyppi (vec (vals (sort-by #(-> % key first)
                                                                                             (fn [aika-1 aika-2]
                                                                                               (pvm/ennen? aika-1 aika-2))
                                                                                             (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                                                       rahavaraukset))))])
                                                               (group-by :tyyppi
                                                                         rahavaraukset)))])
                                                 rahavaraukset-toimenpiteittain))
          hoidon-johto-kustannukset (filter #(= (:toimenpide-avain %) :mhu-johto)
                                            (:kustannusarvioidut-tyot vastaus))
          hoidonjohtopalkkio-kuukausitasolla? (not (apply = (map :maara hoidon-johto-kustannukset)))
          app (-> app
                  (assoc-in [:domain :suunnittellut-hankinnat] hankinnat-hoitokausille)
                  (assoc-in [:domain :rahavaraukset] rahavaraukset-hoitokausille)
                  (assoc-in [:domain :hoidonjohtopalkkio] (vec
                                                            (sort-by #(-> % first :aika)
                                                                     (vals
                                                                       (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                                 (transduce (comp (filter (fn [{haettu-asia :haettu-asia}]
                                                                                                            (= haettu-asia :hoidonjohtopalkkio)))
                                                                                                  (map (fn [{:keys [summa kuukausi vuosi]}]
                                                                                                         {:maara summa
                                                                                                          :kuukausi kuukausi
                                                                                                          :vuosi vuosi
                                                                                                          :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)})))
                                                                                            conj
                                                                                            []
                                                                                            hoidon-johto-kustannukset))))))
                  (assoc-in [:gridit :hoidonjohtopalkkio :kuukausitasolla?] hoidonjohtopalkkio-kuukausitasolla?)
                  (assoc :kantahaku-valmis? true))]
      #_(tarkista-datan-validius! hankinnat hankinnat-laskutukseen-perustuen)
      ;(async/put! tapahtumat {:ajax :tuck :tapahtuma :hankintakustannukset-haettu})
      app)
    #_(println-c)
    #_(time (let [hankintojen-pohjadata (urakan-ajat)
                {urakan-aloituspvm :alkupvm urakan-lopetuspvm :loppupvm urakka-id :id} (-> @tiedot/tila :yleiset :urakka)
                hankintojen-taydennys-fn (fn [hankinnat kustannusarvoitu?]
                                           (sequence
                                             (comp
                                               (mapcat (fn [toimenpide]
                                                         (tyokalut/generoi-pohjadata (if kustannusarvoitu?
                                                                                       (eduction
                                                                                         (filter #(= (:toimenpide-avain %) toimenpide))
                                                                                         (map #(assoc % :toimenpide (get toimenpiteiden-avaimet toimenpide)))
                                                                                         hankinnat)
                                                                                       (filter #(= (:toimenpide %) (get toimenpiteiden-avaimet toimenpide))
                                                                                               hankinnat))
                                                                                     {:summa ""
                                                                                      :toimenpide (get toimenpiteiden-avaimet toimenpide)}
                                                                                     hankintojen-pohjadata)))
                                               (map (fn [{:keys [vuosi kuukausi] :as data}]
                                                      (assoc data :pvm (pvm/luo-pvm vuosi (dec kuukausi) 15)))))
                                             toimenpiteet))
                maara-kk-taulukon-data (fn [kustannusarvioidut-tyot haettu-asia asia-nimi-frontilla]
                                         (let [asia-kannasta (filter (fn [tyo]
                                                                       (= haettu-asia (:haettu-asia tyo)))
                                                                     kustannusarvioidut-tyot)
                                               asia-kannasta (vec
                                                               (reduce (fn [palkkiot hoitokauden-aloitus-vuosi]
                                                                         (let [vuoden-maara (some (fn [{:keys [vuosi summa kuukausi]}]
                                                                                                    (when (and (= vuosi hoitokauden-aloitus-vuosi)
                                                                                                               (> kuukausi 9))
                                                                                                      summa))
                                                                                                  asia-kannasta)
                                                                               maara-kk (or vuoden-maara 0)]
                                                                           (conj palkkiot {:maara-kk maara-kk
                                                                                           :nimi asia-nimi-frontilla
                                                                                           :yhteensa (* 12 maara-kk)})))
                                                                       []
                                                                       (range (pvm/vuosi urakan-aloituspvm)
                                                                              (pvm/vuosi (last kuluvan-hoitovuoden-pvmt)))))]
                                           [[(last asia-kannasta)] asia-kannasta]))
                hankinnat (:kiinteahintaiset-tyot vastaus)
                hankinnat-laskutukseen-perustuen (filter #(and (= (:tyyppi %) "laskutettava-tyo")
                                                               (nil? (:haettu-asia %)))
                                                         (:kustannusarvioidut-tyot vastaus))
                hankinnat-hoitokausille (hankintojen-taydennys-fn hankinnat false)
                hankinnat-laskutukseen-perustuen-hoitokausille (hankintojen-taydennys-fn hankinnat-laskutukseen-perustuen true)
                laskutukseen-perustuvat-toimenpiteet (reduce (fn [toimenpide-avaimet toimenpide]
                                                               (conj toimenpide-avaimet
                                                                     (some #(when (= (second %) toimenpide)
                                                                              (first %))
                                                                           toimenpiteiden-avaimet)))
                                                             #{} (distinct
                                                                   (eduction
                                                                     (remove #(or (= (:summa %) 0)
                                                                                  (= (:summa %) "")))
                                                                     (map :toimenpide)
                                                                     hankinnat-laskutukseen-perustuen-hoitokausille)))
                hankinnat-toimenpiteittain (group-by :toimenpide hankinnat-hoitokausille)
                hankinnat-laskutukseen-perustuen-toimenpiteittain (group-by :toimenpide hankinnat-laskutukseen-perustuen-hoitokausille)
                hankinnat-hoitokausittain (group-by #(pvm/paivamaaran-hoitokausi (:pvm %))
                                                    (concat hankinnat-laskutukseen-perustuen-hoitokausille hankinnat-hoitokausille))
                rahavarausten-taydennys-fn (fn [rahavaraukset]
                                             (mapcat (fn [toimenpide]
                                                       (tyokalut/generoi-pohjadata (eduction
                                                                                     (filter #(= (:toimenpide-avain %) toimenpide))
                                                                                     (map #(assoc % :toimenpide (get toimenpiteiden-avaimet toimenpide)))
                                                                                     rahavaraukset)
                                                                                   {:summa ""
                                                                                    :toimenpide (get toimenpiteiden-avaimet toimenpide)}
                                                                                   (if (= :mhu-yllapito toimenpide)
                                                                                     [{:tyyppi "muut-rahavaraukset"}]
                                                                                     [{:tyyppi "vahinkojen-korjaukset"}
                                                                                      {:tyyppi "akillinen-hoitotyo"}])))
                                                     toimenpiteet-rahavarauksilla))
                ;; Kantaan ollaan tallennettu kk-tasolla, koska integroituvat järjestelmät näin haluaa. Kumminkin frontilla
                ;; näytetään vain yksi rivi, joka on sama jokaiselle kk ja vuodelle
                rahavaraukset (distinct (keep #(when (#{:rahavaraus-lupaukseen-1 :kolmansien-osapuolten-aiheuttamat-vahingot :akilliset-hoitotyot} (:haettu-asia %))
                                                 (select-keys % #{:tyyppi :summa :toimenpide-avain}))
                                              (:kustannusarvioidut-tyot vastaus)))
                rahavaraukset-hoitokausile (rahavarausten-taydennys-fn rahavaraukset)
                rahavarauket-toimenpiteittain (group-by :toimenpide rahavaraukset-hoitokausile)

                johto-ja-hallintokorvaukset (:johto-ja-hallintokorvaukset vastaus)
                jh-toimenkuva-jarjestys {"sopimusvastaava" 0
                                         "vastuunalainen työnjohtaja" 1
                                         "päätoiminen apulainen" 2
                                         "apulainen/työnjohtaja" 3
                                         "viherhoidosta vastaava henkilö" 4
                                         "hankintavastaava" 5
                                         "harjoittelija" 6}
                jh-maksukausi-jarjestys {:molemmat 0
                                         :kesa 1
                                         :talvi 2}
                jh-jarjestys (fn [{:keys [toimenkuva maksukausi hoitokaudet]}]
                               [(get jh-toimenkuva-jarjestys toimenkuva)
                                (get jh-maksukausi-jarjestys maksukausi)
                                (apply min hoitokaudet)])
                hoitokauden-arvot (fn [jhk-hoitokaudet f]
                                    (reduce (fn [arvot-hoitokausittain hoitokausi]
                                              (let [hoitokauden-arvot (some #(when (or (= (:hoitokausi %) hoitokausi)
                                                                                       (and (= hoitokausi 1)
                                                                                            (= (:hoitokausi %) 0)))
                                                                               %)
                                                                            jhk-hoitokaudet)]
                                                (conj arvot-hoitokausittain
                                                      (if hoitokauden-arvot
                                                        (f hoitokauden-arvot)
                                                        0))))
                                            [] (range 1 6)))
                jh-korvaukset (if (empty? johto-ja-hallintokorvaukset)
                                [{:toimenkuva "sopimusvastaava" :kk-v 12 :maksukausi :molemmat
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "vastuunalainen työnjohtaja" :kk-v 12 :maksukausi :molemmat
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "päätoiminen apulainen" :kk-v 7 :maksukausi :talvi
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "päätoiminen apulainen" :kk-v 5 :maksukausi :kesa
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "apulainen/työnjohtaja" :kk-v 7 :maksukausi :talvi
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "apulainen/työnjohtaja" :kk-v 5 :maksukausi :kesa
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "viherhoidosta vastaava henkilö" :kk-v 5 :maksukausi :molemmat
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "hankintavastaava" :kk-v 4.5 :maksukausi :molemmat
                                  :tunnit-kk [0] :tuntipalkka [0]
                                  :yhteensa-kk [0] :hoitokaudet #{0}}
                                 {:toimenkuva "hankintavastaava" :kk-v 12 :maksukausi :molemmat
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}
                                 {:toimenkuva "harjoittelija" :kk-v 4 :maksukausi :molemmat
                                  :tunnit-kk (into [] (repeat 5 0)) :tuntipalkka (into [] (repeat 5 0))
                                  :yhteensa-kk (into [] (repeat 5 0)) :hoitokaudet (into #{} (range 1 6))}]
                                (into []
                                      (sort-by jh-jarjestys
                                               (map (fn [[jhk jhk-hoitokaudet]]
                                                      (let []
                                                        (assoc jhk :yhteensa-kk (hoitokauden-arvot jhk-hoitokaudet (fn [{:keys [tunnit tuntipalkka]}]
                                                                                                                     (* tunnit tuntipalkka)))
                                                               :hoitokaudet (into #{} (map :hoitokausi jhk-hoitokaudet))
                                                               :tunnit-kk (hoitokauden-arvot jhk-hoitokaudet :tunnit)
                                                               :tuntipalkka (hoitokauden-arvot jhk-hoitokaudet :tuntipalkka))))
                                                    (group-by (fn [m]
                                                                (select-keys m #{:toimenkuva :kk-v :maksukausi}))
                                                              johto-ja-hallintokorvaukset)))))
                ;; TODO tähän parempi ratkaisu
                _ (when (empty? johto-ja-hallintokorvaukset)
                    (doseq [{:keys [toimenkuva kk-v maksukausi tunnit-kk tuntipalkka hoitokaudet]} jh-korvaukset]
                      (let [lahetettava-data {:urakka-id urakka-id
                                              :toimenkuva toimenkuva
                                              :maksukausi maksukausi
                                              :jhkt (mapv (fn [hoitokausi]
                                                            {:hoitokausi hoitokausi
                                                             :tunnit 0
                                                             :tuntipalkka 0
                                                             :kk-v kk-v})
                                                          (sort hoitokaudet))}]
                        (tuck-apurit/post! app :tallenna-johto-ja-hallintokorvaukset
                                           lahetettava-data
                                           {:onnistui ->TallennaJohtoJaHallintokorvauksetOnnistui
                                            :epaonnistui ->TallennaJohtoJaHallintokorvauksetEpaonnistui
                                            :paasta-virhe-lapi? true}))))

                hoidon-johto-kustannukset (filter #(= (:toimenpide-avain %) :mhu-johto)
                                                  (:kustannusarvioidut-tyot vastaus))

                [erillishankinnat erillishankinnat-kannasta] (maara-kk-taulukon-data hoidon-johto-kustannukset :erillishankinnat "Erillishankinnat")
                [jh-toimistokulut jh-toimistokulut-kannasta] (maara-kk-taulukon-data hoidon-johto-kustannukset :toimistokulut "Toimistokulut")
                [johtopalkkio johtopalkkio-kannasta] (maara-kk-taulukon-data hoidon-johto-kustannukset :hoidonjohtopalkkio "Hoidonjohtopalkkio")

                valinnat (assoc valinnat :laskutukseen-perustuen laskutukseen-perustuvat-toimenpiteet)
                ;; 26
                _ (println-c)

                app (-> app
                        println-c->
                        (assoc-in [:hankintakustannukset :valinnat :laskutukseen-perustuen] laskutukseen-perustuvat-toimenpiteet)
                        println-c->
                        (assoc-in [:hankintakustannukset :yhteenveto] (into []
                                                                            (map-indexed (fn [index [_ tiedot]]
                                                                                           {:hoitokausi (inc index)
                                                                                            :summa (+
                                                                                                     ;; Hankintakustannukset
                                                                                                     (apply + (map #(if (number? (:summa %))
                                                                                                                      (:summa %) 0)
                                                                                                                   tiedot))
                                                                                                     ;; Rahavaraukset
                                                                                                     (* 12 (apply + (map :summa rahavaraukset))))})
                                                                                         hankinnat-hoitokausittain)))
                        ;29
                        println-c->
                        (assoc-in [:hankintakustannukset :toimenpiteet]
                                  (into {}
                                        (map (fn [[toimenpide-avain toimenpide-nimi]]
                                               [toimenpide-avain (hankintojen-taulukko (get hankinnat-toimenpiteittain toimenpide-nimi) valinnat toimenpide-avain kirjoitusoikeus? false)])
                                             toimenpiteiden-avaimet)))
                        ;30
                        println-c->
                        (assoc-in [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen]
                                  (into {}
                                        (map (fn [[toimenpide-avain toimenpide-nimi]]
                                               [toimenpide-avain (hankintojen-taulukko (get hankinnat-laskutukseen-perustuen-toimenpiteittain toimenpide-nimi) valinnat toimenpide-avain kirjoitusoikeus? true)])
                                             toimenpiteiden-avaimet)))
                        println-c->
                        (assoc-in [:hankintakustannukset :rahavaraukset]
                                  (into {}
                                        (keep (fn [[toimenpide-avain toimenpide-nimi]]
                                                (when (toimenpiteet-rahavarauksilla toimenpide-avain)
                                                  [toimenpide-avain (rahavarausten-taulukko (get rahavarauket-toimenpiteittain toimenpide-nimi) valinnat toimenpide-avain kirjoitusoikeus?)]))
                                              toimenpiteiden-avaimet)))
                        println-c->
                        (assoc-in [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-laskulla] (johto-ja-hallintokorvaus-laskulla-taulukko jh-korvaukset kirjoitusoikeus?))
                        println-c->
                        (assoc-in [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-yhteenveto] (johto-ja-hallintokorvaus-yhteenveto-taulukko jh-korvaukset))
                        println-c->
                        (assoc-in [:hallinnolliset-toimenpiteet :erillishankinnat] (erillishankinnat-taulukko (first erillishankinnat) :erillishankinnat kirjoitusoikeus?))
                        println-c->
                        (assoc-in [:hallinnolliset-toimenpiteet :toimistokulut] (toimistokulut-taulukko (first jh-toimistokulut) :toimistokulut kirjoitusoikeus?))
                        println-c->
                        (assoc-in [:hallinnolliset-toimenpiteet :johtopalkkio] (johtopalkkio-taulukko (first johtopalkkio) :hoidonjohtopalkkio kirjoitusoikeus?))
                        println-c->
                        ;; Edellisten vuosien data, jota ei voi muokata
                        (assoc-in [:hallinnolliset-toimenpiteet :menneet-vuodet :erillishankinnat] (into [] (butlast erillishankinnat-kannasta)))
                        println-c->
                        (assoc-in [:hallinnolliset-toimenpiteet :menneet-vuodet :toimistokulut] (into [] (butlast jh-toimistokulut-kannasta)))
                        println-c->
                        (assoc-in [:hallinnolliset-toimenpiteet :menneet-vuodet :johtopalkkio] (into [] (butlast johtopalkkio-kannasta)))
                        println-c->)
                _ (println-c)]
            (tarkista-datan-validius! hankinnat hankinnat-laskutukseen-perustuen)
            ;; 42
            (println-c)
            (-> app
                (update-in [:hankintakustannukset :toimenpiteet]
                           (fn [toimenpiteet]
                             (into {}
                                   (map (fn [[toimenpide-avain toimenpide]]
                                          [toimenpide-avain (paivita-hankinta-summat-automaattisesti toimenpide
                                                                                                     [:hankintakustannukset :toimenpiteet toimenpide-avain]
                                                                                                     app)])
                                        toimenpiteet))))
                println-c->
                (update-in [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen]
                           (fn [toimenpiteet]
                             (into {}
                                   (map (fn [[toimenpide-avain toimenpide]]
                                          [toimenpide-avain (paivita-hankinta-summat-automaattisesti toimenpide
                                                                                                     [:hankintakustannukset :toimenpiteet-laskutukseen-perustuen toimenpide-avain]
                                                                                                     app)])
                                        toimenpiteet))))
                println-c->
                (update-in [:hankintakustannukset :rahavaraukset]
                           (fn [rahavaraukset-toimenpiteittain]
                             (into {}
                                   (map (fn [[toimenpide-avain rahavaraus]]
                                          [toimenpide-avain (paivita-rahavaraus-summat-automaattisesti rahavaraus
                                                                                                       [:hankintakustannukset :rahavaraukset toimenpide-avain]
                                                                                                       app)])
                                        rahavaraukset-toimenpiteittain))))
                println-c->
                (update-in [:hallinnolliset-toimenpiteet :erillishankinnat]
                           (fn [erillishankinnat]
                             (paivita-maara-kk-taulukon-summat-automaattisesti erillishankinnat
                                                                               [:hallinnolliset-toimenpiteet :erillishankinnat]
                                                                               app)))
                println-c->
                (update-in [:hallinnolliset-toimenpiteet :toimistokulut]
                           (fn [toimistokulut]
                             (paivita-maara-kk-taulukon-summat-automaattisesti toimistokulut
                                                                               [:hallinnolliset-toimenpiteet :toimistokulut]
                                                                               app)))
                println-c->
                (update-in [:hallinnolliset-toimenpiteet :johtopalkkio]
                           (fn [johtopalkkio]
                             (paivita-maara-kk-taulukon-summat-automaattisesti johtopalkkio
                                                                               [:hallinnolliset-toimenpiteet :johtopalkkio]
                                                                               app)))
                println-c->


                (assoc-in [:domain :hoidonjohtopalkkio] (vec
                                                          (sort-by #(-> % first :aika)
                                                                   (vals
                                                                     (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                               (transduce (comp (filter (fn [{haettu-asia :haettu-asia}]
                                                                                                          (= haettu-asia :hoidonjohtopalkkio)))
                                                                                                (map (fn [{:keys [summa kuukausi vuosi]}]
                                                                                                       {:maara summa
                                                                                                        :kuukausi kuukausi
                                                                                                        :vuosi vuosi
                                                                                                        :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)})))
                                                                                          conj
                                                                                          []
                                                                                          hoidon-johto-kustannukset))))))
                println-c->
                (assoc :kantahaku-valmis? true)
                println-c->
                println-c-nollaus))))
  HaeHankintakustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;; TODO
    (viesti/nayta! "Hankintakustannusten haku epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
  LaajennaSoluaKlikattu
  (process-event [{:keys [polku-taulukkoon rivin-id auki?]} app]
    (let [rivin-container (tyokalut/rivin-vanhempi (get-in app (conj polku-taulukkoon :rivit))
                                                   rivin-id)
          toggle-fn (if auki? disj conj)
          uusi-taulukko (update (get-in app polku-taulukkoon) :rivit
                                (fn [rivit]
                                  (mapv (fn [rivi]
                                          (if (p/janan-id? rivi (p/janan-id rivin-container))
                                            (update rivi :janat (fn [[paa & lapset]]
                                                                  (into []
                                                                        (cons paa
                                                                              (map #(update % ::piillotettu toggle-fn :laajenna-kiinni) lapset)))))
                                            rivi))
                                        rivit)))]
      (assoc-in app polku-taulukkoon uusi-taulukko)))
  YhteenvetoLaajennaSoluaKlikattu
  (process-event [{:keys [polku-taulukkoon rivin-id auki?]} app]
    (let [taulukko (get-in app polku-taulukkoon)
          toggle-fn (if auki? disj conj)
          uusi-taulukko (tyokalut/paivita-asiat-taulukossa taulukko [:hankintakustannukset :toimenpide]
                                                           (fn [taulukko polut]
                                                             (let [[_ _ _ toimenpide-sisalto] polut
                                                                   toimenpide-sisalto (get-in taulukko toimenpide-sisalto)
                                                                   laajenna-toimenpide (first (p/arvo toimenpide-sisalto :lapset))]
                                                               (if (= (p/janan-id laajenna-toimenpide) rivin-id)
                                                                 (p/paivita-arvo toimenpide-sisalto :lapset
                                                                                 (fn [rivit]
                                                                                   (tyokalut/mapv-range 1 (fn [rivi]
                                                                                                            (p/paivita-arvo rivi :class toggle-fn "piillotettu"))
                                                                                                        rivit)))
                                                                 toimenpide-sisalto))))]
      (assoc-in app polku-taulukkoon uusi-taulukko)))
  MuutaTaulukonOsa
  (process-event [{:keys [osa arvo polku-taulukkoon]} app]
    (let [taulukko (get-in app polku-taulukkoon)
          paivitetty-osa (p/aseta-arvo osa :arvo arvo)
          solun-polku (into [] (apply concat (p/osan-polku-taulukossa taulukko paivitetty-osa)))
          uusi-taulukko (assoc-in taulukko solun-polku paivitetty-osa)]
      (assoc-in app polku-taulukkoon uusi-taulukko)))
  MuutaTaulukonOsanSisarta
  (process-event [{:keys [osa sisaren-tunniste polku-taulukkoon arvo]} app]
    (let [taulukko (get-in app polku-taulukkoon)
          sisar-osa (tyokalut/osan-sisar taulukko osa sisaren-tunniste)
          paivitetty-sisar-osa (p/aseta-arvo sisar-osa :arvo arvo)
          solun-polku (into [] (apply concat (p/osan-polku-taulukossa taulukko paivitetty-sisar-osa)))
          uusi-taulukko (assoc-in taulukko solun-polku paivitetty-sisar-osa)]
      (assoc-in app polku-taulukkoon uusi-taulukko)))
  PaivitaTaulukonOsa
  (process-event [{:keys [osa polku-taulukkoon paivitys-fn]} app]
    (let [taulukko (get-in app polku-taulukkoon)
          paivitetty-osa (p/paivita-arvo osa :arvo paivitys-fn)
          solun-polku (into [] (apply concat (p/osan-polku-taulukossa taulukko paivitetty-osa)))
          uusi-taulukko (assoc-in taulukko solun-polku paivitetty-osa)]
      (assoc-in app polku-taulukkoon uusi-taulukko)))
  TaytaAlas
  (process-event [{:keys [maara-solu polku-taulukkoon]} app]
    (let [kopioidaan-tuleville-vuosille? (get-in app [:hankintakustannukset :valinnat :kopioidaan-tuleville-vuosille?])
          maksetaan (get-in app [:hankintakustannukset :valinnat :maksetaan])
          kauden-viimeinen-rivi-index (case maksetaan
                                        :kesakausi 12
                                        :talvikausi 7
                                        :molemmat 12)
          taulukko (get-in app polku-taulukkoon)
          [polku-container-riviin polku-riviin polku-soluun] (p/osan-polku-taulukossa taulukko maara-solu)
          muokattu-hoitokausi (:hoitokausi (get-in taulukko polku-container-riviin))
          tulevien-vuosien-rivien-indexit (when kopioidaan-tuleville-vuosille?
                                            (keep-indexed (fn [index rivi]
                                                            (when (> (:hoitokausi rivi) muokattu-hoitokausi)
                                                              index))
                                                          (p/arvo taulukko :lapset)))
          paivitettavien-yhteenvetojen-hoitokaudet (if kopioidaan-tuleville-vuosille?
                                                     (keep (fn [rivi]
                                                             (when (>= (:hoitokausi rivi) muokattu-hoitokausi)
                                                               (:hoitokausi rivi)))
                                                           (p/arvo taulukko :lapset))
                                                     [muokattu-hoitokausi])
          tayta-rivista-eteenpain (first (keep-indexed (fn [index rivi]
                                                         (when (p/osan-polku rivi maara-solu)
                                                           index))
                                                       (p/arvo (get-in taulukko polku-container-riviin) :lapset)))
          value (:value (p/arvo maara-solu :arvo))
          maara-otsikon-index (p/otsikon-index taulukko "Määrä")
          yhteensa-otsikon-index (p/otsikon-index taulukko "Yhteensä")
          uusi-taulukko (tyokalut/paivita-asiat-taulukossa taulukko [:laajenna-lapsilla]
                                                           (fn [taulukko taulukon-asioiden-polut]
                                                             (let [[rivit hoitokauden-container] taulukon-asioiden-polut
                                                                   hoitokauden-container (get-in taulukko hoitokauden-container)
                                                                   kasiteltavan-rivin-hoitokausi (:hoitokausi hoitokauden-container)
                                                                   arvo-paivitetaan? (or (= kasiteltavan-rivin-hoitokausi muokattu-hoitokausi)
                                                                                         (and kopioidaan-tuleville-vuosille?
                                                                                              (some #(= kasiteltavan-rivin-hoitokausi %)
                                                                                                    tulevien-vuosien-rivien-indexit)))]
                                                               (if arvo-paivitetaan?
                                                                 (p/paivita-arvo hoitokauden-container :lapset
                                                                                 (fn [rivit]
                                                                                   (tyokalut/mapv-range tayta-rivista-eteenpain
                                                                                                        (inc kauden-viimeinen-rivi-index)
                                                                                                        (fn [maara-rivi]
                                                                                                          (p/paivita-arvo maara-rivi
                                                                                                                          :lapset
                                                                                                                          (fn [osat]
                                                                                                                            (tyokalut/mapv-indexed
                                                                                                                              (fn [index osa]
                                                                                                                                (cond
                                                                                                                                  (= index maara-otsikon-index) (p/paivita-arvo osa :arvo assoc :value value)
                                                                                                                                  (= index yhteensa-otsikon-index) (p/aseta-arvo osa :arvo (clj-str/replace value #"," "."))
                                                                                                                                  :else osa))
                                                                                                                              osat))))
                                                                                                        rivit)))
                                                                 hoitokauden-container))))]
      (-> app
          (assoc-in polku-taulukkoon uusi-taulukko)
          (update-in [:hankintakustannukset :yhteenveto]
                     (fn [yhteenvedot]
                       (reduce (fn [yhteenvedot hoitokausi]
                                 (assoc-in yhteenvedot [(dec hoitokausi) :summa] (yhteensa-yhteenveto hoitokausi app)))
                               yhteenvedot paivitettavien-yhteenvetojen-hoitokaudet))))))
  ToggleHankintakustannuksetOtsikko
  (process-event [{:keys [kylla?]} app]
    (let [toimenpide-avain (get-in app [:suodattimet :hankinnat :toimenpide])
          polku [:hankintakustannukset :toimenpiteet toimenpide-avain]
          taulukko (get-in app polku)
          uusi-taulukko (tyokalut/paivita-asiat-taulukossa taulukko [0 "Nimi"]
                                                           (fn [taulukko taulukon-asioiden-polut]
                                                             (let [[rivit rivi osat osa] taulukon-asioiden-polut
                                                                   osa (get-in taulukko osa)]
                                                               (p/aseta-arvo osa :arvo
                                                                             (if kylla?
                                                                               "Kiinteät" " ")))))]
      (assoc-in app polku uusi-taulukko)))
  PaivitaJHRivit
  (process-event [{:keys [paivitetty-osa]}
                  {{kuluva-hoitovuosi :vuosi kuluvan-hoitovuoden-pvmt :pvmt} :kuluva-hoitokausi :as app}]
    ;; Nämä arvothan voisi päivittää automaattisesti Taulukon TilanSeuranta protokollan avulla, mutta se aiheuttaisi
    ;; saman agregoinnin useaan kertaan. Lasketaan tässä kerralla kaikki tarvittava.
    (let [laskulla-taulukon-polku [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-laskulla]
          yhteenveto-taulukon-polku [:hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus-yhteenveto]
          laskulla-taulukko (get-in app laskulla-taulukon-polku)
          yhteenveto-taulukko (get-in app yhteenveto-taulukon-polku)
          tunnit-sarakkeen-index (p/otsikon-index laskulla-taulukko "Tunnit/kk")
          tuntipalkka-sarakkeen-index (p/otsikon-index laskulla-taulukko "Tuntipalkka")
          kk-v-sarakkeen-index (p/otsikon-index laskulla-taulukko "kk/v")
          [rivin-polku _] (p/osan-polku-taulukossa laskulla-taulukko paivitetty-osa)
          laskulla-taulukko (tyokalut/paivita-asiat-taulukossa laskulla-taulukko [(last rivin-polku) "Yhteensä/kk"]
                                                               (fn [taulukko polut]
                                                                 (let [[rivit rivi osat osa] polut
                                                                       osat (get-in taulukko osat)
                                                                       yhteensaosa (get-in taulukko osa)
                                                                       tunnit (p/arvo (nth osat tunnit-sarakkeen-index) :arvo)
                                                                       tunnit (if (number? tunnit) tunnit 0)
                                                                       tuntipalkka (p/arvo (nth osat tuntipalkka-sarakkeen-index) :arvo)
                                                                       tuntipalkka (if (number? tuntipalkka) tuntipalkka 0)]
                                                                   (p/aseta-arvo yhteensaosa :arvo (* tunnit tuntipalkka)))))
          indeksikorjatun-rivin-index (dec (count (p/arvo yhteenveto-taulukko :lapset)))
          summa-rivin-index (dec indeksikorjatun-rivin-index)

          yhteenveto-taulukko (-> yhteenveto-taulukko
                                  (tyokalut/paivita-asiat-taulukossa [(last rivin-polku)]
                                                                     (fn [taulukko polut]
                                                                       (let [[rivit rivi] polut
                                                                             rivi (get-in taulukko rivi)
                                                                             rivin-vuodet (:vuodet rivi)
                                                                             [aloitus-vuosi lopetus-vuosi] [(apply min rivin-vuodet) (apply max rivin-vuodet)]
                                                                             [aloitus-vuosi lopetus-vuosi] [(cond
                                                                                                              (= aloitus-vuosi 0) 1
                                                                                                              (< aloitus-vuosi kuluva-hoitovuosi) kuluva-hoitovuosi
                                                                                                              :else aloitus-vuosi)
                                                                                                            (if (= lopetus-vuosi 0) 1 lopetus-vuosi)]]
                                                                         (p/paivita-arvo rivi :lapset
                                                                                         (fn [osat]
                                                                                           (tyokalut/mapv-range (inc aloitus-vuosi)
                                                                                                                (+ 2 lopetus-vuosi)
                                                                                                                (fn [vuosi-yhteensa-osa]
                                                                                                                  (let [laskulla-taulukon-rivin-osat (p/arvo (get-in laskulla-taulukko rivin-polku) :lapset)
                                                                                                                        tunnit (p/arvo (nth laskulla-taulukon-rivin-osat tunnit-sarakkeen-index) :arvo)
                                                                                                                        tunnit (if (number? tunnit) tunnit 0)
                                                                                                                        tuntipalkka (p/arvo (nth laskulla-taulukon-rivin-osat tuntipalkka-sarakkeen-index) :arvo)
                                                                                                                        tuntipalkka (if (number? tuntipalkka) tuntipalkka 0)
                                                                                                                        kk-v (p/arvo (nth laskulla-taulukon-rivin-osat kk-v-sarakkeen-index) :arvo)]
                                                                                                                    (p/aseta-arvo vuosi-yhteensa-osa :arvo (* tunnit tuntipalkka kk-v))))
                                                                                                                osat))))))
                                  (tyokalut/paivita-asiat-taulukossa [summa-rivin-index]
                                                                     (fn [taulukko polut]
                                                                       (let [[rivit rivi] polut
                                                                             rivit (get-in taulukko rivit)
                                                                             yhteensarivi (get-in taulukko rivi)
                                                                             summarivit (->> rivit rest (drop-last 2))]
                                                                         (p/paivita-arvo yhteensarivi
                                                                                         :lapset
                                                                                         (fn [osat]
                                                                                           (into []
                                                                                                 (map-indexed (fn [index osa]
                                                                                                                (if (> index 1)
                                                                                                                  (let [rivit-yhteensa-vuodelta (reduce (fn [summa rivi]
                                                                                                                                                          (+ summa (p/arvo
                                                                                                                                                                     (nth (p/arvo rivi :lapset)
                                                                                                                                                                          index)
                                                                                                                                                                     :arvo)))
                                                                                                                                                        0 summarivit)]
                                                                                                                    (p/aseta-arvo osa :arvo rivit-yhteensa-vuodelta))
                                                                                                                  osa))
                                                                                                              osat)))))))
                                  (tyokalut/paivita-asiat-taulukossa [indeksikorjatun-rivin-index]
                                                                     (fn [taulukko polut]
                                                                       (let [[rivit rivi] polut
                                                                             rivit (get-in taulukko rivit)
                                                                             indeksikorjattuyhteensarivi (get-in taulukko rivi)
                                                                             indeksikorjatturivit (->> rivit rest (drop-last 2))]
                                                                         (p/paivita-arvo indeksikorjattuyhteensarivi
                                                                                         :lapset
                                                                                         (fn [osat]
                                                                                           (into []
                                                                                                 (map-indexed (fn [index osa]
                                                                                                                (if (> index 1)
                                                                                                                  (let [rivit-yhteensa-vuodelta (reduce (fn [summa rivi]
                                                                                                                                                          (+ summa (p/arvo
                                                                                                                                                                     (nth (p/arvo rivi :lapset)
                                                                                                                                                                          index)
                                                                                                                                                                     :arvo)))
                                                                                                                                                        0 indeksikorjatturivit)]
                                                                                                                    (p/aseta-arvo osa :arvo (indeksikorjaa rivit-yhteensa-vuodelta (dec index))))
                                                                                                                  osa))
                                                                                                              osat))))))))]
      (-> app
          (assoc-in laskulla-taulukon-polku laskulla-taulukko)
          (assoc-in yhteenveto-taulukon-polku yhteenveto-taulukko))))
  PaivitaSuunnitelmienTila
  (process-event [{:keys [paivitetyt-taulukot]} {:keys [kuluva-hoitokausi suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat
                                                        hankintakustannukset hallinnolliset-toimenpiteet] :as app}]
    (let [paivitetyt-taulukot-instanssi @paivitetyt-taulukot
          tilat (suunnitelman-osat paivitetyt-taulukot-instanssi kuluva-hoitokausi suunnitelmien-tila-taulukko suunnitelmien-tila-taulukon-tilat hankintakustannukset hallinnolliset-toimenpiteet)
          suunnitelmien-tila-taulukko (paivita-suunnitelmien-tila-taulukko suunnitelmien-tila-taulukko paivitetyt-taulukot-instanssi tilat kuluva-hoitokausi)]
      (swap! paivitetyt-taulukot (fn [edelliset-taulukot]
                                   ;; On mahdollista, että tätä atomia päivitetään pääthreadissä
                                   ;; samaan aikaan kuin tämä process-event ajetaan. Jos näin käy, halutaan pitää
                                   ;; pääthreadin tekemät muutokset sen sijaan, että kaikki olisi nil,
                                   ;; koska ne on voimassa tämän process-eventin uudelleen ajossa.
                                   (if (not= edelliset-taulukot paivitetyt-taulukot-instanssi)
                                     edelliset-taulukot
                                     (assoc edelliset-taulukot :hankinnat nil :hallinnolliset nil))))
      (assoc app :suunnitelmien-tila-taulukko suunnitelmien-tila-taulukko
                 :suunnitelmien-tila-taulukon-tilat tilat
                 :suunnitelmien-tila-taulukon-tilat-luotu-kerran? true)))
  MaksukausiValittu
  (process-event [_ app]
    (let [maksetaan (get-in app [:suodattimet :hankinnat :maksetaan])
          maksu-kk (case maksetaan
                     :kesakausi [5 9]
                     :talvikausi [10 4]
                     :molemmat [1 12])
          piillotetaan? (fn [kk]
                          (case maksetaan
                            :kesakausi (or (< kk (first maksu-kk))
                                           (> kk (second maksu-kk)))
                            :talvikausi (and (< kk (first maksu-kk))
                                             (> kk (second maksu-kk)))
                            :molemmat false))
          g (get-in app [:gridit :suunnittellut-hankinnat :grid])]
      (doseq [otsikko-datasisalto (grid/hae-grid (grid/get-in-grid g [::g-pohjat/data]) :lapset)]
        (grid/paivita-grid! (grid/get-in-grid otsikko-datasisalto [::g-pohjat/data-sisalto])
                            :lapset
                            (fn [rivit]
                              (mapv (fn [rivi]
                                      (if (-> rivi (grid/get-in-grid [0]) grid/solun-arvo pvm/kuukausi piillotetaan?)
                                        (grid/piillota! rivi)
                                        (grid/nayta! rivi))
                                      rivi)
                                    rivit))))
      app
      #_(update app :hankintakustannukset
              (fn [kustannukset]
                (-> kustannukset
                    (update :toimenpiteet (fn [taulukot]
                                            (into {}
                                                  (map (fn [[toimenpide taulukko]]
                                                         (let [pvm-sarakkeen-index (p/otsikon-index taulukko "Nimi")]
                                                           [toimenpide (tyokalut/paivita-asiat-taulukossa taulukko [:laajenna-lapsilla :lapset]
                                                                                                          (fn [taulukko polut]
                                                                                                            (let [[_ _ _ lapsirivi] polut
                                                                                                                  lapsirivi (get-in taulukko lapsirivi)
                                                                                                                  pvm-solu (get (p/arvo lapsirivi :lapset) pvm-sarakkeen-index)
                                                                                                                  solun-kk (pvm/kuukausi (p/arvo pvm-solu :arvo))]
                                                                                                              (if (piillotetaan? solun-kk)
                                                                                                                (update lapsirivi ::piillotettu conj :maksetaan)
                                                                                                                (update lapsirivi ::piillotettu disj :maksetaan)))))]))
                                                       taulukot))))
                    (update :toimenpiteet-laskutukseen-perustuen (fn [taulukot]
                                                                   (into {}
                                                                         (map (fn [[toimenpide taulukko]]
                                                                                (let [pvm-sarakkeen-index (p/otsikon-index taulukko "Nimi")]
                                                                                  [toimenpide (tyokalut/paivita-asiat-taulukossa taulukko [:laajenna-lapsilla :lapset]
                                                                                                                                 (fn [taulukko polut]
                                                                                                                                   (let [[_ _ _ lapsirivi] polut
                                                                                                                                         lapsirivi (get-in taulukko lapsirivi)
                                                                                                                                         pvm-solu (get (p/arvo lapsirivi :lapset) pvm-sarakkeen-index)
                                                                                                                                         solun-kk (pvm/kuukausi (p/arvo pvm-solu :arvo))]
                                                                                                                                     (if (piillotetaan? solun-kk)
                                                                                                                                       (update lapsirivi ::piillotettu conj :maksetaan)
                                                                                                                                       (update lapsirivi ::piillotettu disj :maksetaan)))))]))
                                                                              taulukot)))))))))
  TallennaKiinteahintainenTyo
  (process-event [{:keys [toimenpide-avain osa polku-taulukkoon tayta-alas?]} app]
    (let [{urakka-id :id} (:urakka @tiedot/yleiset)
          taulukko (get-in app polku-taulukkoon)
          kopioidaan-tuleville-vuosille? (get-in app [:hankintakustannukset :valinnat :kopioidaan-tuleville-vuosille?])
          maksetaan (get-in app [:hankintakustannukset :valinnat :maksetaan])
          ajat (tallennettavien-hankintojen-ajat osa taulukko tayta-alas? kopioidaan-tuleville-vuosille? maksetaan)

          arvo (p/arvo osa :arvo)
          summa (-> arvo :value (clj-str/replace #"," ".") js/Number)

          lahetettava-data {:urakka-id urakka-id
                            :toimenpide-avain toimenpide-avain
                            :summa summa
                            :ajat ajat}]
      (tuck-apurit/post! app :tallenna-kiinteahintaiset-tyot
                         lahetettava-data
                         {:onnistui ->TallennaKiinteahintainenTyoOnnistui
                          :epaonnistui ->TallennaKiinteahintainenTyoEpaonnistui
                          :viive 1000
                          :tunniste (keyword harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
                                             (str "tallenna-kustannusarvioitutyo " toimenpide-avain " " (p/arvo osa :id) " " tayta-alas?))
                          :paasta-virhe-lapi? true})))
  TallennaKiinteahintainenTyoOnnistui
  (process-event [{:keys [vastaus]} app]
    app)
  TallennaKiinteahintainenTyoEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
  TallennaKustannusarvoituTyo
  (process-event [{:keys [tallennettava-asia toimenpide-avain arvo ajat]}
                  {{kuluva-hoitovuosi :vuosi kuluvan-hoitovuoden-hoitokausi :pvmt} :kuluva-hoitokausi
                   {valittu-hoitovuosi :hoitovuosi} :suodatin :as app}]
    (let [{:keys [alkupvm loppupvm] urakka-id :id} (:urakka @tiedot/yleiset)
          kuluvan-hoitokauden-aloitusvuosi (-> kuluvan-hoitovuoden-hoitokausi first pvm/vuosi)
          vuodesta-eteenpain (if (or (not (contains? filteroitavat-tallennettavat-asiat tallennettava-asia))
                                     (= kuluva-hoitovuosi valittu-hoitovuosi))
                               kuluvan-hoitokauden-aloitusvuosi
                               (dec (+ kuluvan-hoitokauden-aloitusvuosi valittu-hoitovuosi)))
          ajat (or ajat
                   (map (fn [vuosi]
                          {:vuosi vuosi})
                        (range vuodesta-eteenpain
                               (pvm/vuosi loppupvm))))
          lahetettava-data {:urakka-id urakka-id
                            :toimenpide-avain toimenpide-avain
                            :tallennettava-asia tallennettava-asia
                            :summa arvo
                            :ajat ajat}]
      (tuck-apurit/post! app :tallenna-kustannusarvioitu-tyo
                         lahetettava-data
                         {:onnistui ->TallennaKustannusarvoituTyoOnnistui
                          :epaonnistui ->TallennaKustannusarvoituTyoEpaonnistui
                          :viive 1000
                          :tunniste (keyword harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
                                             (str "tallenna-kustannusarvioitutyo " tallennettava-asia " " toimenpide-avain " " ajat))
                          :paasta-virhe-lapi? true})))
  TallennaKustannusarvoituTyoOnnistui
  (process-event [{:keys [vastaus]} app]
    app)
  TallennaKustannusarvoituTyoEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
  TallennaHankintasuunnitelma
  (process-event [{:keys [toimenpide-avain osa polku-taulukkoon tayta-alas? laskutuksen-perusteella-taulukko?]} app]
    (let [taulukko (get-in app polku-taulukkoon)
          kopioidaan-tuleville-vuosille? (get-in app [:hankintakustannukset :valinnat :kopioidaan-tuleville-vuosille?])
          maksetaan (get-in app [:hankintakustannukset :valinnat :maksetaan])
          ajat (tallennettavien-hankintojen-ajat osa taulukko tayta-alas? kopioidaan-tuleville-vuosille? maksetaan)
          arvo (p/arvo osa :arvo)
          summa (-> arvo :value (clj-str/replace #"," ".") js/Number)]
      (if laskutuksen-perusteella-taulukko?
        (tuck/process-event (->TallennaKustannusarvoituTyo :toimenpiteen-maaramitattavat-tyot toimenpide-avain summa ajat) app)
        (tuck/process-event (->TallennaKiinteahintainenTyo toimenpide-avain osa polku-taulukkoon tayta-alas?) app))))
  TallennaHankintasuunnitelmaOnnistui
  (process-event [{:keys [vastaus]} app]
    app)
  TallennaHankintasuunnitelmaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
  TallennaJohtoJaHallintokorvaukset
  (process-event [{:keys [osa polku-taulukkoon]}
                  {{valittu-hoitovuosi :hoitovuosi} :suodatin :as app}]
    (let [taulukko (get-in app polku-taulukkoon)
          [polku-riviin polku-osaan] (p/osan-polku-taulukossa taulukko osa)
          rivi (get-in taulukko polku-riviin)
          rivin-lisatty-data (::p/lisatty-data rivi)
          rivin-oleelliset-arvot (tyokalut/rivin-arvot-otsikoilla taulukko rivi "Tunnit/kk" "Tuntipalkka" "kk/v")
          {:keys [alkupvm loppupvm] urakka-id :id} (:urakka @tiedot/yleiset)
          tallennettavat-hoitokaudet (clj-set/intersection (into #{}
                                                                 (range (if (= valittu-hoitovuosi 1) 0 valittu-hoitovuosi)
                                                                        6))
                                                           (-> rivi ::p/lisatty-data :hoitokaudet))
          toimenkuva (:toimenkuva rivin-lisatty-data)
          maksukausi (:maksukausi rivin-lisatty-data)
          lahetettava-data {:urakka-id urakka-id
                            :toimenkuva toimenkuva
                            :maksukausi maksukausi
                            :jhkt (mapv (fn [hoitokausi]
                                          (assoc (zipmap [:tunnit :tuntipalkka :kk-v]
                                                         rivin-oleelliset-arvot)
                                            :hoitokausi hoitokausi))
                                        (sort tallennettavat-hoitokaudet))}]
      (tuck-apurit/post! app :tallenna-johto-ja-hallintokorvaukset
                         lahetettava-data
                         {:onnistui ->TallennaJohtoJaHallintokorvauksetOnnistui
                          :epaonnistui ->TallennaJohtoJaHallintokorvauksetEpaonnistui
                          :viive 1000
                          :tunniste (keyword harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
                                             (str "tallenna-johto-ja-hallintokorvaukset "
                                                  toimenkuva " " maksukausi " " tallennettavat-hoitokaudet))
                          :paasta-virhe-lapi? true})))
  TallennaJohtoJaHallintokorvauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    app)
  TallennaJohtoJaHallintokorvauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)

  TallennaJaPaivitaTavoiteSekaKattohinta
  (process-event [_ {:keys [hankintakustannukset hallinnolliset-toimenpiteet tavoite-ja-kattohinta]
                     {kuluva-hoitovuosi :vuosi} :kuluva-hoitokausi :as app}]
    (let [{:keys [toimenpiteet toimenpiteet-laskutukseen-perustuen rahavaraukset]} hankintakustannukset
          {:keys [erillishankinnat johto-ja-hallintokorvaus-yhteenveto toimistokulut johtopalkkio]} hallinnolliset-toimenpiteet
          vuosia-eteenpain (- 6 kuluva-hoitovuosi)
          kattohinnan-kerroin 1.1

          maara-taulukko-summa (fn [taulukko]
                                 (repeat vuosia-eteenpain
                                         (-> taulukko tyokalut/taulukko->data second (get "Yhteensä"))))
          toimenpiteet-summat (reduce-kv (fn [hoitokausien-summat toimenpide-avain taulukko]
                                           (let [toimenpiteen-laajenna-rivien-data (->> taulukko tyokalut/taulukko->data rest butlast (take-nth 13))
                                                 toimenpiteen-summat (map #(get % "Yhteensä") toimenpiteen-laajenna-rivien-data)]
                                             (map + hoitokausien-summat toimenpiteen-summat)))
                                         (repeat 5 0) toimenpiteet)
          toimenpiteet-laskutukseen-perustuen-summat (reduce-kv (fn [hoitokausien-summat toimenpide-avain taulukko]
                                                                  (let [toimenpiteen-laajenna-rivien-data (->> taulukko tyokalut/taulukko->data rest butlast (take-nth 13))
                                                                        toimenpiteen-summat (map #(get % "Yhteensä") toimenpiteen-laajenna-rivien-data)]
                                                                    (map + hoitokausien-summat toimenpiteen-summat)))
                                                                (repeat 5 0) toimenpiteet-laskutukseen-perustuen)

          johto-ja-hallintokorvaus-yhteenveto-data (-> johto-ja-hallintokorvaus-yhteenveto tyokalut/taulukko->data rest butlast)
          johto-ja-hallintokorvaus-yhteensa-summat (map (fn [hoitokausi]
                                                          (let [vuoden-otsikko (str hoitokausi ".vuosi/€")]
                                                            (apply + (map #(get % vuoden-otsikko) johto-ja-hallintokorvaus-yhteenveto-data))))
                                                        (range 1 6))

          ;; rahavaraukset ja vastaavat laskee summan kuluvalle vuodelle ja siitä eteenpäin.
          ;; hankinnat taas kaikki vuodet
          rahavaraukset-summat (reduce-kv (fn [hoitokausien-summat toimenpide-avain taulukko]
                                            (let [toimenpiteen-laajenna-rivien-data (-> taulukko tyokalut/taulukko->data rest)
                                                  toimenpiteen-vuoden-summa (apply + (map #(get % "Yhteensä") toimenpiteen-laajenna-rivien-data))]
                                              (+ hoitokausien-summat toimenpiteen-vuoden-summa)))
                                          0 rahavaraukset)
          rahavaraukset-summat (repeat vuosia-eteenpain rahavaraukset-summat)
          erillishankinnat-summat (maara-taulukko-summa erillishankinnat)
          toimistokulut-summat (maara-taulukko-summa toimistokulut)
          johtopalkkio-summat (maara-taulukko-summa johtopalkkio)

          summatut-arvot (map +
                              (drop (dec kuluva-hoitovuosi) toimenpiteet-summat)
                              (drop (dec kuluva-hoitovuosi) toimenpiteet-laskutukseen-perustuen-summat)
                              (drop (dec kuluva-hoitovuosi) johto-ja-hallintokorvaus-yhteensa-summat)
                              rahavaraukset-summat
                              erillishankinnat-summat
                              toimistokulut-summat
                              johtopalkkio-summat)
          paivitetyt-tavoitehinnat (map (fn [{:keys [summa hoitokausi] :as yhteensa}]
                                          (if (< hoitokausi kuluva-hoitovuosi)
                                            yhteensa
                                            (assoc yhteensa :summa (nth summatut-arvot (- hoitokausi kuluva-hoitovuosi)))))
                                        (:tavoitehinnat tavoite-ja-kattohinta))
          ;; Kantaan lähtevä data
          {urakka-id :id} (:urakka @tiedot/yleiset)

          lahetettava-data {:urakka-id urakka-id
                            :tavoitteet (mapv (fn [{:keys [summa hoitokausi]}]
                                                {:hoitokausi hoitokausi
                                                 :tavoitehinta summa
                                                 :kattohinta (* summa kattohinnan-kerroin)})
                                              paivitetyt-tavoitehinnat)}
          app (update app :tavoite-ja-kattohinta (fn [tavoite-ja-kattohinta]
                                                   (-> tavoite-ja-kattohinta
                                                       (assoc :tavoitehinnat (into [] paivitetyt-tavoitehinnat))
                                                       (assoc :kattohinnat (mapv (fn [tavoitehinta]
                                                                                   (update tavoitehinta :summa * kattohinnan-kerroin))
                                                                                 paivitetyt-tavoitehinnat)))))]
      (tuck-apurit/post! app :tallenna-budjettitavoite
                         lahetettava-data
                         {:onnistui ->TallennaJaPaivitaTavoiteSekaKattohintaOnnistui
                          :epaonnistui ->TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui
                          :paasta-virhe-lapi? true})))
  TallennaJaPaivitaTavoiteSekaKattohintaOnnistui
  (process-event [{:keys [vastaus]} app]
    app)
  TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)

  PaivitaHoidonjohtopalkkiot
  (process-event [{:keys [arvo]} app]
    (let [hoitokauden-numero (get-in app [:domain :hoitokausi :hoitokauden-numero])
          arvo (js/Number arvo)]
      (update-in app
                 [:domain :hoidonjohtopalkkio (dec hoitokauden-numero)]
                 (fn [hoidonjohtopalkkiot]
                   (mapv #(assoc % :maara arvo)
                         hoidonjohtopalkkiot))))))