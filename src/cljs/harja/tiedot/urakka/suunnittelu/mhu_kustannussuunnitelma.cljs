(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [clojure.string :as clj-str]
            [clojure.set :as clj-set]
            [clojure.walk :as walk]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.domain.oikeudet :as oikeudet]
            [cljs.spec.alpha :as s]
            [harja.loki :refer [warn]]
            [harja.tyokalut.regex :as re]
            [goog.dom :as dom]
            [harja.ui.modal :as modal]
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

(defn laskutukseen-perustuvan-taulukon-nakyvyys! []
  (let [{:keys [toimenpide laskutukseen-perustuen-valinta]} (-> @tiedot/suunnittelu-kustannussuunnitelma :suodattimet :hankinnat)
        laskutukseen-perustuvat-hankinnat-taulukko (get-in @tiedot/suunnittelu-kustannussuunnitelma [:gridit :laskutukseen-perustuvat-hankinnat :grid])]
    (if (contains? laskutukseen-perustuen-valinta toimenpide)
      (grid/nayta! laskutukseen-perustuvat-hankinnat-taulukko)
      (grid/piillota! laskutukseen-perustuvat-hankinnat-taulukko))))

(defn toimenpiteiden-jarjestys
  [toimenpide]
  (case toimenpide
    :talvihoito 0
    :liikenneympariston-hoito 1
    :sorateiden-hoito 2
    :paallystepaikkaukset 3
    :mhu-yllapito 4
    :mhu-korvausinvestointi 5))

(def hoitokausien-maara-urakassa 5)

(def toimenpiteet-rahavarauksilla #{:talvihoito
                                    :liikenneympariston-hoito
                                    :sorateiden-hoito
                                    :mhu-yllapito})

(def jh-korvausten-omiariveja-lkm 2)
(defn jh-omienrivien-nimi [index]
  (str "oma-" index))

(def kk-v-valinnat [:molemmat :talvi :kesa])

(def ^{:doc "Teksti, joka näytetään käyttäjälle yhteenveto-osiossa, kun
             määrät on suunniteltu kuukausitasolla"}
  vaihtelua-teksti "vaihtelua/kk")

(def rahavaraukset-jarjestys {"muut-rahavaraukset" 1
                              "vahinkojen-korjaukset" 1
                              "akillinen-hoitotyo" 2})

(defn toimenpide-koskee-ennen-urakkaa? [hoitokaudet]
  (= #{0} hoitokaudet))

(def johto-ja-hallintokorvaukset-pohjadata [{:toimenkuva "sopimusvastaava" :kk-v 12 :maksukausi :molemmat :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "vastuunalainen työnjohtaja" :kk-v 12 :maksukausi :molemmat :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "päätoiminen apulainen" :kk-v 7 :maksukausi :talvi :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "päätoiminen apulainen" :kk-v 5 :maksukausi :kesa :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "apulainen/työnjohtaja" :kk-v 7 :maksukausi :talvi :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "apulainen/työnjohtaja" :kk-v 5 :maksukausi :kesa :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "viherhoidosta vastaava henkilö" :kk-v 5 :maksukausi :molemmat :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "hankintavastaava" :kk-v 4.5 :maksukausi nil :hoitokaudet #{0}}
                                            {:toimenkuva "hankintavastaava" :kk-v 12 :maksukausi :molemmat :hoitokaudet (into #{} (range 1 6))}
                                            {:toimenkuva "harjoittelija" :kk-v 4 :maksukausi :molemmat :hoitokaudet (into #{} (range 1 6))}])

(defn aakkosta [sana]
  (get {"kesakausi" "kesäkausi"
        "liikenneympariston hoito" "liikenneympäristön hoito"
        "mhu yllapito" "mhu-ylläpito"
        "paallystepaikkaukset" "päällystepaikkaukset"
        "akillinen hoitotyo" "äkillinen hoitotyö"}
       sana
       sana))

(defn toimenkuva-formatoitu [{:keys [toimenkuva maksukausi hoitokaudet]}]
  (str (clj-str/capitalize toimenkuva) " "
       (case maksukausi
         :talvi "(talvikausi)"
         :kesa "(kesäkausi)"
         "")
       (when (contains? hoitokaudet 0)
         "(ennen urakkaa)")))

(def hallinnollisten-idt
  {:erillishankinnat "erillishankinnat"
   :johto-ja-hallintokorvaus "johto-ja-hallintokorvaus"
   :toimistokulut "toimistokulut"
   :hoidonjohtopalkkio "hoidonjohtopalkkio"
   :tilaajan-varaukset "tilaajan-varaukset"})

(def alimman-taulukon-id (hallinnollisten-idt :tilaajan-varaukset))


(defn toimenpiteen-rahavaraukset [toimenpide]
  (case toimenpide
    (:talvihoito :liikenneympariston-hoito :sorateiden-hoito) [:kokonaishintainen-ja-lisatyo :akillinen-hoitotyo :vahinkojen-korjaukset]
    :mhu-yllapito [:kokonaishintainen-ja-lisatyo :muut-rahavaraukset]
    [:kokonaishintainen-ja-lisatyo]))

(defn summaa-lehtivektorit [puu]
  (walk/postwalk (fn [x]
                   (if (and (map? x) (some vector? (vals x)))
                     (apply mapv + (vals x))
                     x))
                 puu))

(defn summaa-mapin-arvot [arvot avain]
  (reduce (fn [summa arvo]
            (+ summa (get arvo avain 0)))
          0
          arvot))

(def maksukauden-kuukaudet
  {:molemmat (vec (range 1 13))
   :talvi (vec (concat (range 1 5) (range 10 13)))
   :kesa (vec (range 5 10))})

(defn kuukausi-kuuluu-maksukauteen? [kuukausi maksukausi]
  (cond
    (= maksukausi :kesa) (<= 5 kuukausi 9)
    (= maksukausi :talvi) (or (<= 1 kuukausi 4)
                              (<= 10 kuukausi 12))
    :else true))

(defn tavoitehinnan-summaus [yhteenvedot]
  (mapv +
        (get-in yhteenvedot [:johto-ja-hallintokorvaukset :summat :erillishankinnat])
        (get-in yhteenvedot [:johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset])
        (get-in yhteenvedot [:johto-ja-hallintokorvaukset :summat :toimistokulut])
        (get-in yhteenvedot [:johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio])
        (summaa-lehtivektorit (get-in yhteenvedot [:hankintakustannukset :summat :rahavaraukset]))
        (summaa-lehtivektorit (get-in yhteenvedot [:hankintakustannukset :summat :suunnitellut-hankinnat]))
        (summaa-lehtivektorit (get-in yhteenvedot [:hankintakustannukset :summat :laskutukseen-perustuvat-hankinnat]))))

(defn kk-v-toimenkuvan-kuvaukselle [{:keys [toimenkuva maksukausi hoitokaudet kk-v]}]
  (cond
    (toimenpide-koskee-ennen-urakkaa? hoitokaudet) (js/Math.ceil kk-v)
    (= maksukausi :kesa) 5
    (= maksukausi :talvi) 7
    (= toimenkuva "harjoittelija") 4
    (= toimenkuva "viherhoidosta vastaava henkilö") 5
    :else 12))

(defn aseta-maara!
  ([grid-polku-fn domain-polku-fn tila arvo tunniste paivitetaan-domain?] (aseta-maara! grid-polku-fn domain-polku-fn tila arvo tunniste paivitetaan-domain? nil :yleinen))
  ([grid-polku-fn domain-polku-fn tila arvo tunniste paivitetaan-domain? hoitokauden-numero suodatin]
   (let [suodatin-polku (case suodatin
                          :yleinen [:suodattimet]
                          :hankinnat [:suodattimet :hankinnat])
         hoitokauden-numero (or hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero]))
         valittu-toimenpide (get-in tila [:suodattimet :hankinnat :toimenpide])
         kopioidaan-tuleville-vuosille? (get-in tila (conj suodatin-polku :kopioidaan-tuleville-vuosille?))
         paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                             (range hoitokauden-numero 6)
                                             [hoitokauden-numero])

         arvo (if (re-matches #"\d*,\d+" arvo)
                (clj-str/replace arvo #"," ".")
                arvo)
         paattyy-desimaalierottajaan? (re-matches #"\d*(,|\.)$" arvo)
         paivita-gridit (fn [tila]
                          (reduce (fn [tila hoitokauden-numero]
                                    (assoc-in tila (grid-polku-fn tunniste hoitokauden-numero valittu-toimenpide) arvo))
                                  tila
                                  paivitettavat-hoitokauden-numerot))
         paivita-domain (fn [tila]
                          (reduce (fn [tila hoitokauden-numero]
                                    (assoc-in tila (domain-polku-fn tunniste hoitokauden-numero valittu-toimenpide) (js/Number arvo)))
                                  tila
                                  paivitettavat-hoitokauden-numerot))]
     ;; Halutaan pitää data atomissa olevat arvot numeroina kun taasen käyttöliittymässä sen täytyy olla string (desimaalierottajan takia)
     (if hoitokauden-numero
       (if (and paivitetaan-domain?
                (not paattyy-desimaalierottajaan?))
         (-> tila paivita-domain paivita-gridit)
         (paivita-gridit tila))
       tila))))

(defn indeksikorjaa
  ([hinta] (indeksikorjaa hinta (-> @tiedot/suunnittelu-kustannussuunnitelma :domain :kuluva-hoitokausi :hoitokauden-numero)))
  ([hinta hoitokauden-numero]
   (let [{{:keys [indeksit]} :domain} @tiedot/suunnittelu-kustannussuunnitelma
         {:keys [indeksikerroin]} (get indeksit (dec hoitokauden-numero))]
     (when indeksikerroin
       (* hinta indeksikerroin)))))

(defn kuluva-hoitokausi []
  (let [hoitovuoden-pvmt (pvm/paivamaaran-hoitokausi (pvm/nyt))
        urakan-aloitusvuosi (pvm/vuosi (-> @tiedot/yleiset :urakka :alkupvm))
        kuluva-hoitokauden-numero (- (pvm/vuosi (second hoitovuoden-pvmt)) urakan-aloitusvuosi)]
    (if (> kuluva-hoitokauden-numero 5)
      ;; Mikäli on jo loppunut projekti, näytetään viimeisen vuoden tietoja
      {:hoitokauden-numero 5
       :pvmt (pvm/paivamaaran-hoitokausi (-> @tiedot/yleiset :urakka :loppupvm))}
      {:hoitokauden-numero kuluva-hoitokauden-numero
       :pvmt hoitovuoden-pvmt})))

(def suunnitelmien-tila-rajapinta (merge {:otsikot any?
                                          :hankintakustannukset any?
                                          :hallinnolliset-toimenpiteet any?}
                                         (reduce (fn [haut toimenpide]
                                                   (merge haut
                                                          {(keyword (str "toimenpide-" toimenpide)) any?}
                                                          (reduce (fn [rahavarauksien-haut rahavaraus]
                                                                    (merge rahavarauksien-haut
                                                                           {(keyword (str "rahavaraus-" rahavaraus "-" toimenpide)) any?}))
                                                                  {}
                                                                  (toimenpiteen-rahavaraukset toimenpide))))
                                                 {}
                                                 toimenpiteet)
                                         (reduce (fn [haut [_ id]]
                                                   (merge haut
                                                          {(keyword (str "hallinnollinen-" id)) any?}))
                                                 {}
                                                 hallinnollisten-idt)))

(defn suunnitelmien-tila-dr [kuluvan-hoitokauden-numero]
  (let [viimeinen-hoitokausi? (= 5 kuluvan-hoitokauden-numero)
        [ensimmainen-hoitokauden-numero toisen-hoitokauden-numero] (if viimeinen-hoitokausi?
                                                                     [nil 5]
                                                                     [kuluvan-hoitokauden-numero (inc kuluvan-hoitokauden-numero)])
        datan-arvo (fn [data avain]
                     (if avain (get data avain) data))
        valmis? (fn [data avain]
                  (every? #(and (not (nil? (datan-arvo % avain)))
                                (not= 0 (datan-arvo % avain)))
                          data))
        kesken? (fn [data avain]
                  (boolean (some #(and (not (nil? (datan-arvo % avain)))
                                       (not= 0 (datan-arvo % avain)))
                                 data)))
        aggregaatti-valmis? (fn [data]
                              (every? #(= :valmis %)
                                      data))
        aggregaatti-kesken? (fn [data]
                              (boolean (some #(or (= :valmis %)
                                                  (= :kesken %))
                                             data)))
        suunnitelman-tila (fn [data avain]
                            (cond
                              (valmis? data avain) :valmis
                              (kesken? data avain) :kesken
                              :else :aloittamatta))
        aggregaatin-tila (fn [rahavarausten-tilat]
                           (let [toimenpiteen-tilat-kuluvalle-hoitokaudelle (map #(get % :kuluva-hoitokausi) rahavarausten-tilat)
                                 toimenpiteen-tilat-seuraavalle-hoitokaudelle (map #(get % :seuraava-hoitokausi) rahavarausten-tilat)]
                             (cond-> {:kuluva-hoitokausi (cond
                                                           (aggregaatti-valmis? toimenpiteen-tilat-kuluvalle-hoitokaudelle) :valmis
                                                           (aggregaatti-kesken? toimenpiteen-tilat-kuluvalle-hoitokaudelle) :kesken
                                                           :else :aloittamatta)}
                                     (not viimeinen-hoitokausi?) (assoc :seuraava-hoitokausi (cond
                                                                                               (aggregaatti-valmis? toimenpiteen-tilat-seuraavalle-hoitokaudelle) :valmis
                                                                                               (aggregaatti-kesken? toimenpiteen-tilat-seuraavalle-hoitokaudelle) :kesken
                                                                                               :else :aloittamatta)))))
        suunnitelman-tila-ikoniksi (fn [suunnitelman-tila]
                                     (case suunnitelman-tila
                                       :aloittamatta ikonit/remove
                                       :kesken ikonit/livicon-question
                                       :valmis ikonit/ok
                                       nil))
        rahavarauksen-rajapinta-asetukset (fn [toimenpide rahavaraus polku]
                                            {:polut (cond-> [(conj polku (dec ensimmainen-hoitokauden-numero))]
                                                            (not viimeinen-hoitokausi?) (conj (conj polku (dec toisen-hoitokauden-numero))))
                                             :init (fn [tila]
                                                     (assoc-in tila
                                                               [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset rahavaraus]
                                                               (cond-> {:kuluva-hoitokausi :aloittamatta}
                                                                       (not viimeinen-hoitokausi?) (assoc :seuraava-hoitokausi :aloittamatta))))
                                             :aseta (if viimeinen-hoitokausi?
                                                      (fn [tila ensimmaisen-hoitokauden-data]
                                                        (assoc-in tila
                                                                  [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset rahavaraus :kuluva-hoitokausi]
                                                                  (suunnitelman-tila ensimmaisen-hoitokauden-data :maara)))
                                                      (fn [tila ensimmaisen-hoitokauden-data toisen-hoitokauden-data]
                                                        (update-in tila
                                                                   [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset rahavaraus]
                                                                   (fn [hoitokausien-tila]
                                                                     (assoc hoitokausien-tila
                                                                            :kuluva-hoitokausi (suunnitelman-tila ensimmaisen-hoitokauden-data :maara)
                                                                            :seuraava-hoitokausi (suunnitelman-tila toisen-hoitokauden-data :maara))))))})
        hallinnollisten-rajapinta-asetukset (fn [hallinto]
                                              (let [polun-alku [:domain hallinto]]
                                                {:polut (cond-> [(conj polun-alku (dec ensimmainen-hoitokauden-numero))]
                                                                (not viimeinen-hoitokausi?) (conj (conj polun-alku (dec toisen-hoitokauden-numero))))
                                                 :init (fn [tila]
                                                         (update-in tila
                                                                    [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet hallinto]
                                                                    merge
                                                                    (cond-> {:kuluva-hoitokausi :aloittamatta}
                                                                            (not viimeinen-hoitokausi?) (assoc :seuraava-hoitokausi :aloittamatta))))
                                                 :aseta (if viimeinen-hoitokausi?
                                                          (fn [tila ensimmaisen-hoitokauden-data]
                                                            (assoc-in tila
                                                                      [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet hallinto :kuluva-hoitokausi]
                                                                      (suunnitelman-tila ensimmaisen-hoitokauden-data :maara)))
                                                          (fn [tila ensimmaisen-hoitokauden-data toisen-hoitokauden-data]
                                                            (update-in tila
                                                                       [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet hallinto]
                                                                       (fn [hoitokausien-tila]
                                                                         (assoc hoitokausien-tila
                                                                                :kuluva-hoitokausi (suunnitelman-tila ensimmaisen-hoitokauden-data :maara)
                                                                                :seuraava-hoitokausi (suunnitelman-tila toisen-hoitokauden-data :maara))))))}))
        rahavaraus->nimi (fn [rahavaraus]
                           (case rahavaraus
                             :kokonaishintainen-ja-lisatyo "Suunnitellut hankinnat"
                             :vahinkojen-korjaukset "Kolmansien osapuolien aiheuttamien vaurioiden korjaukset"
                             :akillinen-hoitotyo "Äkilliset hoitotyöt"
                             :muut-rahavaraukset "Rahavaraus lupaukseen 1"))]
    (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                            suunnitelmien-tila-rajapinta
                            (merge {:otsikot {:polut [[:gridit :suunnitelmien-tila :otsikot]]
                                              :haku identity}
                                    :hankintakustannukset {:polut (mapv (fn [toimenpide]
                                                                          [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :aggregate])
                                                                        toimenpiteet)
                                                           :haku (fn [& toimenpiteiden-aggregatet]
                                                                   (let [{:keys [kuluva-hoitokausi seuraava-hoitokausi]} (aggregaatin-tila toimenpiteiden-aggregatet)]
                                                                     (if viimeinen-hoitokausi?
                                                                       ["Hankintakustannukset" {:ikoni (suunnitelman-tila-ikoniksi nil)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/vuosi*"]
                                                                       ["Hankintakustannukset" {:ikoni (suunnitelman-tila-ikoniksi kuluva-hoitokausi)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/vuosi*"])))}
                                    :hallinnolliset-toimenpiteet {:polut (vec
                                                                           (cons [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet :nimi]
                                                                                 (map (fn [[hallinto _]]
                                                                                        [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet hallinto])
                                                                                      hallinnollisten-idt)))
                                                                  :haku (fn [nimi & hallinnollisten-tila]
                                                                          (let [{:keys [kuluva-hoitokausi seuraava-hoitokausi]} (aggregaatin-tila hallinnollisten-tila)]
                                                                            (if viimeinen-hoitokausi?
                                                                              [nimi {:ikoni (suunnitelman-tila-ikoniksi nil)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/vuosi"]
                                                                              [nimi {:ikoni (suunnitelman-tila-ikoniksi kuluva-hoitokausi)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/vuosi"])))}}
                                   (reduce (fn [haut toimenpide]
                                             (merge haut
                                                    {(keyword (str "toimenpide-" toimenpide)) {:polut [[:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :aggregate]]
                                                                                               :haku (fn [{:keys [kuluva-hoitokausi seuraava-hoitokausi]}]
                                                                                                       (if viimeinen-hoitokausi?
                                                                                                         [(-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize) {:ikoni (suunnitelman-tila-ikoniksi nil)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/vuosi"]
                                                                                                         [(-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize) {:ikoni (suunnitelman-tila-ikoniksi kuluva-hoitokausi)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/vuosi"]))}}
                                                    (second (reduce (fn [[ensimmainen-rivi? rahavarauksien-haut] rahavaraus]
                                                                      (let [jakso (if ensimmainen-rivi?
                                                                                    "/kk**"
                                                                                    "/kk")]
                                                                        [false (merge rahavarauksien-haut
                                                                                      {(keyword (str "rahavaraus-" rahavaraus "-" toimenpide)) {:polut [[:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset rahavaraus]]
                                                                                                                                                :haku (fn [{:keys [kuluva-hoitokausi seuraava-hoitokausi]}]
                                                                                                                                                        (if viimeinen-hoitokausi?
                                                                                                                                                          [(rahavaraus->nimi rahavaraus) {:ikoni (suunnitelman-tila-ikoniksi nil)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} jakso]
                                                                                                                                                          [(rahavaraus->nimi rahavaraus) {:ikoni (suunnitelman-tila-ikoniksi kuluva-hoitokausi)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} jakso]))}})]))
                                                                    [true {}]
                                                                    (toimenpiteen-rahavaraukset toimenpide)))))
                                           {}
                                           toimenpiteet)
                                   (reduce (fn [haut [hallinto id]]
                                             (merge haut
                                                    {(keyword (str "hallinnollinen-" id)) {:polut [[:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet hallinto]]
                                                                                           :haku (fn [{:keys [nimi kuluva-hoitokausi seuraava-hoitokausi]}]
                                                                                                   (if viimeinen-hoitokausi?
                                                                                                     [nimi {:ikoni (suunnitelman-tila-ikoniksi nil)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/kk"]
                                                                                                     [nimi {:ikoni (suunnitelman-tila-ikoniksi kuluva-hoitokausi)} {:ikoni (suunnitelman-tila-ikoniksi seuraava-hoitokausi)} "/kk"]))}}))
                                           {}
                                           hallinnollisten-idt))
                            {}
                            (merge (reduce (fn [seurannat toimenpide]
                                             (merge seurannat
                                                    {(keyword (str "toimenpide-" toimenpide "-seuranta")) {:polut [[:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset]]
                                                                                                           :init (fn [tila]
                                                                                                                   (assoc-in tila
                                                                                                                             [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :aggregate]
                                                                                                                             (cond-> {:kuluva-hoitokausi :aloittamatta}
                                                                                                                                     (not viimeinen-hoitokausi?) (assoc :seuraava-hoitokausi :aloittamatta))))
                                                                                                           :aseta (fn [tila rahavarausten-tilat]
                                                                                                                    (assoc-in tila
                                                                                                                              [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :aggregate]
                                                                                                                              (aggregaatin-tila (map val rahavarausten-tilat))))}}
                                                    (reduce (fn [rahavarauksien-haut rahavaraus]
                                                              (merge rahavarauksien-haut
                                                                     {(keyword (str "rahavaraus-" rahavaraus "-" toimenpide "-seuranta")) (case rahavaraus
                                                                                                                                            :kokonaishintainen-ja-lisatyo {:polut (cond-> [[:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
                                                                                                                                                                                           [:domain :suunnittellut-hankinnat toimenpide (dec ensimmainen-hoitokauden-numero)]
                                                                                                                                                                                           [:domain :laskutukseen-perustuvat-hankinnat toimenpide (dec ensimmainen-hoitokauden-numero)]]
                                                                                                                                                                                          (not viimeinen-hoitokausi?) (conj [:domain :suunnittellut-hankinnat toimenpide (dec toisen-hoitokauden-numero)]
                                                                                                                                                                                                                            [:domain :laskutukseen-perustuvat-hankinnat toimenpide (dec toisen-hoitokauden-numero)]))
                                                                                                                                                                           :init (fn [tila]
                                                                                                                                                                                   (assoc-in tila
                                                                                                                                                                                             [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset rahavaraus]
                                                                                                                                                                                             (cond-> {:kuluva-hoitokausi :aloittamatta}
                                                                                                                                                                                                     (not viimeinen-hoitokausi?) (assoc :seuraava-hoitokausi :aloittamatta))))
                                                                                                                                                                           :aseta (if viimeinen-hoitokausi?
                                                                                                                                                                                    (fn [tila laskutukseen-perustuen-valinta ensimmaisen-hoitokauden-data laskutukseen-perustuen-data]
                                                                                                                                                                                      (let [suunnitellaan-laskutukseen-perustuen? (contains? laskutukseen-perustuen-valinta toimenpide)
                                                                                                                                                                                            kuluvan-hoitokauden-tila (suunnitelman-tila ensimmaisen-hoitokauden-data :maara)
                                                                                                                                                                                            laskutukseen-perustuvan-hoitokauden-tila (when suunnitellaan-laskutukseen-perustuen?
                                                                                                                                                                                                                                       (suunnitelman-tila laskutukseen-perustuen-data :maara))]
                                                                                                                                                                                        (assoc-in tila
                                                                                                                                                                                                  [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset rahavaraus :kuluva-hoitokausi]
                                                                                                                                                                                                  (if suunnitellaan-laskutukseen-perustuen?
                                                                                                                                                                                                    (aggregaatin-tila [{:kuluva-hoitokausi kuluvan-hoitokauden-tila} {:kuluva-hoitokausi laskutukseen-perustuvan-hoitokauden-tila}])
                                                                                                                                                                                                    kuluvan-hoitokauden-tila))))
                                                                                                                                                                                    (fn [tila laskutukseen-perustuen-valinta ensimmaisen-hoitokauden-data laskutukseen-perustuen-data toisen-hoitokauden-data toisen-laskutukseen-perustuen-data]
                                                                                                                                                                                      (let [suunnitellaan-laskutukseen-perustuen? (contains? laskutukseen-perustuen-valinta toimenpide)
                                                                                                                                                                                            kuluvan-hoitokauden-tila (suunnitelman-tila ensimmaisen-hoitokauden-data :maara)
                                                                                                                                                                                            seuraavan-hoitokauden-tila (suunnitelman-tila toisen-hoitokauden-data :maara)
                                                                                                                                                                                            laskutukseen-perustuvan-hoitokauden-tila (when suunnitellaan-laskutukseen-perustuen?
                                                                                                                                                                                                                                       (suunnitelman-tila laskutukseen-perustuen-data :maara))
                                                                                                                                                                                            seuraavan-laskutukseen-perustuvan-hoitokauden-tila (when suunnitellaan-laskutukseen-perustuen?
                                                                                                                                                                                                                                                 (suunnitelman-tila toisen-laskutukseen-perustuen-data :maara))
                                                                                                                                                                                            hoitokausien-tilat (if suunnitellaan-laskutukseen-perustuen?
                                                                                                                                                                                                                 (aggregaatin-tila [{:kuluva-hoitokausi kuluvan-hoitokauden-tila :seuraava-hoitokausi seuraavan-hoitokauden-tila}
                                                                                                                                                                                                                                    {:kuluva-hoitokausi laskutukseen-perustuvan-hoitokauden-tila :seuraava-hoitokausi seuraavan-laskutukseen-perustuvan-hoitokauden-tila}])
                                                                                                                                                                                                                 {:kuluva-hoitokausi kuluvan-hoitokauden-tila
                                                                                                                                                                                                                  :seuraava-hoitokausi seuraavan-hoitokauden-tila})]
                                                                                                                                                                                        (update-in tila
                                                                                                                                                                                                   [:gridit :suunnitelmien-tila :hankintakustannukset :toimenpiteet toimenpide :rahavaraukset rahavaraus]
                                                                                                                                                                                                   (fn [hoitokausien-tila]
                                                                                                                                                                                                     (merge hoitokausien-tila
                                                                                                                                                                                                            hoitokausien-tilat))))))}
                                                                                                                                            :akillinen-hoitotyo (rahavarauksen-rajapinta-asetukset toimenpide rahavaraus [:domain :rahavaraukset toimenpide "akillinen-hoitotyo"])
                                                                                                                                            :vahinkojen-korjaukset (rahavarauksen-rajapinta-asetukset toimenpide rahavaraus [:domain :rahavaraukset toimenpide "vahinkojen-korjaukset"])
                                                                                                                                            :muut-rahavaraukset (rahavarauksen-rajapinta-asetukset toimenpide rahavaraus [:domain :rahavaraukset toimenpide "muut-rahavaraukset"]))}))
                                                            {}
                                                            (toimenpiteen-rahavaraukset toimenpide))))
                                           {}
                                           toimenpiteet)
                                   (reduce (fn [haut [h-avain id]]
                                             (merge haut
                                                    {(keyword (str "hallinnollinen-" id "-seuranta")) (if (= h-avain :johto-ja-hallintokorvaus)
                                                                                                        {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto]]
                                                                                                         :init (fn [tila]
                                                                                                                 (update-in tila
                                                                                                                            [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus]
                                                                                                                            merge
                                                                                                                            (cond-> {:kuluva-hoitokausi :aloittamatta}
                                                                                                                                    (not viimeinen-hoitokausi?) (assoc :seuraava-hoitokausi :aloittamatta))))
                                                                                                         :aseta (fn [tila johto-ja-hallintokorvauksien-yhteenvedot]
                                                                                                                  (let [korvauksien-arvot-ensimmaiselle-ja-toiselle-hoitokaudelle (reduce-kv (fn [tilat _ v]
                                                                                                                                                                                               (let [maksukausien-arvot-ensimmaiselle-hoitokaudelle (keep (fn [[maksukausi hoitokausien-arvot]]
                                                                                                                                                                                                                                                            (let [seurataan-ennen-urakkaa-arvoja? (and (nil? maksukausi) (= 1 ensimmainen-hoitokauden-numero))]
                                                                                                                                                                                                                                                              (if seurataan-ennen-urakkaa-arvoja?
                                                                                                                                                                                                                                                                (first hoitokausien-arvot)
                                                                                                                                                                                                                                                                (get hoitokausien-arvot (dec ensimmainen-hoitokauden-numero)))))
                                                                                                                                                                                                                                                          v)
                                                                                                                                                                                                     maksukausien-arvot-seuraavalle-hoitokaudelle (when (not viimeinen-hoitokausi?)
                                                                                                                                                                                                                                                    (keep (fn [[maksukausi hoitokausien-arvot]]
                                                                                                                                                                                                                                                            (let [ennen-urakkaa? (nil? maksukausi)]
                                                                                                                                                                                                                                                              (when-not ennen-urakkaa?
                                                                                                                                                                                                                                                                (get hoitokausien-arvot (dec toisen-hoitokauden-numero)))))
                                                                                                                                                                                                                                                          v))]
                                                                                                                                                                                                 (cond-> (update tilat :ensimmainen concat maksukausien-arvot-ensimmaiselle-hoitokaudelle)
                                                                                                                                                                                                         (not viimeinen-hoitokausi?) (update :toinen concat maksukausien-arvot-seuraavalle-hoitokaudelle))))
                                                                                                                                                                                             {:ensimmainen []
                                                                                                                                                                                              :toinen []}
                                                                                                                                                                                             johto-ja-hallintokorvauksien-yhteenvedot)]
                                                                                                                    (update-in tila
                                                                                                                               [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet :johto-ja-hallintokorvaus]
                                                                                                                               (fn [hoitokausien-tila]
                                                                                                                                 (cond-> (assoc hoitokausien-tila :kuluva-hoitokausi (suunnitelman-tila (get korvauksien-arvot-ensimmaiselle-ja-toiselle-hoitokaudelle :ensimmainen) nil))
                                                                                                                                         (not viimeinen-hoitokausi?) (assoc :seuraava-hoitokausi (suunnitelman-tila (get korvauksien-arvot-ensimmaiselle-ja-toiselle-hoitokaudelle :toinen) nil)))))))}
                                                                                                        (hallinnollisten-rajapinta-asetukset h-avain))}))
                                           {}
                                           hallinnollisten-idt)))))

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
                          {:aseta-suunnittellut-hankinnat! (partial aseta-maara!
                                                                    (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
                                                                      [:gridit :suunnittellut-hankinnat :hankinnat (dec hoitokauden-numero) (first osan-paikka) osa])
                                                                    (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
                                                                      [:domain :suunnittellut-hankinnat valittu-toimenpide (dec hoitokauden-numero) (first osan-paikka) osa]))
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
                                                           :init (fn [tila]
                                                                   (assoc-in tila [:gridit :suunnittellut-hankinnat :hankinnat] (vec (repeat 5 nil))))
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
                                                                                                                             (-> tila
                                                                                                                                 (update-in [:gridit :suunnittellut-hankinnat :hankinnat (dec hoitokauden-numero)] (vec (repeat 12 {})))
                                                                                                                                 (update-in [:gridit :suunnittellut-hankinnat :yhteenveto :data] (fn [data]
                                                                                                                                                                                                   (if (nil? data)
                                                                                                                                                                                                     (vec (repeat 5 nil))
                                                                                                                                                                                                     data)))))
                                                                                                                     :aseta (fn [tila vuoden-hoidonjohtopalkkio valittu-toimenpide]
                                                                                                                              (let [vuoden-hoidonjohtopalkkiot-yhteensa (summaa-mapin-arvot (get-in vuoden-hoidonjohtopalkkio [valittu-toimenpide (dec hoitokauden-numero)])
                                                                                                                                                                                            :maara)]
                                                                                                                                (-> tila
                                                                                                                                    (assoc-in [:gridit :suunnittellut-hankinnat :yhteenveto :data (dec hoitokauden-numero)] {:yhteensa vuoden-hoidonjohtopalkkiot-yhteensa
                                                                                                                                                                                                                             :indeksikorjattu (indeksikorjaa vuoden-hoidonjohtopalkkiot-yhteensa)})
                                                                                                                                    ;; Päivitetään myös yhteenvedotkomponentti
                                                                                                                                    (assoc-in [:yhteenvedot :hankintakustannukset :summat :suunnitellut-hankinnat valittu-toimenpide (dec hoitokauden-numero)] vuoden-hoidonjohtopalkkiot-yhteensa))))}}))
                                      {}
                                      (range 1 6))))))

(def laskutukseen-perustuvat-hankinnat-rajapinta (merge {:otsikot any?
                                                         :yhteensa any?

                                                         :aseta-laskutukseen-perustuvat-hankinnat! any?
                                                         :aseta-yhteenveto! any?}
                                                        (reduce (fn [rajapinnat hoitokauden-numero]
                                                                  (merge rajapinnat
                                                                         {(keyword (str "yhteenveto-" hoitokauden-numero)) any?
                                                                          (keyword (str "laskutukseen-perustuvat-hankinnat-" hoitokauden-numero)) any?}))
                                                                {}
                                                                (range 1 6))))

(defn laskutukseen-perustuvat-hankinnat-dr []
  (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                          laskutukseen-perustuvat-hankinnat-rajapinta
                          (merge {:otsikot {:polut [[:gridit :laskutukseen-perustuvat-hankinnat :otsikot]]
                                            :haku identity}
                                  :yhteensa {:polut [[:gridit :laskutukseen-perustuvat-hankinnat :yhteensa :data]
                                                     [:gridit :laskutukseen-perustuvat-hankinnat :yhteensa :nimi]]
                                             :haku (fn [data nimi]
                                                     (assoc data :nimi nimi))}}
                                 (reduce (fn [haut hoitokauden-numero]
                                           (merge haut
                                                  {(keyword (str "yhteenveto-" hoitokauden-numero)) {:polut [[:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data (dec hoitokauden-numero)]]
                                                                                                     :haku (fn [data]
                                                                                                             (assoc data :nimi (str hoitokauden-numero ". hoitovuosi")))}
                                                   (keyword (str "laskutukseen-perustuvat-hankinnat-" hoitokauden-numero)) {:polut [[:domain :laskutukseen-perustuvat-hankinnat]
                                                                                                                                    [:suodattimet :hankinnat :toimenpide]
                                                                                                                                    [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat (dec hoitokauden-numero)]]
                                                                                                                            :haku (fn [laskutukseen-perustuvat-hankinnat valittu-toimenpide johdetut-arvot]
                                                                                                                                    (let [arvot (mapv (fn [m]
                                                                                                                                                        (select-keys m #{:maara :aika :yhteensa}))
                                                                                                                                                      (get-in laskutukseen-perustuvat-hankinnat [valittu-toimenpide (dec hoitokauden-numero)]))
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
                          {:aseta-laskutukseen-perustuvat-hankinnat! (partial aseta-maara!
                                                                              (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
                                                                                [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat (dec hoitokauden-numero) (first osan-paikka) osa])
                                                                              (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
                                                                                [:domain :laskutukseen-perustuvat-hankinnat valittu-toimenpide (dec hoitokauden-numero) (first osan-paikka) osa]))
                           :aseta-yhteenveto! (fn [tila arvo tunniste hoitokauden-numero]
                                                (let [arvo (if (re-matches #"\d*,\d+" arvo)
                                                             (clj-str/replace arvo #"," ".")
                                                             arvo)]
                                                  (if hoitokauden-numero
                                                    (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data (dec hoitokauden-numero) tunniste] arvo)
                                                    tila)))}
                          (merge
                            {:hankinnat-yhteensa-seuranta {:polut [[:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data]]
                                                           :init (fn [tila]
                                                                   (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :yhteensa :data] nil))
                                                           :aseta (fn [tila data]
                                                                    (let [hoidonjohtopalkkiot-yhteensa (apply + (map :yhteensa data))]
                                                                      (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :yhteensa :data] {:yhteensa hoidonjohtopalkkiot-yhteensa
                                                                                                                                                   :indeksikorjattu (indeksikorjaa hoidonjohtopalkkiot-yhteensa)})))}
                             :valittu-toimenpide-seuranta {:polut [[:suodattimet :hankinnat :toimenpide]]
                                                           :init (fn [tila]
                                                                   (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat] (vec (repeat 5 nil))))
                                                           :aseta (fn [tila _]
                                                                    (update-in tila [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat]
                                                                               (fn [kaikki-hankinnat]
                                                                                 (mapv (fn [hoitokauden-hankinnat]
                                                                                         (vec (repeat 12 {})))
                                                                                       kaikki-hankinnat))))}}
                            (doall
                              (reduce (fn [seurannat hoitokauden-numero]
                                        (merge seurannat
                                               {(keyword (str "laskutukseen-perustuvat-yhteenveto-seuranta-" hoitokauden-numero)) {:polut [[:domain :laskutukseen-perustuvat-hankinnat]
                                                                                                                                           [:suodattimet :hankinnat :toimenpide]]
                                                                                                                                   :init (fn [tila]
                                                                                                                                           (-> tila
                                                                                                                                               (update-in [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat (dec hoitokauden-numero)] (vec (repeat 12 {})))
                                                                                                                                               (update-in [:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data] (fn [data]
                                                                                                                                                                                                                           (if (nil? data)
                                                                                                                                                                                                                             (vec (repeat 5 nil))
                                                                                                                                                                                                                             data)))))
                                                                                                                                   :aseta (fn [tila vuoden-hoidonjohtopalkkio valittu-toimenpide]
                                                                                                                                            (let [vuoden-hoidonjohtopalkkiot-yhteensa (summaa-mapin-arvot (get-in vuoden-hoidonjohtopalkkio [valittu-toimenpide (dec hoitokauden-numero)])
                                                                                                                                                                                                          :maara)]
                                                                                                                                              (-> tila
                                                                                                                                                  (assoc-in [:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data (dec hoitokauden-numero)] {:yhteensa vuoden-hoidonjohtopalkkiot-yhteensa
                                                                                                                                                                                                                                                     :indeksikorjattu (indeksikorjaa vuoden-hoidonjohtopalkkiot-yhteensa)})
                                                                                                                                                  ;; Päivitetään myös yhteenvedotkomponentti
                                                                                                                                                  (assoc-in [:yhteenvedot :hankintakustannukset :summat :suunnitellut-hankinnat valittu-toimenpide (dec hoitokauden-numero)] vuoden-hoidonjohtopalkkiot-yhteensa))))}}))
                                      {}
                                      (range 1 6))))))

(def rahavarausten-rajapinta {:rahavaraukset-otsikot any?
                              :rahavaraukset-yhteensa any?
                              :rahavaraukset any?
                              :rahavaraukset-kuukausitasolla? any?
                              :kuukausitasolla? any?

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
                                                                {:valittu-toimenpide valittu-toimenpide
                                                                 :hoitokauden-numero hoitokauden-numero})))}
                           :rahavaraukset-yhteenveto {:polut [[:gridit :rahavaraukset :seurannat]
                                                              [:suodattimet :hankinnat :toimenpide]
                                                              [:suodattimet :hoitokauden-numero]]
                                                      :luonti (fn [seurannat valittu-toimenpide hoitokauden-numero]
                                                                (vec
                                                                  (map (fn [[tyyppi _]]
                                                                         ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                                                         {(keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero)) [[:gridit :rahavaraukset :seurannat tyyppi]]})
                                                                       seurannat)))
                                                      :haku identity}
                           :rahavaraukset-kuukausitasolla? {:polut [[:domain :rahavaraukset]
                                                                    [:suodattimet :hankinnat :toimenpide]]
                                                            :luonti-init (fn [tila rahavaraukset valittu-toimenpide]
                                                                           (reduce (fn [tila tyyppi]
                                                                                     (assoc-in tila [:gridit :rahavaraukset :kuukausitasolla? tyyppi] false))
                                                                                   tila
                                                                                   (distinct (keys (get rahavaraukset valittu-toimenpide)))))
                                                            :luonti (fn [rahavaraukset valittu-toimenpide]
                                                                      (mapv (fn [tyyppi]
                                                                              {(keyword (str "rahavaraukset-kuukausitasolla-" tyyppi "?")) [[:gridit :rahavaraukset :kuukausitasolla? tyyppi]]})
                                                                            (distinct (keys (get rahavaraukset valittu-toimenpide)))))
                                                            :haku identity}
                           :rahavaraukset-data {:polut [[:domain :rahavaraukset]
                                                        [:suodattimet :hankinnat :toimenpide]
                                                        [:suodattimet :hoitokauden-numero]]
                                                :luonti (fn [rahavaraukset valittu-toimenpide hoitokauden-numero]
                                                          (let [toimenpiteen-rahavaraukset (get rahavaraukset valittu-toimenpide)]
                                                            (mapv (fn [[tyyppi data]]
                                                                    {(keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero)) [[:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                                                                                                                                 [:gridit :rahavaraukset :varaukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]]})
                                                                  toimenpiteen-rahavaraukset)
                                                            #_(when (not (nil? (ffirst toimenpiteen-rahavaraukset)))
                                                                (mapv (fn [[tyyppi data]]
                                                                        {(keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero)) [[:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
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
                                                   (let [hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
                                                         valittu-toimenpide (get-in tila [:suodattimet :hankinnat :toimenpide])
                                                         kopioidaan-tuleville-vuosille? (get-in tila [:suodattimet :kopioidaan-tuleville-vuosille?])
                                                         paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                                                                             (range hoitokauden-numero 6)
                                                                                             [hoitokauden-numero])

                                                         arvo (if (re-matches #"\d*,\d+" arvo)
                                                                (clj-str/replace arvo #"," ".")
                                                                arvo)
                                                         paattyy-desimaalierottajaan? (re-matches #"\d*(,|\.)$" arvo)
                                                         paivita-gridit (fn [tila]
                                                                          (reduce (fn [tila hoitokauden-numero]
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
                                                                                  tila
                                                                                  paivitettavat-hoitokauden-numerot))
                                                         paivita-domain (fn [tila]
                                                                          (reduce (fn [tila hoitokauden-numero]
                                                                                    (update-in tila [:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                                                               (fn [hoitokauden-varaukset]
                                                                                                 (if osan-paikka
                                                                                                   (assoc-in hoitokauden-varaukset [(first osan-paikka) osa] (js/Number arvo))
                                                                                                   (mapv (fn [varaus]
                                                                                                           (assoc varaus osa (js/Number arvo)))
                                                                                                         hoitokauden-varaukset)))))
                                                                                  tila
                                                                                  paivitettavat-hoitokauden-numerot))]
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
                           ;; Hoitaa myös kuukausitasolla? filtterin asettamisen
                           :rahavaraukset-yhteenveto-asettaminen {:polut [[:domain :rahavaraukset]
                                                                          [:suodattimet :hankinnat :toimenpide]
                                                                          [:suodattimet :hoitokauden-numero]]
                                                                  :luonti (fn [rahavaraukset valittu-toimenpide hoitokauden-numero]
                                                                            (when (contains? toimenpiteet-rahavarauksilla valittu-toimenpide)
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
                                                                                            toimenpiteen-rahavaraukset))))))
                                                                  :siivoa-tila (fn [tila _ _ tyyppi]
                                                                                 (update-in tila
                                                                                            [:gridit :rahavaraukset :seurannat]
                                                                                            (fn [tyyppien-data]
                                                                                              (dissoc tyyppien-data tyyppi))))
                                                                  :aseta (fn [tila maarat valittu-toimenpide tyyppi]
                                                                           (when (contains? toimenpiteet-rahavarauksilla valittu-toimenpide)
                                                                             (let [yhteensa (summaa-mapin-arvot maarat :maara)
                                                                                   hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
                                                                                   kuukausitasolla? (not (every? #(= (:maara (first maarat)) (:maara %))
                                                                                                                 maarat))
                                                                                   kopioidaan-tuleville-vuosille? (get-in tila [:suodattimet :kopioidaan-tuleville-vuosille?])
                                                                                   paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                                                                                                       (range hoitokauden-numero 6)
                                                                                                                       [hoitokauden-numero])
                                                                                   tila (assoc-in tila
                                                                                                  [:gridit :rahavaraukset :seurannat tyyppi]
                                                                                                  {:nimi (-> tyyppi (clj-str/replace #"-" " ") aakkosta clj-str/capitalize)
                                                                                                   :yhteensa yhteensa
                                                                                                   :indeksikorjattu (indeksikorjaa yhteensa)
                                                                                                   :maara (if kuukausitasolla?
                                                                                                            vaihtelua-teksti
                                                                                                            (:maara (first maarat)))})]
                                                                               (reduce (fn [tila hoitokauden-numero]
                                                                                         (assoc-in tila [:yhteenvedot :hankintakustannukset :summat :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)] yhteensa))
                                                                                       tila
                                                                                       paivitettavat-hoitokauden-numerot))))}
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


(defn maarataulukon-rajapinta [polun-osa aseta-yhteenveto-avain aseta-avain]
  {:otsikot any?
   :yhteenveto any?
   :kuukausitasolla? any?
   :yhteensa any?
   polun-osa any?

   aseta-avain any?
   aseta-yhteenveto-avain any?})

(defn maarataulukon-dr [rajapinta polun-osa yhteenvedot-polku aseta-avain aseta-yhteenveto-avain]
  (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                          rajapinta
                          {:otsikot {:polut [[:gridit polun-osa :otsikot]]
                                     :haku identity}
                           :yhteenveto {:polut [[:gridit polun-osa :yhteenveto]]
                                        :haku identity}
                           :kuukausitasolla? {:polut [[:gridit polun-osa :kuukausitasolla?]]
                                              :luonti-init (fn [tila _]
                                                             (assoc-in tila [:gridit polun-osa :kuukausitasolla?] false))
                                              :haku identity}
                           polun-osa {:polut [[:domain polun-osa]
                                              [:suodattimet :hoitokauden-numero]
                                              [:gridit polun-osa :palkkiot]
                                              [:gridit polun-osa :kuukausitasolla?]]
                                      :haku (fn [osan-arvot hoitokauden-numero johdetut-arvot kuukausitasolla?]
                                              (let [arvot (if hoitokauden-numero
                                                            (get osan-arvot (dec hoitokauden-numero))
                                                            [])
                                                    arvot (mapv (fn [m]
                                                                  (select-keys m #{:maara :aika}))
                                                                arvot)]
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
                           :yhteensa {:polut [[:gridit polun-osa :yhteensa]]
                                      :haku identity}}
                          {aseta-avain (partial aseta-maara!
                                                (fn [{:keys [osa osan-paikka]} _ _]
                                                  [:gridit polun-osa :palkkiot (first osan-paikka) osa])
                                                (fn [{:keys [osa osan-paikka]} hoitokauden-numero _]
                                                  [:domain polun-osa (dec hoitokauden-numero) (first osan-paikka) osa]))
                           aseta-yhteenveto-avain (fn [tila arvo tunniste paivitetaan-domain?]
                                                    (let [arvo (cond
                                                                 ;; Voi olla nil on-focus eventin jälkeen, kun "vaihtelua/kk" teksti otetaan pois
                                                                 (nil? arvo) nil
                                                                 (re-matches #"\d*,\d+" arvo) (clj-str/replace arvo #"," ".")
                                                                 :else arvo)
                                                          hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
                                                          kopioidaan-tuleville-vuosille? (get-in tila [:suodattimet :kopioidaan-tuleville-vuosille?])
                                                          paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                                                                              (range hoitokauden-numero 6)
                                                                                              [hoitokauden-numero])
                                                          domain-paivitys (fn [tila]
                                                                            (reduce (fn [tila hoitokauden-numero]
                                                                                      (update-in tila
                                                                                                 [:domain polun-osa (dec hoitokauden-numero)]
                                                                                                 (fn [hoitokauden-maarat]
                                                                                                   (mapv (fn [maara]
                                                                                                           (assoc maara tunniste (js/Number arvo)))
                                                                                                         hoitokauden-maarat))))
                                                                                    tila
                                                                                    paivitettavat-hoitokauden-numerot))
                                                          grid-paivitys (fn [tila kaikki?]
                                                                          (if kaikki?
                                                                            (update-in tila
                                                                                       [:gridit polun-osa :palkkiot]
                                                                                       (fn [hoitokauden-maarat]
                                                                                         (mapv (fn [maara]
                                                                                                 (assoc maara tunniste arvo))
                                                                                               hoitokauden-maarat)))
                                                                            (assoc-in tila [:gridit polun-osa :yhteenveto tunniste] arvo)))]
                                                      (if (and paivitetaan-domain? arvo (not (.isNaN js/Number arvo)))
                                                        (-> tila domain-paivitys (grid-paivitys true))
                                                        (grid-paivitys tila false))))}
                          {:yhteenveto-seuranta {:polut [[:domain polun-osa]
                                                         [:suodattimet :hoitokauden-numero]]
                                                 :init (fn [tila]
                                                         (assoc-in tila [:gridit polun-osa :palkkiot] (vec (repeat 12 {}))))
                                                 :aseta (fn [tila maarat hoitokauden-numero]
                                                          (let [valitun-vuoden-maarat (get maarat (dec hoitokauden-numero))
                                                                vuoden-maarat-yhteensa (summaa-mapin-arvot valitun-vuoden-maarat :maara)
                                                                maarat-samoja? (apply = (map :maara valitun-vuoden-maarat))
                                                                maara (if maarat-samoja?
                                                                        (get-in valitun-vuoden-maarat [0 :maara])
                                                                        vaihtelua-teksti)]
                                                            (-> tila
                                                                (assoc-in [:gridit polun-osa :yhteenveto :maara] maara)
                                                                (assoc-in [:gridit polun-osa :yhteenveto :yhteensa] vuoden-maarat-yhteensa)
                                                                (assoc-in [:gridit polun-osa :yhteenveto :indeksikorjattu] (indeksikorjaa vuoden-maarat-yhteensa)))))}
                           :yhteenveto-komponentin-seuranta {:polut [[:domain polun-osa]]
                                                             :aseta (fn [tila maarat]
                                                                      (let [hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
                                                                            kopioidaan-tuleville-vuosille? (get-in tila [:suodattimet :kopioidaan-tuleville-vuosille?])
                                                                            paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                                                                                                (range hoitokauden-numero 6)
                                                                                                                [hoitokauden-numero])
                                                                            valitun-vuoden-maarat (get maarat (dec hoitokauden-numero))
                                                                            vuoden-maarat-yhteensa (summaa-mapin-arvot valitun-vuoden-maarat :maara)]
                                                                        (reduce (fn [tila hoitokauden-numero]
                                                                                  (assoc-in tila (vec (concat yhteenvedot-polku [:summat polun-osa (dec hoitokauden-numero)])) vuoden-maarat-yhteensa))
                                                                                tila
                                                                                paivitettavat-hoitokauden-numerot)))}
                           :yhteensa-seuranta {:polut [[:domain polun-osa]]
                                               :aseta (fn [tila maarat]
                                                        (let [maarat-yhteensa (summaa-mapin-arvot (flatten maarat) :maara)]
                                                          (-> tila
                                                              (assoc-in [:gridit polun-osa :yhteensa :yhteensa] maarat-yhteensa)
                                                              (assoc-in [:gridit polun-osa :yhteensa :indeksikorjattu] (indeksikorjaa maarat-yhteensa)))))}}))

(def johto-ja-hallintokorvaus-rajapinta (merge {:otsikot any?

                                                :aseta-jh-yhteenveto! any?
                                                :aseta-tunnit! any?}
                                               (reduce (fn [rajapinnat index]
                                                         (let [nimi (jh-omienrivien-nimi index)]
                                                           (merge rajapinnat
                                                                  {(keyword (str "johto-ja-hallintokorvaus-" nimi)) any?
                                                                   (keyword (str "kuukausitasolla?-" nimi)) any?
                                                                   (keyword (str "yhteenveto-" nimi)) any?})))
                                                       {}
                                                       (range 1 (inc jh-korvausten-omiariveja-lkm)))
                                               (reduce (fn [rajapinnat {:keys [toimenkuva maksukausi]}]
                                                         (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                                                           (assoc rajapinnat
                                                                  (keyword (str "yhteenveto" yksiloiva-nimen-paate)) any?
                                                                  (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate)) any?
                                                                  (keyword (str "johto-ja-hallintokorvaus" yksiloiva-nimen-paate)) any?)))
                                                       {}
                                                       johto-ja-hallintokorvaukset-pohjadata)))

(defn jh-yhteenvetopaivitys [tila arvo {:keys [omanimi osa toimenkuva maksukausi data-koskee-ennen-urakkaa? osa-kuukaudesta-vaikuttaa?]} paivitetaan-domain?]
  (let [arvo (cond
               ;; Voi olla nil on-focus eventin jälkeen, kun "vaihtelua/kk" teksti otetaan pois
               ;; Voi olla myös keyword kun valitaan maksukausi
               (not (string? arvo)) arvo
               (re-matches #"\d*,\d+" arvo) (clj-str/replace arvo #"," ".")
               :else arvo)
        hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
        kopioidaan-tuleville-vuosille? (get-in tila [:suodattimet :kopioidaan-tuleville-vuosille?])
        paivitettavat-hoitokauden-numerot (cond
                                            data-koskee-ennen-urakkaa? [1]
                                            kopioidaan-tuleville-vuosille? (range hoitokauden-numero 6)
                                            :else [hoitokauden-numero])
        domain-paivitys (fn [tila]
                          (reduce (fn [tila hoitokauden-numero]
                                    (update-in tila
                                               (if omanimi
                                                 [:domain :johto-ja-hallintokorvaukset omanimi (dec hoitokauden-numero)]
                                                 [:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi (dec hoitokauden-numero)])
                                               (fn [hoitokauden-jh-korvaukset]
                                                 (mapv (fn [{:keys [osa-kuukaudesta] :as jh-korvaus}]
                                                         (assoc jh-korvaus osa (cond
                                                                                 (= osa :toimenkuva) arvo
                                                                                 (and data-koskee-ennen-urakkaa? osa-kuukaudesta-vaikuttaa?) (* osa-kuukaudesta (js/Number arvo))
                                                                                 :else (js/Number arvo))))
                                                       hoitokauden-jh-korvaukset))))
                                  tila
                                  paivitettavat-hoitokauden-numerot))
        grid-paivitys (fn [tila kaikki?]
                        (if kaikki?
                          (update-in tila
                                     (if omanimi
                                       [:gridit :johto-ja-hallintokorvaukset :johdettu omanimi]
                                       [:gridit :johto-ja-hallintokorvaukset :johdettu toimenkuva maksukausi])
                                     (fn [hoitokauden-jh-korvaukset]
                                       (mapv (fn [jh-korvaus]
                                               (assoc jh-korvaus osa arvo))
                                             hoitokauden-jh-korvaukset)))
                          (assoc-in tila
                                    (if omanimi
                                      [:gridit :johto-ja-hallintokorvaukset :yhteenveto omanimi osa]
                                      [:gridit :johto-ja-hallintokorvaukset :yhteenveto toimenkuva maksukausi osa])
                                    arvo)))]
    (if (and paivitetaan-domain? arvo (not (.isNaN js/Number arvo)))
      (-> tila domain-paivitys (grid-paivitys true))
      (grid-paivitys tila false))))

(defn johto-ja-hallintokorvaus-dr []
  (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                          johto-ja-hallintokorvaus-rajapinta
                          (merge
                            {:otsikot {:polut [[:gridit :johto-ja-hallintokorvaukset :otsikot]]
                                       :haku identity}}
                            (apply merge
                                   (reduce (fn [rajapinnat index]
                                             (let [nimi (jh-omienrivien-nimi index)]
                                               (merge rajapinnat
                                                      {(keyword (str "yhteenveto-" nimi)) {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi]]
                                                                                           :haku identity}
                                                       (keyword (str "kuukausitasolla?-" nimi)) {:polut [[:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? nimi]]
                                                                                                 :luonti-init (fn [tila _]
                                                                                                                (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? nimi] false))
                                                                                                 :haku identity}
                                                       (keyword (str "johto-ja-hallintokorvaus-" nimi)) {:polut [[:domain :johto-ja-hallintokorvaukset nimi]
                                                                                                                 [:suodattimet :hoitokauden-numero]
                                                                                                                 [:gridit :johto-ja-hallintokorvaukset :johdettu nimi]
                                                                                                                 [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? nimi]]
                                                                                                         :haku (fn [jh-korvaukset hoitokauden-numero johdetut-arvot kuukausitasolla?]
                                                                                                                 (let [arvot (if hoitokauden-numero
                                                                                                                               (get jh-korvaukset (dec hoitokauden-numero))
                                                                                                                               [])]
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
                                                                                                                              johdetut-arvot))))))}})))
                                           {}
                                           (range 1 (inc jh-korvausten-omiariveja-lkm)))
                                   (mapv (fn [{:keys [toimenkuva maksukausi hoitokaudet]}]
                                           (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)
                                                 data-koskee-ennen-urakkaa? (toimenpide-koskee-ennen-urakkaa? hoitokaudet)]
                                             (if data-koskee-ennen-urakkaa?
                                               {(keyword (str "yhteenveto" yksiloiva-nimen-paate)) {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto toimenkuva maksukausi]]
                                                                                                    :haku identity}}
                                               {(keyword (str "yhteenveto" yksiloiva-nimen-paate)) {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto toimenkuva maksukausi]]
                                                                                                    :haku identity}
                                                (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate)) {:polut [[:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi]]
                                                                                                          :luonti-init (fn [tila _]
                                                                                                                         (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi] false))
                                                                                                          :haku identity}
                                                (keyword (str "johto-ja-hallintokorvaus" yksiloiva-nimen-paate)) {:polut [[:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi]
                                                                                                                          [:suodattimet :hoitokauden-numero]
                                                                                                                          [:gridit :johto-ja-hallintokorvaukset :johdettu toimenkuva maksukausi]
                                                                                                                          [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi]]
                                                                                                                  :haku (fn [jh-korvaukset hoitokauden-numero johdetut-arvot kuukausitasolla?]
                                                                                                                          (let [arvot (if hoitokauden-numero
                                                                                                                                        (get jh-korvaukset (dec hoitokauden-numero))
                                                                                                                                        [])
                                                                                                                                arvot (transduce (comp
                                                                                                                                                   (filter (fn [{:keys [kuukausi]}]
                                                                                                                                                             (kuukausi-kuuluu-maksukauteen? kuukausi maksukausi)))
                                                                                                                                                   (map (fn [m]
                                                                                                                                                          (select-keys m #{:tunnit :aika}))))
                                                                                                                                                 conj
                                                                                                                                                 []
                                                                                                                                                 arvot)]
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
                                                                                                                                       johdetut-arvot))))))}})))
                                         johto-ja-hallintokorvaukset-pohjadata)))
                          {:aseta-tunnit! (partial aseta-maara!
                                                   (fn [{:keys [omanimi osa osan-paikka toimenkuva maksukausi]} hoitokauden-numero valittu-toimenpide]
                                                     (if omanimi
                                                       [:gridit :johto-ja-hallintokorvaukset :johdettu omanimi (first osan-paikka) osa]
                                                       [:gridit :johto-ja-hallintokorvaukset :johdettu toimenkuva maksukausi (first osan-paikka) osa]))
                                                   (fn [{:keys [omanimi osa osan-paikka toimenkuva maksukausi]} hoitokauden-numero valittu-toimenpide]
                                                     (if omanimi
                                                       [:domain :johto-ja-hallintokorvaukset omanimi (dec hoitokauden-numero) (first osan-paikka) osa]
                                                       [:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi (dec hoitokauden-numero) (first osan-paikka) osa])))
                           :aseta-jh-yhteenveto! jh-yhteenvetopaivitys}
                          (merge (reduce (fn [seurannat {:keys [toimenkuva maksukausi hoitokaudet] :as toimenkuva-kuvaus}]
                                           (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)
                                                 data-koskee-ennen-urakkaa? (toimenpide-koskee-ennen-urakkaa? hoitokaudet)]
                                             (merge seurannat
                                                    {(keyword (str "yhteenveto" yksiloiva-nimen-paate "-seuranta")) {:polut [[:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi]
                                                                                                                             [:suodattimet :hoitokauden-numero]]
                                                                                                                     :init (fn [tila]
                                                                                                                             (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :johdettu toimenkuva maksukausi] (vec (repeat (kk-v-toimenkuvan-kuvaukselle toimenkuva-kuvaus) {}))))
                                                                                                                     :aseta (fn [tila jh-korvaukset hoitokauden-numero]
                                                                                                                              (let [korvauksien-index (if data-koskee-ennen-urakkaa?
                                                                                                                                                        0
                                                                                                                                                        (dec hoitokauden-numero))
                                                                                                                                    valitun-vuoden-jh-tunnit (get jh-korvaukset korvauksien-index)
                                                                                                                                    maksukauden-jh-tunnit (filterv (fn [{:keys [kuukausi osa-kuukaudesta]}]
                                                                                                                                                                     (if data-koskee-ennen-urakkaa?
                                                                                                                                                                       (and (= 10 kuukausi) (= 1 osa-kuukaudesta))
                                                                                                                                                                       (kuukausi-kuuluu-maksukauteen? kuukausi maksukausi)))
                                                                                                                                                                   valitun-vuoden-jh-tunnit)
                                                                                                                                    tuntipalkka (get-in maksukauden-jh-tunnit [0 :tuntipalkka])
                                                                                                                                    kk-v (get-in maksukauden-jh-tunnit [0 :kk-v])
                                                                                                                                    tunnit-samoja? (apply = (map :tunnit maksukauden-jh-tunnit))
                                                                                                                                    tunnit (if tunnit-samoja?
                                                                                                                                             (get-in maksukauden-jh-tunnit [0 :tunnit])
                                                                                                                                             vaihtelua-teksti)
                                                                                                                                    yhteensa (if tunnit-samoja?
                                                                                                                                               (* tunnit tuntipalkka)
                                                                                                                                               vaihtelua-teksti)]
                                                                                                                                (update-in tila [:gridit :johto-ja-hallintokorvaukset :yhteenveto toimenkuva maksukausi] merge {:tunnit tunnit
                                                                                                                                                                                                                                :yhteensa (when-not (= 0 yhteensa)
                                                                                                                                                                                                                                            yhteensa)
                                                                                                                                                                                                                                :tuntipalkka tuntipalkka
                                                                                                                                                                                                                                :kk-v kk-v})))}})))
                                         {}
                                         johto-ja-hallintokorvaukset-pohjadata)
                                 (reduce (fn [seurannat index]
                                           (let [nimi (jh-omienrivien-nimi index)]
                                             (merge seurannat
                                                    {(keyword (str "yhteenveto-" nimi "-seuranta")) {:polut [[:domain :johto-ja-hallintokorvaukset nimi]
                                                                                                             [:suodattimet :hoitokauden-numero]]
                                                                                                     :init (fn [tila]
                                                                                                             (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :johdettu nimi] (vec (repeat 12 {}))))
                                                                                                     :aseta (fn [tila jh-korvaukset hoitokauden-numero]
                                                                                                              (let [korvauksien-index (dec hoitokauden-numero)
                                                                                                                    maksukauden-jh-tunnit (get jh-korvaukset korvauksien-index)
                                                                                                                    tuntipalkka (get-in maksukauden-jh-tunnit [0 :tuntipalkka])
                                                                                                                    kk-v (get-in maksukauden-jh-tunnit [0 :kk-v])
                                                                                                                    tunnit-samoja? (apply = (map :tunnit maksukauden-jh-tunnit))
                                                                                                                    tunnit (if tunnit-samoja?
                                                                                                                             (get-in maksukauden-jh-tunnit [0 :tunnit])
                                                                                                                             vaihtelua-teksti)
                                                                                                                    yhteensa (if tunnit-samoja?
                                                                                                                               (* tunnit tuntipalkka)
                                                                                                                               vaihtelua-teksti)]
                                                                                                                (update-in tila [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi] merge {:tunnit tunnit
                                                                                                                                                                                               :yhteensa (when-not (= 0 yhteensa)
                                                                                                                                                                                                           yhteensa)
                                                                                                                                                                                               :tuntipalkka tuntipalkka
                                                                                                                                                                                               :kk-v kk-v})))}})))
                                         {}
                                         (range 1 (inc jh-korvausten-omiariveja-lkm))))))

(def johto-ja-hallintokorvaus-yhteenveto-rajapinta (merge {:otsikot any?
                                                           :yhteensa any?
                                                           :indeksikorjattu any?}
                                                          (reduce (fn [rajapinnat {:keys [toimenkuva maksukausi]}]
                                                                    (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                                                                      (assoc rajapinnat
                                                                             (keyword (str "yhteenveto" yksiloiva-nimen-paate)) any?)))
                                                                  {}
                                                                  johto-ja-hallintokorvaukset-pohjadata)))

(defn johto-ja-hallintokorvaus-yhteenveto-dr []
  (grid/datan-kasittelija tiedot/suunnittelu-kustannussuunnitelma
                          johto-ja-hallintokorvaus-yhteenveto-rajapinta
                          (merge
                            {:otsikot {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :otsikot]]
                                       :haku identity}
                             :yhteensa {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteensa]]
                                        :haku #(vec (concat ["Yhteensä" ""] %))}
                             :indeksikorjattu {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :indeksikorjattu]]
                                               :haku #(vec (concat ["Indeksikorjattu" ""] %))}}
                            (apply merge
                                   (mapv (fn [{:keys [toimenkuva maksukausi kk-v] :as toimenkuva-kuvaus}]
                                           (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)
                                                 toimenkuva-formatoitu (toimenkuva-formatoitu toimenkuva-kuvaus)]
                                             {(keyword (str "yhteenveto" yksiloiva-nimen-paate)) {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto toimenkuva maksukausi]]
                                                                                                  :haku #(vec (concat [toimenkuva-formatoitu kk-v] %))}}))
                                         johto-ja-hallintokorvaukset-pohjadata)))
                          {}
                          (apply merge
                                 {:yhteensa-seuranta {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto]]
                                                      :aseta (fn [tila yhteenvedot]
                                                               (let [yhteensa-arvot (summaa-lehtivektorit (walk/postwalk (fn [x]
                                                                                                                           (if (and (vector? x) (not= hoitokausien-maara-urakassa (count x)))
                                                                                                                             (let [vektorin-koko (count x)]
                                                                                                                               (reduce (fn [valivaihe index]
                                                                                                                                         (if (>= index vektorin-koko)
                                                                                                                                           (conj valivaihe 0)
                                                                                                                                           (conj valivaihe (get x index))))
                                                                                                                                       []
                                                                                                                                       (range hoitokausien-maara-urakassa)))
                                                                                                                             x))
                                                                                                                         yhteenvedot))]
                                                                 (-> tila
                                                                     (assoc-in [:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteensa] yhteensa-arvot)
                                                                     (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset] yhteensa-arvot))))}
                                  :indeksikorjattu-seuranta {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteensa]]
                                                             :aseta (fn [tila yhteensa]
                                                                      (assoc-in tila
                                                                                [:gridit :johto-ja-hallintokorvaukset-yhteenveto :indeksikorjattu]
                                                                                (mapv indeksikorjaa yhteensa)))}}
                                 (mapv (fn [{:keys [toimenkuva maksukausi]}]
                                         (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                                           {(keyword (str "yhteenveto" yksiloiva-nimen-paate "-seuranta")) {:polut [[:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi]]
                                                                                                            :aseta (fn [tila jh-korvaukset]
                                                                                                                     (let [yhteensa-arvot (mapv (fn [hoitokauden-arvot]
                                                                                                                                                  (let [tuntipalkka (get-in hoitokauden-arvot [0 :tuntipalkka])]
                                                                                                                                                    (* (summaa-mapin-arvot hoitokauden-arvot :tunnit)
                                                                                                                                                       tuntipalkka)))
                                                                                                                                                jh-korvaukset)]
                                                                                                                       (assoc-in tila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto toimenkuva maksukausi] yhteensa-arvot)))}}))
                                       johto-ja-hallintokorvaukset-pohjadata))))

(defn paivita-solun-arvo [{:keys [paivitettava-asia arvo solu ajettavat-jarejestykset triggeroi-seuranta?]
                           :or {ajettavat-jarejestykset false triggeroi-seuranta? false}}
                          & args]
  (jarjesta-data ajettavat-jarejestykset
    (triggeroi-seurannat triggeroi-seuranta?
      (case paivitettava-asia
        :aseta-suunnittellut-hankinnat! (apply grid/aseta-rajapinnan-data!
                                               (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                               :aseta-suunnittellut-hankinnat!
                                               arvo
                                               (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                               args)
        :aseta-laskutukseen-perustuvat-hankinnat! (apply grid/aseta-rajapinnan-data!
                                                         (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                         :aseta-laskutukseen-perustuvat-hankinnat!
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
                                           (grid/solun-asia solu :tunniste-rajapinnan-dataan))
        :aseta-erillishankinnat! (apply grid/aseta-rajapinnan-data!
                                        (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                        :aseta-erillishankinnat!
                                        arvo
                                        (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                        args)
        :aseta-erillishankinnat-yhteenveto! (apply grid/aseta-rajapinnan-data!
                                                   (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                   :aseta-erillishankinnat-yhteenveto!
                                                   arvo
                                                   (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                                   args)
        :aseta-tunnit! (apply grid/aseta-rajapinnan-data!
                              (grid/osien-yhteinen-asia solu :datan-kasittelija)
                              :aseta-tunnit!
                              arvo
                              (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                              args)
        :aseta-jh-yhteenveto! (apply grid/aseta-rajapinnan-data!
                                     (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                     :aseta-jh-yhteenveto!
                                     arvo
                                     (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                     args)
        :aseta-toimistokulut! (apply grid/aseta-rajapinnan-data!
                                     (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                     :aseta-toimistokulut!
                                     arvo
                                     (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                     args)
        :aseta-toimistokulut-yhteenveto! (apply grid/aseta-rajapinnan-data!
                                                (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                :aseta-toimistokulut-yhteenveto!
                                                arvo
                                                (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                                args)
        :aseta-hoidonjohtopalkkio! (apply grid/aseta-rajapinnan-data!
                                          (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                          :aseta-hoidonjohtopalkkio!
                                          arvo
                                          (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                          args)
        :aseta-hoidonjohtopalkkio-yhteenveto! (apply grid/aseta-rajapinnan-data!
                                                     (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                     :aseta-hoidonjohtopalkkio-yhteenveto!
                                                     arvo
                                                     (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                                     args)
        :aseta-tilaajan-varaukset! (apply grid/aseta-rajapinnan-data!
                                          (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                          :aseta-tilaajan-varaukset!
                                          arvo
                                          (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                          args)
        :aseta-tilaajan-varaukset-yhteenveto! (apply grid/aseta-rajapinnan-data!
                                                     (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                     :aseta-tilaajan-varaukset-yhteenveto!
                                                     arvo
                                                     (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                                     args)))))

(defn triggeroi-seuranta! [solu seurannan-nimi]
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
      (if rivi
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
  ([solu auki? dom-id polku-dataan
    {:keys [aukeamis-polku sulkemis-polku]
     :or {aukeamis-polku [:.. :.. 1]
          sulkemis-polku [:.. :.. 1]}}]
   (let [alin-taulukko? (= dom-id alimman-taulukon-id)]
     (if auki?
       (do (grid/nayta! (grid/osa-polusta solu aukeamis-polku))
           (paivita-raidat! (grid/osa-polusta (grid/root solu) polku-dataan))
           (r/flush)
           (when alin-taulukko?
             (r/after-render
               (fn []
                 (.scrollIntoView (dom/getElement dom-id) #js {"block" "end" "inline" "nearest" "behavior" "smooth"})))))
       (do (grid/piillota! (grid/osa-polusta solu sulkemis-polku))
           (paivita-raidat! (grid/osa-polusta (grid/root solu) polku-dataan))
           (r/flush))))))


(defrecord TaulukoidenVakioarvot [])
(defrecord FiltereidenAloitusarvot [])
(defrecord TallennaHankintojenArvot [tallennettava-asia hoitokauden-numero tunnisteet])
(defrecord TallennaHankintojenArvotOnnistui [vastaus])
(defrecord TallennaHankintojenArvotEpaonnistui [vastaus])
(defrecord TallennaKustannusarvoitu [tallennettava-asia tunnisteet])
(defrecord TallennaKustannusarvoituOnnistui [vastaus])
(defrecord TallennaKustannusarvoituEpaonnistui [vastaus])
(defrecord TallennaJohtoJaHallintokorvaukset [tallennettava-asia tunnisteet])
(defrecord TallennaJohtoJaHallintokorvauksetOnnistui [vastaus])
(defrecord TallennaJohtoJaHallintokorvauksetEpaonnistui [vastaus])
(defrecord TallennaJaPaivitaTavoiteSekaKattohinta [])
(defrecord TallennaJaPaivitaTavoiteSekaKattohintaOnnistui [vastaus])
(defrecord TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui [vastaus])
(defrecord MuutaOmanJohtoJaHallintokorvauksenArvoa [nimi sarake arvo])
(defrecord Hoitokausi [])
(defrecord YleisSuodatinArvot [])
(defrecord HaeIndeksitOnnistui [vastaus])
(defrecord HaeIndeksitEpaonnistui [vastaus])
(defrecord Oikeudet [])
(defrecord HaeKustannussuunnitelma [])
(defrecord HaeHankintakustannuksetOnnistui [vastaus])
(defrecord HaeHankintakustannuksetEpaonnistui [vastaus])
(defrecord MaksukausiValittu [])


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

(extend-protocol tuck/Event
  TaulukoidenVakioarvot
  (process-event [_ app]
    (let [tilan-hallinnollisten-nimet {:erillishankinnat "Erillishankinnat"
                                       :johto-ja-hallintokorvaus "Johto- ja hallintokorvaus"
                                       :toimistokulut "Toimistokulut"
                                       :hoidonjohtopalkkio "Hoidonjohtopalkkio"
                                       :tilaajan-varaukset "Tilaajan varaukset"}
          kuluva-hoitokauden-numero (get-in app [:domain :kuluva-hoitokausi :hoitokauden-numero])
          viimeinen-vuosi? (= 5 kuluva-hoitokauden-numero)
          app (reduce (fn [app [h-avain _]]
                        (assoc-in app [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet h-avain :nimi] (get tilan-hallinnollisten-nimet h-avain)))
                      app
                      hallinnollisten-idt)]
      (-> app
          (assoc-in [:gridit :suunnitelmien-tila :otsikot] {:kuluva-hoitovuosi (str (when-not viimeinen-vuosi?
                                                                                      kuluva-hoitokauden-numero)
                                                                                    ". vuosi")
                                                            :seuraava-hoitovuosi (str (if viimeinen-vuosi?
                                                                                        kuluva-hoitokauden-numero
                                                                                        (inc kuluva-hoitokauden-numero))
                                                                                      ". vuosi")})
          (assoc-in [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet :nimi] "Hallinnolliset toimenteet")
          (assoc-in [:gridit :laskutukseen-perustuvat-hankinnat] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                                                  :yhteenveto {:nimi "Määrämitattavat"}
                                                                  :yhteensa {:nimi "Yhteensä"}
                                                                  :kuukausitasolla? false})
          (assoc-in [:gridit :rahavaraukset] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                              :yhteensa {:nimi "Yhteensä"}})
          (assoc-in [:gridit :erillishankinnat] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                                 :yhteenveto {:nimi "Erillishankinnat"}
                                                 :yhteensa {:nimi "Yhteensä"}
                                                 :kuukausitasolla? false})
          (assoc-in [:gridit :johto-ja-hallintokorvaukset] {:otsikot {:toimenkuva "Toimenkuva" :tunnit "Tunnit/kk, h" :tuntipalkka "Tuntipalkka, €" :yhteensa "Yhteensä/kk" :kk-v "kk/v"}
                                                            :yhteenveto (reduce (fn [yhteenveto-otsikot {:keys [toimenkuva maksukausi] :as toimenkuva-kuvaus}]
                                                                                  (assoc-in yhteenveto-otsikot [toimenkuva maksukausi :toimenkuva] (toimenkuva-formatoitu toimenkuva-kuvaus)))
                                                                                {}
                                                                                johto-ja-hallintokorvaukset-pohjadata)})
          (assoc-in [:gridit :johto-ja-hallintokorvaukset-yhteenveto] {:otsikot {:toimenkuva "Toimenkuva" :kk-v "kk/v" :hoitovuosi-1 "1.vuosi/€" :hoitovuosi-2 "2.vuosi/€" :hoitovuosi-3 "3.vuosi/€" :hoitovuosi-4 "4.vuosi/€" :hoitovuosi-5 "5.vuosi/€"}})
          (assoc-in [:gridit :suunnittellut-hankinnat] {:otsikot {:nimi "Kiinteät" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                                        :yhteensa {:nimi "Yhteensä"}})
          (assoc-in [:gridit :toimistokulut] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                              :yhteenveto {:nimi "Toimistokulut, Pientarvikevarasto"}
                                              :yhteensa {:nimi "Yhteensä"}
                                              :kuukausitasolla? false})
          (assoc-in [:gridit :hoidonjohtopalkkio] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                                   :yhteenveto {:nimi "Hoidonjohtopalkkio"}
                                                   :yhteensa {:nimi "Yhteensä"}
                                                   :kuukausitasolla? false})
          (assoc-in [:gridit :tilaajan-varaukset] {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
                                                   :yhteenveto {:nimi "Tilaajan varaukset"}
                                                   :yhteensa {:nimi "Yhteensä"}
                                                   :kuukausitasolla? false}))))
  FiltereidenAloitusarvot
  (process-event [_ app]
    (-> app
        (assoc-in [:suodattimet :hankinnat :toimenpide] :talvihoito)))
  Hoitokausi
  (process-event [_ app]
    (assoc-in app [:domain :kuluva-hoitokausi] (kuluva-hoitokausi)))
  YleisSuodatinArvot
  (process-event [_ app]
    (-> app
        (assoc-in [:suodattimet :hoitokauden-numero] (get-in app [:domain :kuluva-hoitokausi :hoitokauden-numero]))
        (assoc-in [:suodattimet :kopioidaan-tuleville-vuosille?] true)))
  HaeIndeksitOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc-in app [:domain :indeksit] vastaus))
  HaeIndeksitEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Indeksien haku epäonnistui!" :warning viesti/viestin-nayttoaika-pitka)
    app)
  Oikeudet
  (process-event [_ app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)
          kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-kustannussuunnittelu urakka-id)]
      (assoc-in app [:domain :kirjoitusoikeus?] kirjoitusoikeus?)))
  HaeKustannussuunnitelma
  (process-event [_ app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)]
      (varmista-kasittelyjen-jarjestys
        (tuck-apurit/post! app :budjettisuunnittelun-indeksit
                           {:urakka-id urakka-id}
                           {:onnistui ->HaeIndeksitOnnistui
                            :epaonnistui ->HaeIndeksitEpaonnistui
                            :paasta-virhe-lapi? true})
        (tuck-apurit/post! app :budjetoidut-tyot
                           {:urakka-id urakka-id}
                           {:onnistui ->HaeHankintakustannuksetOnnistui
                            :epaonnistui ->HaeHankintakustannuksetEpaonnistui
                            :paasta-virhe-lapi? true}))
      app))
  HaeHankintakustannuksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (let [{urakan-aloituspvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)
          pohjadata (urakan-ajat)
          pohjadatan-taydennys (fn [data-backilta filter-fn rikastamis-fn]
                                 (let [sort-fn (juxt :vuosi :kuukausi)
                                       data (loop [[pd & pd-loput] (sort-by sort-fn pohjadata)
                                                   muodostettu []
                                                   i 0]
                                              (cond
                                                (nil? pd) muodostettu
                                                (filter-fn (:vuosi pd) (:kuukausi pd)) (let [tarkasteltava-data (get data-backilta i)
                                                                                             loydetty-data? (and (= (:vuosi pd) (:vuosi tarkasteltava-data))
                                                                                                                 (= (:kuukausi pd) (:kuukausi tarkasteltava-data)))]
                                                                                         (recur pd-loput
                                                                                                (conj muodostettu
                                                                                                      (if loydetty-data?
                                                                                                        tarkasteltava-data
                                                                                                        pd))
                                                                                                (if loydetty-data?
                                                                                                  (inc i)
                                                                                                  i)))
                                                :else (recur pd-loput
                                                             muodostettu
                                                             i)))]
                                   (map rikastamis-fn data)))
          pohjadatan-taydennys-toimenpiteittain (fn [data toimenpiteet rikastamis-fn]
                                                  (reduce (fn [data-toimenpiteittain toimenpide]
                                                            (let [sort-fn (juxt :vuosi :kuukausi)
                                                                  data-backilta (vec (sort-by sort-fn (filter #(= (:toimenpide-avain %) toimenpide) data)))
                                                                  data (pohjadatan-taydennys data-backilta (constantly true) rikastamis-fn)]
                                                              (merge data-toimenpiteittain
                                                                     {toimenpide data})))
                                                          {}
                                                          toimenpiteet))
          hankinnat (:kiinteahintaiset-tyot vastaus)
          hankinnat-laskutukseen-perustuen (filter #(and (= (:tyyppi %) "laskutettava-tyo")
                                                         (nil? (:haettu-asia %)))
                                                   (:kustannusarvioidut-tyot vastaus))
          toimenpiteet-joilla-laskutukseen-perustuvia-suunnitelmia (into #{} (distinct (map :toimenpide-avain hankinnat-laskutukseen-perustuen)))
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
          hankinnat-laskutukseen-perustuen (into {}
                                                 (map (fn [[toimenpide hankinnat]]
                                                        [toimenpide (vec (vals (sort-by #(-> % key first)
                                                                                        (fn [aika-1 aika-2]
                                                                                          (pvm/ennen? aika-1 aika-2))
                                                                                        (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                                                  hankinnat))))])
                                                      hankinnat-laskutukseen-perustuen-toimenpiteittain))
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

          maara-kk-taulukon-data (fn [kustannusarvioidut-tyot haettu-asia]
                                   (let [asia-kannasta (filter (fn [tyo]
                                                                 (= haettu-asia (:haettu-asia tyo)))
                                                               kustannusarvioidut-tyot)]
                                     (pohjadatan-taydennys (vec (sort-by (juxt :vuosi :kuukausi) asia-kannasta))
                                                           (constantly true)
                                                           (fn [{:keys [vuosi kuukausi summa] :as data}]
                                                             (-> data
                                                                 (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                        :maara summa)
                                                                 (dissoc :summa))))))
          erillishankinnat (maara-kk-taulukon-data hoidon-johto-kustannukset :erillishankinnat)
          jh-toimistokulut (maara-kk-taulukon-data hoidon-johto-kustannukset :toimistokulut)
          johtopalkkio (maara-kk-taulukon-data hoidon-johto-kustannukset :hoidonjohtopalkkio)
          tilaajan-varaukset (maara-kk-taulukon-data hoidon-johto-kustannukset :tilaajan-varaukset)

          omat-jh-korvaukset (vec (sort-by first
                                           (group-by :toimenkuva (get-in vastaus [:johto-ja-hallintokorvaukset :omat]))))
          vapaat-omien-toimekuvien-idt (clj-set/difference (into #{} (get-in vastaus [:johto-ja-hallintokorvaukset :omien-idt]))
                                                           (into #{} (distinct (map :toimenkuva-id (get-in vastaus [:johto-ja-hallintokorvaukset :omat])))))
          _ (when (> (count omat-jh-korvaukset) 2)
              (modal/nayta! {:otsikko "Omia toimenkuvia liikaa!"}
                            [:div
                             [:span (str "Löytyi seuraavat omat toimenkuvat kannasta vaikka maksimi määrä on " jh-korvausten-omiariveja-lkm ". Ota yhteyttä Harja-tiimiin.")]
                             [:ul
                              (doall (for [[toimenkuva _] omat-jh-korvaukset]
                                       ^{:key toimenkuva}
                                       [:li (str toimenkuva)]))]]))
          jh-korvaukset (merge (reduce (fn [korvaukset {:keys [toimenkuva kk-v maksukausi hoitokaudet]}]
                                         (let [asia-kannasta (reverse (sort-by :osa-kuukaudesta (filter (fn [jh-korvaus]
                                                                                                          (and (= (:toimenkuva jh-korvaus) toimenkuva)
                                                                                                               (= (:maksukausi jh-korvaus) maksukausi)))
                                                                                                        (get-in vastaus [:johto-ja-hallintokorvaukset :vakiot]))))
                                               data-koskee-ennen-urakkaa? (toimenpide-koskee-ennen-urakkaa? hoitokaudet)
                                               taytetty-jh-data (if data-koskee-ennen-urakkaa?
                                                                  (let [kannasta (filterv :ennen-urakkaa asia-kannasta)]
                                                                    (if (empty? kannasta)
                                                                      (let [arvot {:aika (pvm/luo-pvm (pvm/vuosi urakan-aloituspvm) 9 15)
                                                                                   :vuosi (pvm/vuosi urakan-aloituspvm)
                                                                                   :kk-v kk-v
                                                                                   :osa-kuukaudesta 1
                                                                                   :tunnit 0
                                                                                   :tuntipalkka 0
                                                                                   :kuukausi 10}
                                                                            kokonaiset (vec (repeat (js/Math.floor kk-v) arvot))
                                                                            osittainen? (not= 0 (- kk-v (count kokonaiset)))]
                                                                        (if osittainen?
                                                                          (conj kokonaiset (assoc arvot :osa-kuukaudesta (- kk-v (count kokonaiset))))
                                                                          kokonaiset))
                                                                      kannasta))
                                                                  (pohjadatan-taydennys (vec (sort-by (juxt :vuosi :kuukausi) asia-kannasta))
                                                                                        (fn [vuosi kuukausi]
                                                                                          (cond
                                                                                            (= maksukausi :kesa) (<= 5 kuukausi 9)
                                                                                            (= maksukausi :talvi) (or (<= 1 kuukausi 4)
                                                                                                                      (<= 10 kuukausi 12))
                                                                                            (= toimenkuva "harjoittelija") (<= 5 kuukausi 8)
                                                                                            (= toimenkuva "viherhoidosta vastaava henkilö") (<= 4 kuukausi 8)
                                                                                            :else true))
                                                                                        (fn [{:keys [vuosi kuukausi tunnit tuntipalkka] :as data}]
                                                                                          (-> data
                                                                                              (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                                                     :tunnit (or tunnit 0)
                                                                                                     :tuntipalkka (or tuntipalkka 0)
                                                                                                     :kk-v kk-v)
                                                                                              (select-keys #{:aika :kk-v :tunnit :tuntipalkka :kuukausi :vuosi :osa-kuukaudesta})))))]
                                           (if data-koskee-ennen-urakkaa?
                                             (assoc korvaukset toimenkuva {maksukausi [taytetty-jh-data]})
                                             (update korvaukset toimenkuva (fn [maksukausien-arvot]
                                                                             (assoc maksukausien-arvot maksukausi (vec (vals (sort-by #(-> % key first)
                                                                                                                                      (fn [aika-1 aika-2]
                                                                                                                                        (pvm/ennen? aika-1 aika-2))
                                                                                                                                      (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                                                                                                taytetty-jh-data))))))))))
                                       {}
                                       johto-ja-hallintokorvaukset-pohjadata)
                               (first (reduce (fn [[omat-korvaukset vapaat-omien-toimekuvien-idt] jarjestysnumero]
                                                (let [omanimi (jh-omienrivien-nimi jarjestysnumero)
                                                      asia-kannasta (get-in omat-jh-korvaukset [(dec jarjestysnumero) 1])
                                                      toimenkuva-id (or (get-in asia-kannasta [0 :toimenkuva-id]) (first vapaat-omien-toimekuvien-idt))
                                                      taytetty-jh-data (pohjadatan-taydennys (vec (sort-by (juxt :vuosi :kuukausi) asia-kannasta))
                                                                                             (constantly true)
                                                                                             (fn [{:keys [vuosi kuukausi tunnit tuntipalkka] :as data}]
                                                                                               (-> data
                                                                                                   (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                                                          :toimenkuva-id toimenkuva-id
                                                                                                          :tunnit (or tunnit 0)
                                                                                                          :tuntipalkka (or tuntipalkka 0))
                                                                                                   (select-keys #{:aika :toimenkuva-id :tunnit :tuntipalkka :kuukausi :vuosi :osa-kuukaudesta}))))]
                                                  [(assoc omat-korvaukset omanimi (vec (vals (sort-by #(-> % key first)
                                                                                                      (fn [aika-1 aika-2]
                                                                                                        (pvm/ennen? aika-1 aika-2))
                                                                                                      (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                                                                taytetty-jh-data)))))
                                                   (disj vapaat-omien-toimekuvien-idt toimenkuva-id)]))
                                              [{} vapaat-omien-toimekuvien-idt]
                                              (range 1 (inc jh-korvausten-omiariveja-lkm)))))
          kuluva-hoitokauden-numero (get-in app [:domain :kuluva-hoitokausi :hoitokauden-numero])
          app (reduce (fn [app jarjestysnumero]
                        (let [nimi (jh-omienrivien-nimi jarjestysnumero)
                              tayttamattomat-rivit (remove #(and (= 0 (:tunnit %)) (= 0 (:tuntipalkka %)))
                                                           (get-in jh-korvaukset [nimi kuluva-hoitokauden-numero]))
                              maksukausi (case (count tayttamattomat-rivit)
                                           5 :kesa
                                           7 :talvi
                                           :molemmat)]
                          (assoc-in app [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :maksukausi] maksukausi)))
                      app
                      (range 1 (inc jh-korvausten-omiariveja-lkm)))

          hoidonjohto-jarjestys-fn (fn [data]
                                     (vec
                                       (sort-by #(-> % first :aika)
                                                (vals
                                                  (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                            data)))))
          erillishankinnat-hoitokausittain (hoidonjohto-jarjestys-fn erillishankinnat)
          toimistokulut-hoitokausittain (hoidonjohto-jarjestys-fn jh-toimistokulut)
          hoidonjohtopalkkio-hoitokausittain (hoidonjohto-jarjestys-fn johtopalkkio)
          tilaajan-varaukset-hoitokausittain (hoidonjohto-jarjestys-fn tilaajan-varaukset)]
      (-> app
          (assoc-in [:domain :suunnittellut-hankinnat] hankinnat-hoitokausille)
          (assoc-in [:domain :laskutukseen-perustuvat-hankinnat] hankinnat-laskutukseen-perustuen)
          (assoc-in [:domain :rahavaraukset] rahavaraukset-hoitokausille)
          (assoc-in [:domain :erillishankinnat] erillishankinnat-hoitokausittain)
          (assoc-in [:domain :johto-ja-hallintokorvaukset] jh-korvaukset)
          (assoc-in [:domain :toimistokulut] toimistokulut-hoitokausittain)
          (assoc-in [:domain :hoidonjohtopalkkio] hoidonjohtopalkkio-hoitokausittain)
          (assoc-in [:domain :tilaajan-varaukset] tilaajan-varaukset-hoitokausittain)
          (assoc-in [:yhteenvedot :hankintakustannukset :summat :suunnitellut-hankinnat] (reduce (fn [summat [toimenpide summat-hoitokausittain]]
                                                                                                   (assoc summat toimenpide (mapv (fn [summat-kuukausittain]
                                                                                                                                    (reduce #(+ %1 (:maara %2)) 0 summat-kuukausittain))
                                                                                                                                  summat-hoitokausittain)))
                                                                                                 {}
                                                                                                 hankinnat-hoitokausille))
          (assoc-in [:yhteenvedot :hankintakustannukset :summat :laskutukseen-perustuvat-hankinnat] (reduce (fn [summat [toimenpide summat-hoitokausittain]]
                                                                                                              (assoc summat toimenpide (mapv (fn [summat-kuukausittain]
                                                                                                                                               (reduce #(+ %1 (:maara %2)) 0 summat-kuukausittain))
                                                                                                                                             summat-hoitokausittain)))
                                                                                                            {}
                                                                                                            hankinnat-laskutukseen-perustuen))
          (assoc-in [:yhteenvedot :hankintakustannukset :summat :rahavaraukset] (reduce (fn [summat [toimenpide toimenpiteen-rahavaraukset]]
                                                                                          (update summat
                                                                                                  toimenpide
                                                                                                  (fn [toimenpiteen-summat]
                                                                                                    (reduce (fn [toimenpiteen-summat [tyyppi maarat-hoitokausittain]]
                                                                                                              (update toimenpiteen-summat
                                                                                                                      tyyppi
                                                                                                                      (fn [summat-hoitokausittain]
                                                                                                                        (mapv +
                                                                                                                              (or summat-hoitokausittain (repeat 5 0))
                                                                                                                              (map (fn [hoitokauden-maarat]
                                                                                                                                     (reduce #(+ %1 (:maara %2)) 0 hoitokauden-maarat))
                                                                                                                                   maarat-hoitokausittain)))))
                                                                                                            toimenpiteen-summat
                                                                                                            toimenpiteen-rahavaraukset))))
                                                                                        {}
                                                                                        rahavaraukset-hoitokausille))
          (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :erillishankinnat] (mapv #(summaa-mapin-arvot % :maara) erillishankinnat-hoitokausittain))
          (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :toimistokulut] (mapv #(summaa-mapin-arvot % :maara) toimistokulut-hoitokausittain))
          (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio] (mapv #(summaa-mapin-arvot % :maara) hoidonjohtopalkkio-hoitokausittain))
          (assoc-in [:suodattimet :hankinnat :laskutukseen-perustuen-valinta] toimenpiteet-joilla-laskutukseen-perustuvia-suunnitelmia)
          (assoc :kantahaku-valmis? true))))
  HaeHankintakustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;; TODO
    (viesti/nayta! "Hankintakustannusten haku epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
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
          g-sh (get-in app [:gridit :suunnittellut-hankinnat :grid])
          g-hlp (get-in app [:gridit :laskutukseen-perustuvat-hankinnat :grid])
          nakyvyydet-fn! (fn [g]
                           (doseq [otsikko-datasisalto (grid/hae-grid (grid/get-in-grid g [::g-pohjat/data]) :lapset)]
                             (grid/paivita-grid! (grid/get-in-grid otsikko-datasisalto [:harja.views.urakka.suunnittelu.kustannussuunnitelma/data-sisalto])
                                                 :lapset
                                                 (fn [rivit]
                                                   (mapv (fn [rivi]
                                                           (if (-> rivi (grid/get-in-grid [0]) grid/solun-arvo pvm/kuukausi piillotetaan?)
                                                             (grid/piillota! rivi)
                                                             (grid/nayta! rivi))
                                                           rivi)
                                                         rivit)))))]
      (nakyvyydet-fn! g-sh)
      (nakyvyydet-fn! g-hlp)
      app))
  MuutaOmanJohtoJaHallintokorvauksenArvoa
  (process-event [{:keys [nimi sarake arvo]} app]
    (let [hoitokauden-numero (get-in app [:domain :kuluva-hoitokausi :hoitokauden-numero])]
      (assoc-in app [:domain :johto-ja-hallintokorvaukset nimi (dec hoitokauden-numero) sarake] arvo)))
  TallennaHankintojenArvot
  (process-event [{:keys [tallennettava-asia hoitokauden-numero tunnisteet]} app]
    (let [{urakka-id :id} (:urakka @tiedot/yleiset)
          post-kutsu (case tallennettava-asia
                       :hankintakustannus :tallenna-kiinteahintaiset-tyot
                       :laskutukseen-perustuva-hankinta :tallenna-kustannusarvioitu-tyo)
          valittu-toimenpide (get-in app [:suodattimet :hankinnat :toimenpide])
          kopioidaan-tuleville-vuosille? (get-in app [:suodattimet :hankinnat :kopioidaan-tuleville-vuosille?])
          paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                              (range hoitokauden-numero 6)
                                              [hoitokauden-numero])
          summa (case tallennettava-asia
                  :hankintakustannus (get-in app [:domain :suunnittellut-hankinnat valittu-toimenpide hoitokauden-numero (get-in tunnisteet [0 :osan-paikka 0]) :maara])
                  :laskutukseen-perustuva-hankinta (get-in app [:domain :laskutukseen-perustuvat-hankinnat valittu-toimenpide hoitokauden-numero (get-in tunnisteet [0 :osan-paikka 0]) :maara]))
          ajat (vec (mapcat (fn [{:keys [osan-paikka]}]
                              (mapv (fn [hoitokauden-numero]
                                      (let [polun-osa (case tallennettava-asia
                                                        :hankintakustannus :suunnittellut-hankinnat
                                                        :laskutukseen-perustuva-hankinta :laskutukseen-perustuvat-hankinnat)]
                                        (select-keys (get-in app [:domain polun-osa valittu-toimenpide (dec hoitokauden-numero) (first osan-paikka)])
                                                     #{:vuosi :kuukausi})))
                                    paivitettavat-hoitokauden-numerot))
                            tunnisteet))
          lahetettava-data (case tallennettava-asia
                             :hankintakustannus {:urakka-id urakka-id
                                                 :toimenpide-avain valittu-toimenpide
                                                 :summa summa
                                                 :ajat ajat}
                             :laskutukseen-perustuva-hankinta {:urakka-id urakka-id
                                                               :toimenpide-avain valittu-toimenpide
                                                               :tallennettava-asia :toimenpiteen-maaramitattavat-tyot
                                                               :summa summa
                                                               :ajat ajat})]
      (tuck-apurit/post! app
                         post-kutsu
                         lahetettava-data
                         {:onnistui ->TallennaHankintojenArvotOnnistui
                          :epaonnistui ->TallennaHankintojenArvotEpaonnistui
                          :paasta-virhe-lapi? true})))
  TallennaHankintojenArvotOnnistui
  (process-event [{:keys [vastaus]} app]
    app)
  TallennaHankintojenArvotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
  TallennaKustannusarvoitu
  (process-event [{:keys [tallennettava-asia tunnisteet]} app]
    (let [{urakka-id :id} (:urakka @tiedot/yleiset)
          post-kutsu :tallenna-kustannusarvioitu-tyo
          hoitokauden-numero (get-in app [:suodattimet :hoitokauden-numero])
          valittu-toimenpide (get-in app [:suodattimet :hankinnat :toimenpide])
          kopioidaan-tuleville-vuosille? (get-in app [:suodattimet :kopioidaan-tuleville-vuosille?])
          paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                              (range hoitokauden-numero 6)
                                              [hoitokauden-numero])
          summa (case tallennettava-asia
                  :kolmansien-osapuolten-aiheuttamat-vahingot (get-in app [:domain :rahavaraukset valittu-toimenpide "vahinkojen-korjaukset" (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])
                  :akilliset-hoitotyot (get-in app [:domain :rahavaraukset valittu-toimenpide "akillinen-hoitotyo" (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])
                  :rahavaraus-lupaukseen-1 (get-in app [:domain :rahavaraukset valittu-toimenpide "muut-rahavaraukset" (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])
                  :erillishankinnat (get-in app [:domain :erillishankinnat (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])
                  :toimistokulut (get-in app [:domain :toimistokulut (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])
                  :hoidonjohtopalkkio (get-in app [:domain :hoidonjohtopalkkio (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])
                  :tilaajan-varaukset (get-in app [:domain :tilaajan-varaukset (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara]))
          ajat (vec (mapcat (fn [{:keys [osan-paikka]}]
                              (mapv (fn [hoitokauden-numero]
                                      (let [polku (case tallennettava-asia
                                                    :kolmansien-osapuolten-aiheuttamat-vahingot [:domain :rahavaraukset valittu-toimenpide "vahinkojen-korjaukset" (dec hoitokauden-numero) (first osan-paikka)]
                                                    :akilliset-hoitotyot [:domain :rahavaraukset valittu-toimenpide "akillinen-hoitotyo" (dec hoitokauden-numero) (first osan-paikka)]
                                                    :rahavaraus-lupaukseen-1 [:domain :rahavaraukset valittu-toimenpide "muut-rahavaraukset" (dec hoitokauden-numero) (first osan-paikka)]
                                                    :erillishankinnat [:domain :erillishankinnat (dec hoitokauden-numero) (first osan-paikka)]
                                                    :toimistokulut [:domain :toimistokulut (dec hoitokauden-numero) (first osan-paikka)]
                                                    :hoidonjohtopalkkio [:domain :hoidonjohtopalkkio (dec hoitokauden-numero) (first osan-paikka)]
                                                    :tilaajan-varaukset [:domain :tilaajan-varaukset (dec hoitokauden-numero) (first osan-paikka)])]
                                        (select-keys (get-in app polku)
                                                     #{:vuosi :kuukausi})))
                                    paivitettavat-hoitokauden-numerot))
                            tunnisteet))

          lahetettava-data (case tallennettava-asia
                             (:kolmansien-osapuolten-aiheuttamat-vahingot
                               :akilliset-hoitotyot
                               :rahavaraus-lupaukseen-1) {:urakka-id urakka-id
                                                          :toimenpide-avain valittu-toimenpide
                                                          :tallennettava-asia tallennettava-asia
                                                          :summa summa
                                                          :ajat ajat}
                             (:erillishankinnat
                               :toimistokulut
                               :hoidonjohtopalkkio
                               :tilaajan-varaukset) {:urakka-id urakka-id
                                                     :toimenpide-avain :mhu-johto
                                                     :tallennettava-asia tallennettava-asia
                                                     :summa summa
                                                     :ajat ajat})]
      (tuck-apurit/post! app
                         post-kutsu
                         lahetettava-data
                         {:onnistui ->TallennaKustannusarvoituOnnistui
                          :epaonnistui ->TallennaKustannusarvoituEpaonnistui
                          :paasta-virhe-lapi? true})))
  TallennaKustannusarvoituOnnistui
  (process-event [{:keys [vastaus]} app]
    app)
  TallennaKustannusarvoituEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app)
  TallennaJohtoJaHallintokorvaukset
  (process-event [{:keys [tallennettava-asia tunnisteet]} app]
    (let [{urakka-id :id} (:urakka @tiedot/yleiset)
          post-kutsu :tallenna-johto-ja-hallintokorvaukset
          hoitokauden-numero (get-in app [:suodattimet :hoitokauden-numero])
          kopioidaan-tuleville-vuosille? (get-in app [:suodattimet :kopioidaan-tuleville-vuosille?])
          paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                              (range hoitokauden-numero 6)
                                              [hoitokauden-numero])
          jhk-tiedot (vec (mapcat (fn [{:keys [osan-paikka data-koskee-ennen-urakkaa?]}]
                                    (if data-koskee-ennen-urakkaa?
                                      (mapv (fn [m]
                                              (select-keys m
                                                           #{:vuosi :kuukausi :osa-kuukaudesta :tunnit :tuntipalkka}))
                                            (get-in app [:domain :johto-ja-hallintokorvaukset (get-in tunnisteet [0 :toimenkuva]) (get-in tunnisteet [0 :maksukausi]) (dec hoitokauden-numero)]))
                                      (mapv (fn [hoitokauden-numero]
                                              (let [tiedot (select-keys (get-in app [:domain :johto-ja-hallintokorvaukset (get-in tunnisteet [0 :toimenkuva]) (get-in tunnisteet [0 :maksukausi]) (dec hoitokauden-numero) (first osan-paikka)])
                                                                        #{:vuosi :kuukausi :osa-kuukaudesta :tunnit :tuntipalkka})]
                                                (if (contains? tiedot :osa-kuukaudesta)
                                                  tiedot
                                                  (assoc tiedot :osa-kuukaudesta 1))))
                                            paivitettavat-hoitokauden-numerot)))
                                  tunnisteet))

          lahetettava-data {:urakka-id urakka-id
                            :toimenkuva (get-in tunnisteet [0 :toimenkuva])
                            :ennen-urakkaa? (nil? (get-in tunnisteet [0 :maksukausi]))
                            :jhk-tiedot jhk-tiedot}]
      (tuck-apurit/post! app
                         post-kutsu
                         lahetettava-data
                         {:onnistui ->TallennaKustannusarvoituOnnistui
                          :epaonnistui ->TallennaKustannusarvoituEpaonnistui
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
  (process-event [_ {:keys [yhteenvedot] :as app}]
    (let [kattohinnan-kerroin 1.1
          yhteenvedot (tavoitehinnan-summaus yhteenvedot)
          {urakka-id :id} (:urakka @tiedot/yleiset)

          lahetettava-data {:urakka-id urakka-id
                            :tavoitteet (vec (map-indexed (fn [index summa]
                                                            {:hoitokausi (inc index)
                                                             :tavoitehinta summa
                                                             :kattohinta (* summa kattohinnan-kerroin)})
                                                          yhteenvedot))}]
      (tuck-apurit/post! app
                         :tallenna-budjettitavoite
                         lahetettava-data
                         {:onnistui ->TallennaJaPaivitaTavoiteSekaKattohintaOnnistui
                          :epaonnistui ->TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui
                          :paasta-virhe-lapi? true})))
  TallennaJaPaivitaTavoiteSekaKattohintaOnnistui
  (process-event [_ app]
    app)
  TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Tallennus epäonnistui..."
                   :warning viesti/viestin-nayttoaika-pitka)
    app))