(ns harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma
  (:require [clojure.string :as clj-str]
            [clojure.set :as clj-set]
            [clojure.walk :as walk]
            [cljs.core.async :as async :refer [<! >! chan timeout]]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tiedot]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log warn]]
            [harja.ui.viesti :as viesti]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.palvelut.budjettisuunnittelu :as bs]
            [harja.domain.mhu :as mhu]
            [cljs.spec.alpha :as s]
            [harja.tyokalut.regex :as re]
            [goog.dom :as dom]
            [harja.ui.modal :as modal]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [harja.ui.grid.protokollat :as grid-protokolla]
            [harja.fmt :as fmt])
  (:require-macros [harja.tyokalut.tuck :refer [varmista-kasittelyjen-jarjestys]]
                   [harja.ui.taulukko.grid :refer [jarjesta-data triggeroi-seurannat]]
                   [cljs.core.async.macros :refer [go go-loop]]))

;; Tuck e!
(def ^{:dynamic true
       :doc "Käytännössä modaalin nappeja varten. Eli, jos tuckin process-event:issä käsitellään
            jokin eventti, joka aiheuttaa modaalin näkymisen ja tästä modaalista halutaan nakata uusi
            eventti voi olla mahdollista, että e!:tä tarvii tässäkin ns:ssa."}
  *e!* nil)


;; #### Spec ####

(s/def ::maara #(re-matches (re-pattern (re/positiivinen-numero-re)) (str %)))
(s/def ::indeksikorjattu #(re-matches (re-pattern (re/positiivinen-numero-re)) (str %)))
(s/def ::aika #(pvm/pvm? %))

(s/def ::hoidonjohtopalkkio (s/keys :req-un [::aika]
                              :opt-un [::maara]))
(s/def ::hoidonjohtopalkkiot-vuodelle (s/coll-of ::hoidonjohtopalkkio :type vector? :min-count 0))
(s/def ::hoidonjohtopalkkiot-urakalle (s/coll-of ::hoidonjohtopalkkiot-vuodelle :type vector?))

(s/def ::maksukausi #(contains? #{:kesa :talvi :molemmat} %))


;; #### Apurit ####
(defn dissoc-nils [m]
  (reduce-kv (fn [m k v]
               (if (nil? v)
                 m
                 (assoc m k v)))
    {}
    m))

(defn summaa-lehtivektorit [puu]
  (walk/postwalk (fn [x]
                   (if (and (map? x) (some vector? (vals x)))
                     (apply mapv + (vals x))
                     x))
    puu))

(defn summaa-mapin-arvot
  [arvot avain]
  (reduce (fn [summa arvo]
            (+ summa (get arvo avain 0)))
    0
    arvot))


(def hallinnollisten-idt
  {:erillishankinnat "erillishankinnat"
   :johto-ja-hallintokorvaus "johto-ja-hallintokorvaus"
   :toimistokulut "toimistokulut"
   :hoidonjohtopalkkio "hoidonjohtopalkkio"
   :tilaajan-varaukset "tilaajan-varaukset"})


;; -- Alin taulukko
(def ^{:private true} alimman-taulukon-id (hallinnollisten-idt :tilaajan-varaukset))

(defn alin-taulukko? [taulukon-id]
  (= taulukon-id alimman-taulukon-id))
;; |--

(defn paivita-raidat!
  "Päivittää taulukon näkyvien rivien CSS-luokat (even/odd)."
  [g]
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
   (let [alin-taulukko? (alin-taulukko? dom-id)]
     (if auki?
       (do (grid/nayta! (grid/osa-polusta solu aukeamis-polku))
           (paivita-raidat! (grid/osa-polusta (grid/root solu) polku-dataan))
           (when alin-taulukko?
             (r/after-render
               (fn []
                 (.scrollIntoView (dom/getElement dom-id) #js {"block" "end" "inline" "nearest" "behavior" "smooth"})))))
       (do (grid/piilota! (grid/osa-polusta solu sulkemis-polku))
           (paivita-raidat! (grid/osa-polusta (grid/root solu) polku-dataan)))))))



;; ### Palvelinkommunikaatio ###

(defonce tallennus-kanava
  ^{:doc "Jokainen muutos tallennetaan kantaan samantien. Se aiheuttaa ongelmia hitaan yhteyden tai kannan kanssa,
          koska kaksi erillistä muutosta, joiden pitäisi tallentua samalle riville kannassa, voidaan lähettää lyhyellä aikavälillä.
          Tämän kanavan kautta lähetetään kaikki tallennuuspyynnöt, ja varmistetaan, että niitä tulee vain yksi kerrallaan
          yhdelle asialle."
    :private true}
  (chan 10))

(defonce tallennus-jono
  ^{:doc "Pidetään täällä odottelemassa ne post kutsut, joita ei voida vielä lähettää."
    :private true}
  (atom {}))

(go-loop [{:keys [palvelu payload onnistui! epaonnistui!]} (async/<! tallennus-kanava)]
  (let [vastaus (<! (k/post! palvelu payload nil true))]
    (swap! tallennus-jono
      update
      palvelu
      (fn [kutsut]
        (vec (rest kutsut))))
    (if (k/virhe? vastaus)
      (epaonnistui! vastaus)
      (onnistui! vastaus))
    (when-let [args (get-in @tallennus-jono [palvelu 0])]
      (async/put! tallennus-kanava
        args))
    (recur (async/<! tallennus-kanava))))

(defn laheta-ja-odota-vastaus [app {:keys [palvelu onnistui epaonnistui] :as args}]
  (let [palvelujono-tyhja? (empty? (get @tallennus-jono palvelu))
        onnistui! (tuck/send-async! onnistui)
        epaonnistui! (tuck/send-async! epaonnistui)
        args (-> args
               (assoc :onnistui! onnistui! :epaonnistui! epaonnistui!)
               (dissoc :onnistui :epaonnistui))]
    (println "palvelujono" palvelujono-tyhja? "tallennusjono?" @tallennus-jono)
    (if palvelujono-tyhja?
      (swap! tallennus-jono assoc palvelu [args])
      (swap! tallennus-jono update palvelu conj args))
    (when palvelujono-tyhja?
      (async/put! tallennus-kanava
        args))
    app))



;; ----


;; Jos urakka on alkanut 2019 tai 2020, sille syötetään kattohinta käsin.
(def manuaalisen-kattohinnan-syoton-vuodet
  [2019 2020])

(def kattohinta-grid-avaimet
  (map (fn [hoitovuosi]
         (keyword (str "kattohinta-vuosi-" hoitovuosi)))
    (range 1 6)))

;; Kursorit manuaalisen kattohinnan gridiin
(def kattohinta-grid (r/cursor tiedot/kustannussuunnitelma-kattohinta [:grid]))
(def kattohinta-virheet (r/cursor tiedot/kustannussuunnitelma-kattohinta [:virheet]))

(def toimenpiteet #{:talvihoito
                    :liikenneympariston-hoito
                    :sorateiden-hoito
                    :paallystepaikkaukset
                    :mhu-yllapito
                    :mhu-korvausinvestointi})

(defn toimenpiteiden-jarjestys
  [toimenpide]
  (case toimenpide
    :talvihoito 0
    :liikenneympariston-hoito 1
    :sorateiden-hoito 2
    :paallystepaikkaukset 3
    :mhu-yllapito 4
    :mhu-korvausinvestointi 5))

(def toimenpiteet-rahavarauksilla #{:talvihoito
                                    :liikenneympariston-hoito
                                    :sorateiden-hoito
                                    :mhu-yllapito})

(defn poista-laskutukseen-perustuen-data! [toimenpide paivita-ui! modal-fn!]
  (let [data-hoitokausittain (keep (fn [hoitokauden-hankinnat]
                                     (let [hoitokauden-hankinnat (filterv (fn [{:keys [maara]}]
                                                                            (and maara (not= 0 maara)))
                                                                   hoitokauden-hankinnat)]
                                       (when-not (empty? hoitokauden-hankinnat)
                                         hoitokauden-hankinnat)))
                               (-> @tiedot/suunnittelu-kustannussuunnitelma :domain :laskutukseen-perustuvat-hankinnat toimenpide))
        poista! (fn []
                  (let [{urakka-id :id} (:urakka @tiedot/yleiset)

                        lahetettava-data {:urakka-id urakka-id
                                          :toimenpide-avain toimenpide
                                          :tallennettava-asia :toimenpiteen-maaramitattavat-tyot
                                          :summa 0
                                          :ajat (vec (mapcat (fn [hoitokauden-hankinnat]
                                                               (map #(select-keys % #{:vuosi :kuukausi})
                                                                 hoitokauden-hankinnat))
                                                       data-hoitokausittain))}]
                    (swap! tiedot/suunnittelu-kustannussuunnitelma
                      (fn [tiedot]
                        (update-in tiedot
                          [:domain :laskutukseen-perustuvat-hankinnat toimenpide]
                          (fn [laskutukseen-perustuvat-kustannukset]
                            (mapv (fn [hoitokausi-kohtaiset]
                                    (mapv (fn [kustannus]
                                            (assoc kustannus :maara 0))
                                      hoitokausi-kohtaiset))
                              laskutukseen-perustuvat-kustannukset)))))
                    (go (let [vastaus (<! (k/post! :tallenna-kustannusarvioitu-tyo
                                            lahetettava-data
                                            nil
                                            true))]
                          (when (k/virhe? vastaus)
                            (viesti/nayta! "Poistaminen epäonnistui..."
                              :warning viesti/viestin-nayttoaika-pitka))))))]
    (if-not (empty? data-hoitokausittain)
      (modal-fn! data-hoitokausittain poista!)
      (paivita-ui!))))


(def hoitokausien-maara-urakassa 5)

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

(def johto-ja-hallintokorvaukset-pohjadata
  [{:toimenkuva "sopimusvastaava" :kk-v 12 :maksukausi :molemmat :hoitokaudet (into #{} (range 1 6))}
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
        "mhu yllapito" "mhu ylläpito"
        "paallystepaikkaukset" "päällystepaikkaukset"
        "akillinen hoitotyo" "äkillinen hoitotyö"}
    sana
    sana))

(defn mhu-isoksi [sana]
  (clj-str/replace sana
    #"^Mhu (.)"
    (fn [[_ ensimmainen-kirjain]]
      (str "MHU " (clj-str/upper-case ensimmainen-kirjain)))))

(defn toimenpide-formatointi [toimenpide]
  (-> toimenpide name (clj-str/replace #"-" " ") aakkosta clj-str/capitalize mhu-isoksi))

(defn toimenkuva-formatoitu [{:keys [toimenkuva maksukausi hoitokaudet]}]
  (str (clj-str/capitalize toimenkuva) " "
    (case maksukausi
      :talvi "(talvikausi)"
      :kesa "(kesäkausi)"
      "")
    (when (contains? hoitokaudet 0)
      "(ennen urakkaa)")))

;; Fixme: Tätä ei käytetä missään?
(defn toimenpiteen-rahavaraukset [toimenpide]
  (case toimenpide
    (:talvihoito :liikenneympariston-hoito :sorateiden-hoito) [:kokonaishintainen-ja-lisatyo :akillinen-hoitotyo :vahinkojen-korjaukset]
    :mhu-yllapito [:kokonaishintainen-ja-lisatyo :muut-rahavaraukset]
    [:kokonaishintainen-ja-lisatyo]))



(defn kuukausi-kuuluu-maksukauteen? [kuukausi maksukausi]
  (cond
    (= maksukausi :kesa) (<= 5 kuukausi 9)
    (= maksukausi :talvi) (or (<= 1 kuukausi 4)
                            (<= 10 kuukausi 12))
    :else true))

;; TODO: Tavoitehinta tulisi hakea suoraan tietokannasta aina kun se päivitetään ja jättää tämä summafunktio käyttämättä.
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

;; --

(defn aseta-maara!
  ([grid-polku-fn domain-polku-fn tila arvo tunniste paivitetaan-domain?]
   (aseta-maara! grid-polku-fn domain-polku-fn tila arvo tunniste paivitetaan-domain? nil :yleinen))
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
                                    (assoc-in tila (domain-polku-fn tunniste hoitokauden-numero valittu-toimenpide)
                                      (if (empty? arvo) nil (js/Number arvo))))
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



;; ### Gridien datankäsittelijät ###

(def suunnitellut-hankinnat-rajapinta
  (merge {:otsikot any?
          :yhteensa any?

          :aseta-suunnitellut-hankinnat! any?
          :aseta-yhteenveto! any?}
    (reduce (fn [rajapinnat hoitokauden-numero]
              (merge rajapinnat
                {(keyword (str "yhteenveto-" hoitokauden-numero)) any?
                 (keyword (str "suunnitellut-hankinnat-" hoitokauden-numero)) any?}))
      {}
      (range 1 6))))

(defn suunnitellut-hankinnat-dr []
  (grid/datan-kasittelija
    tiedot/suunnittelu-kustannussuunnitelma
    suunnitellut-hankinnat-rajapinta
    (merge {:otsikot {:polut [[:gridit :suunnitellut-hankinnat :otsikot]]
                      :haku identity}
            :yhteensa {:polut [[:gridit :suunnitellut-hankinnat :yhteensa :data]
                               [:gridit :suunnitellut-hankinnat :yhteensa :nimi]]
                       :haku (fn [data nimi]
                               (assoc data :nimi nimi))}}
      (reduce (fn [haut hoitokauden-numero]
                (merge haut
                  {(keyword (str "yhteenveto-" hoitokauden-numero))
                   {:polut [[:gridit :suunnitellut-hankinnat :yhteenveto :data (dec hoitokauden-numero)]]
                    :haku (fn [data]
                            (assoc data :nimi (str hoitokauden-numero ". hoitovuosi")))}
                   (keyword (str "suunnitellut-hankinnat-" hoitokauden-numero))
                   {:polut [[:domain :suunnitellut-hankinnat]
                            [:suodattimet :hankinnat :toimenpide]
                            [:gridit :suunnitellut-hankinnat :hankinnat (dec hoitokauden-numero)]]
                    :haku (fn [suunnitellut-hankinnat valittu-toimenpide johdetut-arvot]
                            (let [arvot (mapv (fn [m]
                                                ;; TODO: Valitse myös :indeksikorjattu
                                                (select-keys m #{:maara :aika :yhteensa}))
                                          (get-in suunnitellut-hankinnat [valittu-toimenpide (dec hoitokauden-numero)]))
                                  johdetut-arvot (if (nil? johdetut-arvot)
                                                   (mapv (fn [{maara :maara}]
                                                           ;; TODO: Mahdollisesti lisää myös :indeksikorjattu, johon summataa ":indeksikorjattu"-arvot arvot-vektorista.
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
                                    #_(warn "suunnitellut-hankinnat-dr: JOHDETUT ARVOT EI OLE YHTÄ PITKÄ KUIN ARVOT\n"
                                      "-> ARVOT\n"
                                      (pr-str arvot)
                                      "-> JOHDETUT ARVOT\n"
                                      (pr-str johdetut-arvot))
                                    arvot)
                                  (vec
                                    (map merge
                                      arvot
                                      johdetut-arvot))))))}}))
        {}
        (range 1 6)))

    {:aseta-suunnitellut-hankinnat!
     (partial aseta-maara!
       (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
         [:gridit :suunnitellut-hankinnat :hankinnat (dec hoitokauden-numero) (first osan-paikka) osa])
       (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
         [:domain :suunnitellut-hankinnat valittu-toimenpide (dec hoitokauden-numero) (first osan-paikka) osa]))
     :aseta-yhteenveto!
     (fn [tila arvo tunniste hoitokauden-numero]
       (let [arvo (if (re-matches #"\d*,\d+" arvo)
                    (clj-str/replace arvo #"," ".")
                    arvo)
             paivita-gridit (fn [tila]
                              (update-in tila
                                [:gridit :suunnitellut-hankinnat :yhteenveto :data (dec hoitokauden-numero) tunniste]
                                (fn [tunnisteen-arvo]
                                  arvo)))]
         (if hoitokauden-numero
           (paivita-gridit tila)
           tila)))}

    (merge
      {:hankinnat-yhteensa-seuranta
       {:polut [[:gridit :suunnitellut-hankinnat :yhteenveto :data]]
        :init (fn [tila]
                (assoc-in tila [:gridit :suunnitellut-hankinnat :yhteensa :data] nil))
        :aseta (fn [tila data]
                 (let [hoidonjohtopalkkiot-yhteensa (summaa-mapin-arvot data :yhteensa)
                       indeksikorjatut-arvot (summaa-mapin-arvot data :indeksikorjattu)]
                   (assoc-in tila [:gridit :suunnitellut-hankinnat :yhteensa :data] {:yhteensa hoidonjohtopalkkiot-yhteensa
                                                                                     :indeksikorjattu indeksikorjatut-arvot})))}
       :valittu-toimenpide-seuranta
       {:polut [[:suodattimet :hankinnat :toimenpide]]
        :init (fn [tila]
                (assoc-in tila [:gridit :suunnitellut-hankinnat :hankinnat] (vec (repeat 5 nil))))
        :aseta (fn [tila _]
                 (update-in tila [:gridit :suunnitellut-hankinnat :hankinnat]
                   (fn [kaikki-hankinnat]
                     (mapv (fn [hoitokauden-hankinnat]
                             (vec (repeat 12 {})))
                       kaikki-hankinnat))))}}
      (doall
        (reduce (fn [seurannat hoitokauden-numero]
                  (merge seurannat
                    {(keyword (str "hankinnat-yhteenveto-seuranta-" hoitokauden-numero))
                     {:polut [[:domain :suunnitellut-hankinnat]
                              [:suodattimet :hankinnat :toimenpide]]
                      :init (fn [tila]
                              (-> tila
                                (update-in [:gridit :suunnitellut-hankinnat :hankinnat
                                            (dec hoitokauden-numero)] (vec (repeat 12 {})))
                                (update-in [:gridit :suunnitellut-hankinnat :yhteenveto :data]
                                  (fn [data]
                                    (if (nil? data)
                                      (vec (repeat 5 nil))
                                      data)))))
                      :aseta (fn [tila vuoden-hoidonjohtopalkkio valittu-toimenpide]
                               ;; TODO: Tee myös vuoden-hoidojohtopalkkiot-yhteensa-indeksikorjattu käyttäen :indeksikorjattu-arvoja tietokannasta.
                               (let [vuoden-hoidonjohtopalkkiot-yhteensa
                                     (summaa-mapin-arvot
                                       (get-in vuoden-hoidonjohtopalkkio [valittu-toimenpide (dec hoitokauden-numero)])
                                       :maara)]
                                 (-> tila
                                   (assoc-in [:gridit :suunnitellut-hankinnat :yhteenveto :data (dec hoitokauden-numero)]
                                     {:yhteensa vuoden-hoidonjohtopalkkiot-yhteensa
                                      ;; TODO: Poista indeksikorjaa-kutsu. Käytetään "vuoden-hoidojohtopalkkiot-yhteensa-indeksikorjattu"-summaa.
                                      :indeksikorjattu (indeksikorjaa vuoden-hoidonjohtopalkkiot-yhteensa hoitokauden-numero)})
                                   ;; Päivitetään myös yhteenvedotkomponentti
                                   ;;TODO: Päivitä myös [... :summat-indeksikorjattu :suunnitellut-hankinnat...]
                                   (assoc-in
                                     [:yhteenvedot :hankintakustannukset :summat :suunnitellut-hankinnat valittu-toimenpide (dec hoitokauden-numero)]
                                     vuoden-hoidonjohtopalkkiot-yhteensa))))}}))
          {}
          (range 1 6))))))

(def laskutukseen-perustuvat-hankinnat-rajapinta
  (merge {:otsikot any?
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
                  {(keyword (str "yhteenveto-" hoitokauden-numero))
                   {:polut [[:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data (dec hoitokauden-numero)]]
                    :haku (fn [data]
                            (assoc data :nimi (str hoitokauden-numero ". hoitovuosi")))}
                   (keyword (str "laskutukseen-perustuvat-hankinnat-" hoitokauden-numero))
                   {:polut [[:domain :laskutukseen-perustuvat-hankinnat]
                            [:suodattimet :hankinnat :toimenpide]
                            [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat (dec hoitokauden-numero)]]
                    :haku (fn [laskutukseen-perustuvat-hankinnat valittu-toimenpide johdetut-arvot]
                            (let [arvot (mapv (fn [m]
                                                ;; TODO: Valitse myös :indeksikorjattu
                                                (select-keys m #{:maara :aika :yhteensa}))
                                          (get-in laskutukseen-perustuvat-hankinnat
                                            [valittu-toimenpide (dec hoitokauden-numero)]))
                                  johdetut-arvot (if (nil? johdetut-arvot)
                                                   (mapv (fn [{maara :maara}]
                                                           ;; TODO: Mahdollisesti lisää myös :indeksikorjattu, johon summataa ":indeksikorjattu"-arvot arvot-vektorista.
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
                                    #_(warn "laskutukseen-perustuvat-hankinnat-dr: JOHDETUT ARVOT EI OLE YHTÄ PITKÄ KUIN ARVOT\n"
                                      "-> ARVOT\n"
                                      (pr-str arvot)
                                      "-> JOHDETUT ARVOT\n"
                                      (pr-str johdetut-arvot))
                                    arvot)
                                  (vec
                                    (map merge
                                      arvot
                                      johdetut-arvot))))))}}))
        {}
        (range 1 6)))

    {:aseta-laskutukseen-perustuvat-hankinnat!
     (partial aseta-maara!
       (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
         [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat (dec hoitokauden-numero) (first osan-paikka) osa])
       (fn [{:keys [aika osa osan-paikka]} hoitokauden-numero valittu-toimenpide]
         [:domain :laskutukseen-perustuvat-hankinnat valittu-toimenpide (dec hoitokauden-numero) (first osan-paikka) osa]))
     :aseta-yhteenveto!
     (fn [tila arvo tunniste hoitokauden-numero]
       (let [arvo (if (re-matches #"\d*,\d+" arvo)
                    (clj-str/replace arvo #"," ".")
                    arvo)]
         (if hoitokauden-numero
           (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data (dec hoitokauden-numero) tunniste] arvo)
           tila)))}

    (merge
      {:hankinnat-yhteensa-seuranta
       {:polut [[:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data]]
        :init (fn [tila]
                (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :yhteensa :data] nil))
        :aseta (fn [tila data]
                 (let [hoidonjohtopalkkiot-yhteensa (summaa-mapin-arvot data :yhteensa)
                       indeksikorjattu (summaa-mapin-arvot data :indeksikorjattu)]
                   (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :yhteensa :data]
                     {:yhteensa hoidonjohtopalkkiot-yhteensa
                      :indeksikorjattu indeksikorjattu})))}
       :valittu-toimenpide-seuranta
       {:polut [[:suodattimet :hankinnat :toimenpide]]
        :init (fn [tila]
                (assoc-in tila [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat] (vec (repeat 5 nil))))
        :aseta (fn [tila _]
                 (update-in tila [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat]
                   (fn [kaikki-hankinnat]
                     (mapv (fn [hoitokauden-hankinnat]
                             (vec (repeat 12 {})))
                       kaikki-hankinnat))))}
       :nollaa-johdetut-arvot
       {:polut [[:suodattimet :hankinnat :toimenpide]
                [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]]
        :aseta (fn [tila valittu-toimenpide laskutukseen-perustuen]
                 (if-not (contains? laskutukseen-perustuen valittu-toimenpide)
                   (reduce (fn [tila hoitokauden-numero]
                             (assoc-in tila
                               [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat (dec hoitokauden-numero)]
                               (vec (repeat 12 {}))))
                     tila
                     (range 1 6))
                   tila))}}
      (doall
        (reduce (fn [seurannat hoitokauden-numero]
                  (merge seurannat
                    {(keyword (str "laskutukseen-perustuvat-yhteenveto-seuranta-" hoitokauden-numero))
                     {:polut [[:domain :laskutukseen-perustuvat-hankinnat]
                              [:suodattimet :hankinnat :toimenpide]]
                      :init (fn [tila]
                              (-> tila
                                (update-in [:gridit :laskutukseen-perustuvat-hankinnat :hankinnat (dec hoitokauden-numero)]
                                  (vec (repeat 12 {})))
                                (update-in [:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data]
                                  (fn [data]
                                    (if (nil? data)
                                      (vec (repeat 5 nil))
                                      data)))))
                      :aseta (fn [tila vuoden-hoidonjohtopalkkio valittu-toimenpide]
                               ;; TODO: Laske vastaava summa :indeksikorjattu luvuista
                               (let [vuoden-hoidonjohtopalkkiot-yhteensa
                                     (summaa-mapin-arvot (get-in vuoden-hoidonjohtopalkkio [valittu-toimenpide (dec hoitokauden-numero)])
                                       :maara)]
                                 (-> tila
                                   (assoc-in [:gridit :laskutukseen-perustuvat-hankinnat :yhteenveto :data (dec hoitokauden-numero)]
                                     {:yhteensa vuoden-hoidonjohtopalkkiot-yhteensa
                                      ;; TODO: Poista indeksikorjaa-kutsu ja käytä määriteltyä indeksikorjattua summaa yllä.
                                      :indeksikorjattu (indeksikorjaa vuoden-hoidonjohtopalkkiot-yhteensa hoitokauden-numero)})
                                   ;; Päivitetään myös yhteenvedotkomponentti
                                   (assoc-in
                                     [:yhteenvedot :hankintakustannukset :summat :laskutukseen-perustuvat-hankinnat
                                      valittu-toimenpide (dec hoitokauden-numero)]
                                     vuoden-hoidonjohtopalkkiot-yhteensa))))}}))
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
                                                   ;; TODO: Valitse myös :indeksikorjattu
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
                                                   {(keyword (str "rahavaraukset-yhteenveto-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero))
                                                    [[:gridit :rahavaraukset :seurannat tyyppi]]})
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
                                                        {(keyword (str "rahavaraukset-kuukausitasolla-" tyyppi "?"))
                                                         [[:gridit :rahavaraukset :kuukausitasolla? tyyppi]]})
                                                  (distinct (keys (get rahavaraukset valittu-toimenpide)))))
                                      :haku identity}
     :rahavaraukset-data {:polut [[:domain :rahavaraukset]
                                  [:suodattimet :hankinnat :toimenpide]
                                  [:suodattimet :hoitokauden-numero]]
                          :luonti (fn [rahavaraukset valittu-toimenpide hoitokauden-numero]
                                    (let [toimenpiteen-rahavaraukset (get rahavaraukset valittu-toimenpide)]
                                      (mapv (fn [[tyyppi data]]
                                              {(keyword (str "rahavaraukset-data-" tyyppi "-" valittu-toimenpide "-" hoitokauden-numero))
                                               [[:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                [:gridit :rahavaraukset :varaukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]]})
                                        toimenpiteen-rahavaraukset)))
                          :haku (fn [rahavaraukset johdetut-arvot]
                                  (let [arvot (if (nil? johdetut-arvot)
                                                ;; TODO: Valitse myös :indeksikorjattu
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
                                   paivita-gridit
                                   (fn [tila]
                                     (reduce (fn [tila hoitokauden-numero]
                                               (update-in tila
                                                 [:gridit :rahavaraukset :varaukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
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
                                   paivita-domain
                                   (fn [tila]
                                     (let [arvo (if (empty? arvo)
                                                  nil
                                                  (js/Number arvo))]
                                       (reduce (fn [tila hoitokauden-numero]
                                                 (update-in tila
                                                   [:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                   (fn [hoitokauden-varaukset]
                                                     (if osan-paikka
                                                       (assoc-in hoitokauden-varaukset [(first osan-paikka) osa] arvo)
                                                       (mapv (fn [varaus]
                                                               (assoc varaus osa arvo))
                                                         hoitokauden-varaukset)))))
                                         tila
                                         paivitettavat-hoitokauden-numerot)))]
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
                                      (assoc-in tila [:gridit :rahavaraukset :otsikot :nimi] (toimenpide-formatointi valittu-toimenpide))
                                      tila))}
     ;; Hoitaa myös kuukausitasolla? filtterin asettamisen
     :rahavaraukset-yhteenveto-asettaminen
     {:polut [[:domain :rahavaraukset]
              [:suodattimet :hankinnat :toimenpide]
              [:suodattimet :hoitokauden-numero]]
      :luonti (fn [rahavaraukset valittu-toimenpide hoitokauden-numero]
                (when (contains? toimenpiteet-rahavarauksilla valittu-toimenpide)
                  (let [toimenpiteen-rahavaraukset (get rahavaraukset valittu-toimenpide)]
                    (when (not (nil? (ffirst toimenpiteen-rahavaraukset)))
                      (vec
                        (mapcat (fn [[tyyppi data]]
                                  ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                  [{(keyword (str "rahavaraukset-yhteenveto-" valittu-toimenpide "-" tyyppi "-" (dec hoitokauden-numero)))
                                    ^{:args [tyyppi]} [[:domain :rahavaraukset valittu-toimenpide tyyppi (dec hoitokauden-numero)]
                                                       [:suodattimet :hankinnat :toimenpide]]}])
                          toimenpiteen-rahavaraukset))))))
      :siivoa-tila (fn [tila _ _ tyyppi]
                     (update-in tila
                       [:gridit :rahavaraukset :seurannat]
                       (fn [tyyppien-data]
                         (dissoc tyyppien-data tyyppi))))
      :aseta (fn [tila maarat valittu-toimenpide tyyppi]
               (when (contains? toimenpiteet-rahavarauksilla valittu-toimenpide)
                 ;; TODO: Summaa myös :indeksikorjattu arvot
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
                               ;; TODO: Poista indeksikorjaa-kutsu ja käytä tietokannan arvoista laskettua summaa yllä.
                               :indeksikorjattu (indeksikorjaa yhteensa hoitokauden-numero)
                               :maara (if kuukausitasolla?
                                        vaihtelua-teksti
                                        (:maara (first maarat)))})]
                   (reduce (fn [tila hoitokauden-numero]
                             (as-> tila tila
                               (assoc-in tila
                                 [:yhteenvedot :hankintakustannukset :summat :rahavaraukset valittu-toimenpide tyyppi
                                  (dec hoitokauden-numero)]
                                 yhteensa)))
                     tila
                     paivitettavat-hoitokauden-numerot))))}
     :rahavaraukset-yhteensa-seuranta {:polut [[:gridit :rahavaraukset :seurannat]]
                                       :init (fn [tila]
                                               (assoc-in tila [:gridit :rahavaraukset :yhteensa :data] nil))
                                       :aseta (fn [tila data]
                                                (let [[rahavaraukset-yhteensa indeksikorjatut-arvot]
                                                      (reduce (fn [[summa indeksikorjattu-summa] [_ {:keys [yhteensa indeksikorjattu]}]]
                                                                [(+ summa yhteensa) (+ indeksikorjattu-summa indeksikorjattu)])
                                                        [0 0]
                                                        data)]
                                                  (assoc-in tila [:gridit :rahavaraukset :yhteensa :data]
                                                    {:yhteensa rahavaraukset-yhteensa
                                                     :indeksikorjattu indeksikorjatut-arvot})))}}))


(defn maarataulukon-rajapinta [polun-osa aseta-yhteenveto-avain aseta-avain]
  {:otsikot any?
   :yhteenveto any?
   :kuukausitasolla? any?
   :yhteensa any?
   polun-osa any?

   aseta-avain any?
   aseta-yhteenveto-avain any?})

(defn maarataulukon-dr [nayta-indeksikorjaus? rajapinta polun-osa yhteenvedot-polku aseta-avain aseta-yhteenveto-avain]
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
                                #_(warn (str "maarataulukon-dr (" [:gridit polun-osa :palkkiot] "): JOHDETUT ARVOT EI OLE YHTÄ PITKÄ KUIN ARVOT\n")
                                  "-> ARVOT\n"
                                  (pr-str arvot)
                                  "-> JOHDETUT ARVOT\n"
                                  (pr-str johdetut-arvot))
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
    ;; Gridin syöterivin lopussa oleva yhteenveto
    {:yhteenveto-seuranta {:polut [[:domain polun-osa]
                                   [:suodattimet :hoitokauden-numero]]
                           :init (fn [tila]
                                   (assoc-in tila [:gridit polun-osa :palkkiot] (vec (repeat 12 {}))))
                           :aseta (fn [tila maarat hoitokauden-numero]
                                    (let [valitun-vuoden-luvut (get maarat (dec hoitokauden-numero))
                                          vuoden-maarat-yhteensa (summaa-mapin-arvot valitun-vuoden-luvut :maara)
                                          maarat-samoja? (apply = (map :maara valitun-vuoden-luvut))
                                          maara (if maarat-samoja?
                                                  (get-in valitun-vuoden-luvut [0 :maara])
                                                  vaihtelua-teksti)

                                          ;; Tila ilman indeksikorjausta
                                          tila (-> tila
                                                 (assoc-in [:gridit polun-osa :yhteenveto :maara] maara)
                                                 (assoc-in [:gridit polun-osa :yhteenveto :yhteensa] vuoden-maarat-yhteensa))]
                                      (if nayta-indeksikorjaus?
                                        (let [vuoden-indeksikorjatut-yhteensa (summaa-mapin-arvot valitun-vuoden-luvut :indeksikorjattu)]
                                          (assoc-in tila
                                            [:gridit polun-osa :yhteenveto :indeksikorjattu] vuoden-indeksikorjatut-yhteensa))
                                        tila)))}
     :nollaa-johdetut-arvot {:polut [[:suodattimet :hoitokauden-numero]]
                             :aseta (fn [tila _]
                                      (assoc-in tila [:gridit polun-osa :palkkiot] (vec (repeat 12 {}))))}
     :yhteenveto-komponentin-seuranta {:polut [[:domain polun-osa]]
                                       :aseta (fn [tila hoitovuosien-tiedot]
                                                (let [hoitokauden-numero (get-in tila [:suodattimet :hoitokauden-numero])
                                                      kopioidaan-tuleville-vuosille? (get-in tila [:suodattimet :kopioidaan-tuleville-vuosille?])
                                                      paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                                                                          (range hoitokauden-numero 6)
                                                                                          [hoitokauden-numero])
                                                      valitun-vuoden-maarat (get hoitovuosien-tiedot (dec hoitokauden-numero))
                                                      vuoden-maarat-yhteensa (summaa-mapin-arvot valitun-vuoden-maarat :maara)
                                                      vuoden-indeksikorjatut-yhteensa (summaa-mapin-arvot valitun-vuoden-maarat :indeksikorjattu)]
                                                  (reduce (fn [tila hoitokauden-numero]
                                                            (cond->
                                                              (assoc-in tila
                                                                (vec (concat yhteenvedot-polku [:summat polun-osa (dec hoitokauden-numero)]))
                                                                vuoden-maarat-yhteensa)

                                                              nayta-indeksikorjaus?
                                                              (assoc-in
                                                                (vec (concat yhteenvedot-polku
                                                                       [:indeksikorjatut-summat polun-osa (dec hoitokauden-numero)]))
                                                                vuoden-indeksikorjatut-yhteensa)))
                                                    tila
                                                    paivitettavat-hoitokauden-numerot)))}

     ;; Gridin alaosassa syötteiden alla oleva yhteensä-rivi
     :yhteensa-seuranta {:polut [[:domain polun-osa]]
                         :aseta (fn [tila hoitovuosien-tiedot]
                                  (let [;; Jokaisen hoitovuoden alkuperäinen summa
                                        alkuperaiset-summat (mapv #(summaa-mapin-arvot % :maara)
                                                              hoitovuosien-tiedot)
                                        ;; Kaikilta hoitovuosilta yhteensä
                                        alkuparaiset-summat-yht (apply + alkuperaiset-summat)

                                        ;; Tila ilman indeksikorjausta
                                        tila (assoc-in tila [:gridit polun-osa :yhteensa :yhteensa]
                                               alkuparaiset-summat-yht)]

                                    (if nayta-indeksikorjaus?
                                      (let [;; Jokaisen hoitovuoden indeksikorjattu summa
                                            indeksikorjatut-summat (mapv #(summaa-mapin-arvot % :indeksikorjattu)
                                                                     hoitovuosien-tiedot)
                                            ;; Kaikilta hoitovuosilta yhteensä
                                            indeksikorjatut-summat-yht (apply + indeksikorjatut-summat)]
                                        (assoc-in tila [:gridit polun-osa :yhteensa :indeksikorjattu]
                                          indeksikorjatut-summat-yht))
                                      tila)))}}))

(def johto-ja-hallintokorvaus-rajapinta (merge {:otsikot any?
                                                :aseta-jh-yhteenveto! any?
                                                :aseta-tunnit! any?}
                                          ;; --- Itse lisättyjen toimenkuvarivien rajapinnat ---
                                          (reduce (fn [rajapinnat index]
                                                    (let [nimi (jh-omienrivien-nimi index)]
                                                      (merge rajapinnat
                                                        {(keyword (str "johto-ja-hallintokorvaus-" nimi)) any?
                                                         (keyword (str "kuukausitasolla?-" nimi)) any?
                                                         (keyword (str "yhteenveto-" nimi)) any?})))
                                            {}
                                            (range 1 (inc jh-korvausten-omiariveja-lkm)))

                                          ;; --- Vakio toimenkuvarivien rajapinnat ---
                                          (reduce (fn [rajapinnat {:keys [toimenkuva maksukausi]}]
                                                    (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                                                      (assoc rajapinnat
                                                        (keyword (str "yhteenveto" yksiloiva-nimen-paate)) any?
                                                        (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate)) any?
                                                        (keyword (str "johto-ja-hallintokorvaus" yksiloiva-nimen-paate)) any?)))
                                            {}
                                            johto-ja-hallintokorvaukset-pohjadata)))

(defn jh-yhteenvetopaivitys
  [tila arvo
   {:keys [omanimi osa toimenkuva maksukausi data-koskee-ennen-urakkaa?] :as tunniste}
   paivitetaan-domain?]
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
                                            ;; Itsetäytettävässä rivissä halutaan toimenkuvan olevan sama kaikille.
                                            (= osa :toimenkuva) (range 1 6)
                                            kopioidaan-tuleville-vuosille? (range hoitokauden-numero 6)
                                            :else [hoitokauden-numero])
        domain-paivitys (fn [tila]
                          (reduce (fn [tila hoitokauden-numero]
                                    (update-in tila
                                      (if omanimi
                                        [:domain :johto-ja-hallintokorvaukset omanimi (dec hoitokauden-numero)]
                                        [:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi (dec hoitokauden-numero)])
                                      (fn [hoitokauden-jh-korvaukset]
                                        (mapv (fn [jh-korvaus]
                                                (let [jh-korvaus
                                                      (assoc jh-korvaus
                                                        osa
                                                        (cond
                                                          (= osa :toimenkuva)
                                                          arvo

                                                          :else (js/Number arvo)))
                                                      #_#__ (println "###\n"
                                                              "--- hoitovuosi:" hoitokauden-numero "\n"
                                                              "--- toimenkuva:" toimenkuva "\n"
                                                              "--- input-kentän tyyppi: " osa "\n"
                                                              "--- data-koskee-ennen-urakkaa?:\n    " data-koskee-ennen-urakkaa? "\n"
                                                              "--- arvo: " arvo ", arvo muunnettu:" (osa jh-korvaus))]
                                                  jh-korvaus))
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
    ;; ### Rajapinta
    johto-ja-hallintokorvaus-rajapinta
    ;; ### Haku-kuvaus
    (merge
      {:otsikot {:polut [[:gridit :johto-ja-hallintokorvaukset :otsikot]]
                 :haku identity}}
      (apply merge
        ;; --- Itse lisättyjen toimenkuva-rivien rajapinnat ---
        (reduce (fn [rajapinnat index]
                  (let [nimi (jh-omienrivien-nimi index)]
                    (merge rajapinnat
                      {(keyword (str "yhteenveto-" nimi))
                       {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi]]
                        :haku identity}

                       (keyword (str "kuukausitasolla?-" nimi))
                       {:polut [[:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? nimi]]
                        :luonti-init (fn [tila _]
                                       (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? nimi] false))
                        :haku identity}

                       (keyword (str "johto-ja-hallintokorvaus-" nimi))
                       {:polut [[:domain :johto-ja-hallintokorvaukset nimi]
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
                                        #_(warn (str "johto-ja-hallintokorvaus-dr (" (str "johto-ja-hallintokorvaus-" nimi)
                                                "): JOHDETUT ARVOT EI OLE YHTÄ PITKÄ KUIN ARVOT\n")
                                          "-> ARVOT\n"
                                          (pr-str arvot)
                                          "-> JOHDETUT ARVOT\n"
                                          (pr-str johdetut-arvot))
                                        arvot)
                                      (vec
                                        (map merge
                                          arvot
                                          johdetut-arvot))))))}})))
          {}
          (range 1 (inc jh-korvausten-omiariveja-lkm)))

        ;; --- Vakio toimenkuvarivien rajapinnat ---
        (mapv (fn [{:keys [toimenkuva maksukausi hoitokaudet]}]
                (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)
                      data-koskee-ennen-urakkaa? (toimenpide-koskee-ennen-urakkaa? hoitokaudet)]
                  (if data-koskee-ennen-urakkaa?
                    {(keyword (str "yhteenveto" yksiloiva-nimen-paate))
                     {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto toimenkuva maksukausi]]
                      :haku identity}}

                    {(keyword (str "yhteenveto" yksiloiva-nimen-paate))
                     {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto toimenkuva maksukausi]]
                      :haku identity}

                     (keyword (str "kuukausitasolla?" yksiloiva-nimen-paate))
                     {:polut [[:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi]]
                      :luonti-init (fn [tila _]
                                     (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :kuukausitasolla? toimenkuva maksukausi] false))
                      :haku identity}

                     (keyword (str "johto-ja-hallintokorvaus" yksiloiva-nimen-paate))
                     {:polut [[:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi]
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
                                      #_(warn (str "johto-ja-hallintokorvaus-dr ("
                                              (str "johto-ja-hallintokorvaus" yksiloiva-nimen-paate)
                                              "): JOHDETUT ARVOT EI OLE YHTÄ PITKÄ KUIN ARVOT\n")
                                        "-> ARVOT\n"
                                        (pr-str arvot)
                                        "-> JOHDETUT ARVOT\n"
                                        (pr-str johdetut-arvot))
                                      arvot)
                                    (vec
                                      (map merge
                                        arvot
                                        johdetut-arvot))))))}})))
          johto-ja-hallintokorvaukset-pohjadata)))

    ;; ### Asetus-kuvaus
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

    ;; ### Seurannat-kuvaus
    (merge {:nollaa-johdetut-arvot
            {:polut [[:suodattimet :hoitokauden-numero]]
             :aseta (fn [tila _]
                      (as-> tila $
                        (reduce (fn [tila {:keys [toimenkuva maksukausi hoitokaudet] :as toimenkuva-kuvaus}]
                                  (assoc-in tila
                                    [:gridit :johto-ja-hallintokorvaukset :johdettu toimenkuva maksukausi]
                                    (vec (repeat (kk-v-toimenkuvan-kuvaukselle toimenkuva-kuvaus) {}))))
                          $
                          johto-ja-hallintokorvaukset-pohjadata)
                        (reduce (fn [tila jarjestysnumero]
                                  (let [nimi (jh-omienrivien-nimi jarjestysnumero)]
                                    (assoc-in tila
                                      [:gridit :johto-ja-hallintokorvaukset :johdettu nimi]
                                      (vec (repeat 12 {})))))
                          $
                          (range 1 (inc jh-korvausten-omiariveja-lkm)))))}}
      (reduce (fn [seurannat {:keys [toimenkuva maksukausi hoitokaudet] :as toimenkuva-kuvaus}]
                (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)
                      data-koskee-ennen-urakkaa? (toimenpide-koskee-ennen-urakkaa? hoitokaudet)]
                  (merge seurannat
                    {(keyword (str "yhteenveto" yksiloiva-nimen-paate "-seuranta"))
                     {:polut [[:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi]
                              [:suodattimet :hoitokauden-numero]]
                      :init (fn [tila]
                              (assoc-in tila
                                [:gridit :johto-ja-hallintokorvaukset :johdettu toimenkuva maksukausi]
                                (vec (repeat (kk-v-toimenkuvan-kuvaukselle toimenkuva-kuvaus) {}))))
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
                                 (update-in tila
                                   [:gridit :johto-ja-hallintokorvaukset :yhteenveto toimenkuva maksukausi]
                                   merge
                                   {:tunnit tunnit
                                    :yhteensa (when-not (= 0 yhteensa)
                                                yhteensa)
                                    ;; TODO: Indeksikorjattu yhteensa-arvo tähän esim. :yhteensa-indeksikorjattu.
                                    :tuntipalkka tuntipalkka
                                    ;; TODO: Indeksikorjattu tuntipalkka tähän esim. :tuntipalkka-indeksikorjattu
                                    :kk-v kk-v})))}})))
        {}
        johto-ja-hallintokorvaukset-pohjadata)
      (reduce (fn [seurannat jarjestysnumero]
                (let [nimi (jh-omienrivien-nimi jarjestysnumero)]
                  (merge seurannat
                    {(keyword (str "nollaa-johdetut-arvot-" nimi))
                     {:polut [[:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :maksukausi]]
                      :aseta (fn [tila _]
                               (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :johdettu nimi] (vec (repeat 12 {}))))}

                     (keyword (str "yhteenveto-" nimi "-seuranta"))
                     {:polut [[:domain :johto-ja-hallintokorvaukset nimi]
                              [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :maksukausi]
                              [:suodattimet :hoitokauden-numero]]
                      :init (fn [tila]
                              (assoc-in tila [:gridit :johto-ja-hallintokorvaukset :johdettu nimi] (vec (repeat 12 {}))))
                      :aseta (fn [tila jh-korvaukset maksukausi hoitokauden-numero]
                               (let [korvauksien-index (dec hoitokauden-numero)
                                     maksukauden-jh-tunnit (filterv (fn [{:keys [kuukausi]}]
                                                                      (kuukausi-kuuluu-maksukauteen? kuukausi maksukausi))
                                                             (get jh-korvaukset korvauksien-index))
                                     tuntipalkka (get-in maksukauden-jh-tunnit [0 :tuntipalkka])
                                     kk-v (get-in maksukauden-jh-tunnit [0 :kk-v])
                                     tunnit-samoja? (apply = (map :tunnit maksukauden-jh-tunnit))
                                     tunnit (if tunnit-samoja?
                                              (get-in maksukauden-jh-tunnit [0 :tunnit])
                                              vaihtelua-teksti)
                                     yhteensa (if tunnit-samoja?
                                                (* tunnit tuntipalkka)
                                                vaihtelua-teksti)]
                                 (update-in tila
                                   [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi]
                                   merge
                                   {:tunnit tunnit
                                    :yhteensa (when-not (= 0 yhteensa)
                                                yhteensa)
                                    ;; TODO: Indeksikorjattu yhteensa-arvo tähän. Esim. :yhteensa-indeksikorjattu
                                    :tuntipalkka tuntipalkka
                                    ;; TODO: Hoida indeksikorjattu tuntipalkka tähän. Esim. tuntipalkka-indeksikorjattu
                                    :kk-v kk-v})))}})))
        {}
        (range 1 (inc jh-korvausten-omiariveja-lkm))))))

(def johto-ja-hallintokorvaus-yhteenveto-rajapinta
  (merge {:otsikot any?
          :yhteensa any?
          :indeksikorjattu any?}
    (reduce (fn [rajapinnat {:keys [toimenkuva maksukausi]}]
              (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                (assoc rajapinnat
                  (keyword (str "yhteenveto" yksiloiva-nimen-paate)) any?)))
      {}
      johto-ja-hallintokorvaukset-pohjadata)
    (reduce (fn [rajapinnat index]
              (let [nimi (jh-omienrivien-nimi index)]
                (merge rajapinnat
                  {(keyword (str "yhteenveto-" nimi)) any?})))
      {}
      (range 1 (inc jh-korvausten-omiariveja-lkm)))))

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
                  {(keyword (str "yhteenveto" yksiloiva-nimen-paate))
                   {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto toimenkuva maksukausi]]
                    :haku #(vec (concat [toimenkuva-formatoitu kk-v] %))}}))
          johto-ja-hallintokorvaukset-pohjadata))
      (reduce (fn [rajapinnat index]
                (let [nimi (jh-omienrivien-nimi index)]
                  (merge rajapinnat
                    {(keyword (str "yhteenveto-" nimi))
                     {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto nimi]
                              [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi]]
                      :haku (fn [yhteenvedot-vuosille {:keys [maksukausi toimenkuva]}]
                              (let [kk-v (when maksukausi
                                           (mhu/maksukausi->kuukausi maksukausi))]
                                (vec (concat [toimenkuva kk-v] yhteenvedot-vuosille))))}})))
        {}
        (range 1 (inc jh-korvausten-omiariveja-lkm))))

    {}

    (apply merge
      {:yhteensa-seuranta
       {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto]]
        :aseta (fn [tila yhteenvedot]
                 (let [yhteensa-arvot (summaa-lehtivektorit
                                        (walk/postwalk (fn [x]
                                                         (if (vector? x)
                                                           (let [arvot (mapv #(if (= :ei-aseteta %) 0 %) x)]
                                                             (if (not= hoitokausien-maara-urakassa (count arvot))
                                                               (let [vektorin-koko (count arvot)]
                                                                 (reduce (fn [valivaihe index]
                                                                           (if (>= index vektorin-koko)
                                                                             (conj valivaihe 0)
                                                                             (conj valivaihe (get arvot index))))
                                                                   []
                                                                   (range hoitokausien-maara-urakassa)))
                                                               arvot))
                                                           x))
                                          yhteenvedot))]
                   (-> tila
                     (assoc-in [:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteensa] yhteensa-arvot)
                     (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :johto-ja-hallintokorvaukset] yhteensa-arvot))))}
       :indeksikorjattu-seuranta {:polut [[:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteensa]]
                                  :aseta (fn [tila yhteensa]
                                           (assoc-in tila
                                             [:gridit :johto-ja-hallintokorvaukset-yhteenveto :indeksikorjattu]
                                             (mapv indeksikorjaa yhteensa (range 1 6))))}}
      (reduce (fn [rajapinnat index]
                (let [nimi (jh-omienrivien-nimi index)]
                  (merge rajapinnat
                    {(keyword (str "yhteenveto-" nimi "-seuranta"))
                     {:polut [[:domain :johto-ja-hallintokorvaukset nimi]]
                      :aseta (fn [tila omat-jh-korvaukset]
                               (let [toimenkuva (get-in tila [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :toimenkuva])
                                     yhteensa-arvot (if toimenkuva
                                                      (mapv (fn [hoitokauden-arvot]
                                                              (let [tuntipalkka (get-in hoitokauden-arvot [0 :tuntipalkka])]
                                                                (* (summaa-mapin-arvot hoitokauden-arvot :tunnit)
                                                                  tuntipalkka)))
                                                        omat-jh-korvaukset)
                                                      (vec (repeat 5 :ei-aseteta)))]
                                 ;; TODO: Käsittele myös indeksikorjatut arvot ja laske :yhteensa-indeksikorjatut-arvot. Pitää ehkä tallentaa [.... :yhteenveto-indeksikorjattu nimi] alle..
                                 (assoc-in tila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto nimi] yhteensa-arvot)))}})))
        {}
        (range 1 (inc jh-korvausten-omiariveja-lkm)))
      (mapv (fn [{:keys [toimenkuva maksukausi]}]
              (let [yksiloiva-nimen-paate (str "-" toimenkuva "-" maksukausi)]
                {(keyword (str "yhteenveto" yksiloiva-nimen-paate "-seuranta"))
                 {:polut [[:domain :johto-ja-hallintokorvaukset toimenkuva maksukausi]]
                  :aseta (fn [tila jh-korvaukset]
                           (let [yhteensa-arvot (mapv (fn [hoitokauden-arvot]
                                                        (let [tuntipalkka (get-in hoitokauden-arvot [0 :tuntipalkka])
                                                              tunnit (reduce (fn [summa {:keys [tunnit osa-kuukaudesta]}]
                                                                               (+ summa
                                                                                 (* tunnit osa-kuukaudesta)))
                                                                       0
                                                                       hoitokauden-arvot)]
                                                          (* tunnit
                                                            tuntipalkka)))
                                                  jh-korvaukset)]
                             ;; TODO: Käsittele indeksikorjatut arvot ja laske siitä yhteensä-arvot. Pitää ehkä tallenntaa [... :yhteenveto-indeksikorjattu toimenkuva maksukausi] alle.
                             (assoc-in tila [:gridit :johto-ja-hallintokorvaukset-yhteenveto :yhteenveto toimenkuva maksukausi]
                               yhteensa-arvot)))}}))
        johto-ja-hallintokorvaukset-pohjadata))))

(defn paivita-solun-arvo [{:keys [paivitettava-asia arvo solu ajettavat-jarejestykset triggeroi-seuranta?]
                           :or {ajettavat-jarejestykset false triggeroi-seuranta? false}}
                          & args]
  (jarjesta-data ajettavat-jarejestykset
    (triggeroi-seurannat triggeroi-seuranta?
      (case paivitettava-asia
        :aseta-suunnitellut-hankinnat! (apply grid/aseta-rajapinnan-data!
                                         (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                         :aseta-suunnitellut-hankinnat!
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

;; |--- Gridien datankäsittelijät päättyy



;; ### Suunnitelman osion vahvistus (apurit) ###

(defn- hae-osion-tilat
  "Palauttaa osion vahvistusten tilat jokaiselle hoitovuodelle"
  [app osio-kw hoitovuosilta]
  (println "hae-osion-tila" hoitovuosilta osio-kw)
  (let [osion-tilat (get-in app [:domain :osioiden-tilat osio-kw])
        tarkastettavat (if (number? hoitovuosilta)
                         (get osion-tilat hoitovuosilta)
                         (mapv #(get osion-tilat %) hoitovuosilta))]
    (println "hae-osion-tila tarkastettavat:" tarkastettavat)

    (if (vector? tarkastettavat)
      (every? some? tarkastettavat)
      (some? tarkastettavat))))

(defn- pitaako-osion-muutokset-vahvistaa?
  "Tämä funktio käytännössä palauttaa tiedon siitä, onko osio jo vahvistettu.
  Tätä käytetään tarkastamaan täytyykö osion vahvistamisen jälkeen tehdyt muutokset vahvistaa."
  [app osio-kw hoitovuosi]
  (get-in app [:domain :osioiden-tilat osio-kw hoitovuosi]))

(defn- osion-vahvistettavat-vuodet
  "Palauttaa osion hoitovuodet, joita on muokattu osion vahvistamisen jälkeen.
  Näitä muutoksia varten täytyy käyttäjän kirjoittaa erillinen syy avautuvaan vahvistusdialogiin."
  [app osio-kw hoitovuosilta]
  (let [osion-tilat (get-in app [:domain :osioiden-tilat osio-kw])
        tarkastettavat (select-keys osion-tilat (if (number? hoitovuosilta)
                                                  [hoitovuosilta]
                                                  hoitovuosilta))]
    (into {}
      (comp
        (filter #(true? (second %)))
        (map (fn [[hoitovuoden-nro tila]]
               (let [alkuvuosi (-> @tiedot/yleiset :urakka :alkupvm pvm/vuosi)]
                 [(+ alkuvuosi (dec hoitovuoden-nro)) tila]))))
      tarkastettavat)))

;; TODO: Tämä on vähän erikoinen key-mappi. Katsotaan tarvitaanko tätä lopulta ollenkaan.
;;       Tätä käytetään TarkistaTarvitaankoVahvistus tuck-eventissä, joka ei tällä hetkellä ole käytössä
(def tyyppi->osio {:tilaajan-varaukset :tilaajan-rahavaraukset
                   :akilliset-hoitotyot :hankintakustannukset
                   :kolmansien-osapuolten-aiheuttamat-vahingot :hankintakustannukset
                   :toimistokulut :johto-ja-hallintokorvaus
                   :aseta-jh-yhteenveto! :johto-ja-hallintokorvaus}) ; nää muutamat on outoja, koska ne tulee geneerisestä komponentista ja tunnistetaan siellä annetuilla eventtinimillä

;; | -- Suunnitelman osion vahvistus (apurit) päättyy


;; ------------------------
;; ----- Tuck eventit -----
;; ------------------------

;; Yleiset
(defrecord TaulukoidenVakioarvot [])
(defrecord FiltereidenAloitusarvot [])
(defrecord Oikeudet [])
(defrecord Hoitokausi [])
(defrecord YleisSuodatinArvot [])
(defrecord HaeIndeksitOnnistui [vastaus])
(defrecord HaeIndeksitEpaonnistui [vastaus])
(defrecord HaeKustannussuunnitelma [])
(defrecord HaeHankintakustannuksetOnnistui [vastaus])
(defrecord HaeHankintakustannuksetEpaonnistui [vastaus])
(defrecord HaeKustannussuunnitelmanTilat [])
(defrecord HaeKustannussuunnitelmanTilatOnnistui [vastaus])
(defrecord HaeKustannussuunnitelmanTilatEpaonnistui [vastaus])
(defrecord HaeBudjettitavoite [])
(defrecord HaeBudjettitavoiteOnnistui [vastaus])
(defrecord HaeBudjettitavoiteEpaonnistui [vastaus])

;; Hankintakustannukset
(defrecord MaksukausiValittu [])
(defrecord TallennaHankintojenArvot [tallennettava-asia hoitokauden-numero tunnisteet])
(defrecord TallennaHankintojenArvotOnnistui [vastaus])
(defrecord TallennaHankintojenArvotEpaonnistui [vastaus])

;; Kustannussusarvioidut työt
(defrecord TallennaKustannusarvoitu [tallennettava-asia tunnisteet opts])
(defrecord TallennaKustannusarvoituOnnistui [vastaus])
(defrecord TallennaKustannusarvoituEpaonnistui [vastaus])
(defrecord TallennaErillishankinnatOnnistui [vastaus])
(defrecord TallennaHoidonjohtopalkkioOnnistui [vastaus])
(defrecord TallennaToimistokulutOnnistui [vastaus])

;; Johto- ja hallintokorvaukset
(defrecord TallennaJohtoJaHallintokorvaukset [tunnisteet])
(defrecord TallennaJohtoJaHallintokorvauksetOnnistui [vastaus])
(defrecord TallennaJohtoJaHallintokorvauksetEpaonnistui [vastaus])
(defrecord PoistaOmaJHDdata [sarake nimi maksukausi piilota-modal! paivita-ui! modal-fn!])
(defrecord PoistaOmaJHDdataOnnistui [vastaus])
(defrecord PoistaOmaJHDdataEpaonnistui [vastaus])
;; FIXME: Tätä ei käytetä missään?
(defrecord MuutaOmanJohtoJaHallintokorvauksenArvoa [nimi sarake arvo])

;; Määrätaulukko-grid
(defrecord TallennaJaPaivitaTavoiteSekaKattohinta [])
(defrecord TallennaJaPaivitaTavoiteSekaKattohintaOnnistui [vastaus])
(defrecord TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui [vastaus])

;;
(defrecord TallennaToimenkuva [rivin-nimi])
(defrecord TallennaToimenkuvaOnnistui [vastaus])
(defrecord TallennaToimenkuvaEpaonnistui [vastaus])

;; Osan tilan hallinta. Käytetään eri osissa.
(defrecord TallennaKustannussuunnitelmanOsalleTila [parametrit])
(defrecord TallennaKustannussuunnitelmanOsalleTilaOnnistui [vastaus])
(defrecord TallennaKustannussuunnitelmanOsalleTilaEpaonnistui [vastaus])

;; Osion vahvistaminen / muuttaminen
(defrecord VahvistaSuunnitelmanOsioVuodella [parametrit])
(defrecord VahvistaSuunnitelmanOsioVuodellaOnnistui [vastaus])
(defrecord VahvistaSuunnitelmanOsioVuodellaEpaonnistui [vastaus])
(defrecord KumoaOsionVahvistusVuodelta [parametrit])
(defrecord KumoaOsionVahvistusVuodeltaOnnistui [vastaus])
(defrecord KumoaOsionVahvistusVuodeltaEpaonnistui [vastaus])

;; Kattohinnan gridin käsittelijät
(defrecord PaivitaKattohintaGrid [grid])

;; TODO: Muutoksia ei implementoitu vielä loppuun
(defrecord TallennaSeliteMuutokselle [])
(defrecord TallennaSeliteMuutokselleOnnistui [])
(defrecord TallennaSeliteMuutokselleEpaonnistui [])

;;
(defrecord VahvistaMuutoksetJaTallenna [tiedot])
(defrecord SuljeMuutostenVahvistusModal [])
;; FIXME: Tätä ei käytetä missään?
(defrecord TarkistaTarvitaankoMuutostenVahvistus [asia hoitovuosi toiminto-fn!])



(defn- kysy-muutosten-vahvistus
  [app osio-kw vahvistettavat-vuodet tiedot]
  (assoc-in app [:domain :muutosten-vahvistus]
    {:vaaditaan-muutosten-vahvistus? true
     :vahvistettavat-vuodet vahvistettavat-vuodet
     :asia osio-kw
     :muutos-vahvistettu-fn (r/partial (fn [tiedot e! muutos]
                                         (e! (->VahvistaMuutoksetJaTallenna (update tiedot :payload merge {:muutos muutos}))))
                              tiedot)}))

(defn- tallenna-tavoite-ja-kattohinnat
  [app]
  (let [kattohinnan-kerroin 1.1
        yhteenvedot (tavoitehinnan-summaus (:yhteenvedot app))
        {urakka-id :id
         alkupvm :alkupvm} (:urakka @tiedot/yleiset)
        manuaaliset-kattohinnat? (some #(= (pvm/vuosi alkupvm) %) manuaalisen-kattohinnan-syoton-vuodet)
        lahetettava-data {:urakka-id urakka-id
                          :tavoitteet (vec (map-indexed (fn [index summa]
                                                          (let [kattohinta
                                                                (if manuaaliset-kattohinnat?
                                                                  (when (get-in app [:kattohinta :grid 0 :koskettu?])
                                                                    (get-in app
                                                                      [:kattohinta :grid 0
                                                                       (keyword (str "kattohinta-vuosi-" (inc index)))]))
                                                                  (* summa kattohinnan-kerroin))]
                                                            (merge
                                                              {:hoitokausi (inc index)
                                                               :tavoitehinta summa}
                                                              ;; Älä lähetä tyhjää kattohintaa jotta validaatio toimii.
                                                              (when kattohinta
                                                                {:kattohinta kattohinta}))))
                                             yhteenvedot))}]
    (laheta-ja-odota-vastaus app
      {:palvelu :tallenna-budjettitavoite
       :payload lahetettava-data
       :onnistui ->TallennaJaPaivitaTavoiteSekaKattohintaOnnistui
       :epaonnistui ->TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui})))

(defn urakan-ajat
  "Käytetään tilan alustamisessa pohjadatana."
  []
  (let [urakan-aloitus-pvm (-> @tiedot/tila :yleiset :urakka :alkupvm)]
    ;; Voi olla nil, jos on lähdetty urakasta sillä välin, kun haku kannasta on kesken
    (when-not (nil? urakan-aloitus-pvm)
      (into []
        (drop 9
          (drop-last 3
            (mapcat (fn [vuosi]
                      (map #(identity
                              {:vuosi vuosi
                               :kuukausi %})
                        (range 1 13)))
              (range (pvm/vuosi urakan-aloitus-pvm) (+ (pvm/vuosi urakan-aloitus-pvm) 6)))))))))

(defn pohjadatan-taydennys-fn
  "Käytetään pohjadatan rikastamiseen tilan alustuksessa."
  [pohjadata data-backendilta filter-fn rikastamis-fn]
  (let [sort-fn (juxt :vuosi :kuukausi)
        data (loop [[pd & pd-loput] (sort-by sort-fn pohjadata)
                    muodostettu []
                    i 0]
               (cond
                 (nil? pd) muodostettu

                 (filter-fn (:vuosi pd) (:kuukausi pd))
                 (let [tarkasteltava-data (get data-backendilta i)
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



(defn pohjadatan-taydennys-toimenpiteittain-fn
  "Käytetään pohjadatan rikastamiseen tilan alustuksessa. Data rikastetaan backendiltä saatavalla datalla.
  esim. hankinnat-toimenpiteittain"
  [pohjadata data-backendilta toimenpiteet rikastamis-fn]
  (reduce (fn [data-toimenpiteittain toimenpide]
            (let [sort-fn (juxt :vuosi :kuukausi)
                  data-backilta (vec (sort-by sort-fn (filter #(= (:toimenpide-avain %) toimenpide) data-backendilta)))
                  data (pohjadatan-taydennys-fn pohjadata data-backilta (constantly true) rikastamis-fn)]
              (merge data-toimenpiteittain
                {toimenpide data})))
    {}
    toimenpiteet))

(defn maarataulukon-kk-data-alustus-fn
  "Alustaa maarataulukon datan rikastetulla pohjadatalla."
  [pohjadata kustannusarvioidut-tyot haettu-asia]
  (let [asia-kannasta (filter (fn [tyo]
                                (= haettu-asia (:haettu-asia tyo)))
                        kustannusarvioidut-tyot)]
    (pohjadatan-taydennys-fn pohjadata (vec (sort-by (juxt :vuosi :kuukausi) asia-kannasta))
      (constantly true)
      (fn [{:keys [vuosi kuukausi summa summa-indeksikorjattu] :as data}]
        #_(println "### maara-kk-taulukon-data vuosi:" vuosi " kuukausi: " kuukausi " summa: " summa " indeksikorjattu: " summa-indeksikorjattu)

        (-> data
          (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                 :maara summa
                 :indeksikorjattu summa-indeksikorjattu)
          (dissoc :summa)
          (dissoc :summa-indeksikorjattu))))))


(defn hoidonjohto-jarjestys-fn
  "Käytetään ATM vain määrätaulukon-kk-data:n järjestykseen tilan alustuksessa.
  Voisi ottaa varmaan osaksi maarataulukon-kk-data-alustus-fn prosessia."
  [data]
  (vec
    (sort-by #(-> % first :aika)
      (vals
        (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
          data)))))

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
        (assoc-in [:gridit :suunnitelmien-tila :otsikot]
          {:kuluva-hoitovuosi (str (when-not viimeinen-vuosi?
                                     kuluva-hoitokauden-numero)
                                ". vuosi")
           :seuraava-hoitovuosi (str (if viimeinen-vuosi?
                                       kuluva-hoitokauden-numero
                                       (inc kuluva-hoitokauden-numero))
                                  ". vuosi")})
        (assoc-in [:gridit :suunnitelmien-tila :hallinnolliset-toimenpiteet :nimi] "Hallinnolliset toimenteet")
        (assoc-in [:gridit :laskutukseen-perustuvat-hankinnat]
          {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
           :yhteenveto {:nimi "Määrämitattavat"}
           :yhteensa {:nimi "Yhteensä"}
           :kuukausitasolla? false})
        (assoc-in [:gridit :rahavaraukset]
          {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
           :yhteensa {:nimi "Yhteensä"}})
        (assoc-in [:gridit :erillishankinnat]
          {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
           :yhteenveto {:nimi "Erillishankinnat"}
           :yhteensa {:nimi "Yhteensä"}
           :kuukausitasolla? false})
        (assoc-in [:gridit :johto-ja-hallintokorvaukset]
          {:otsikot {:toimenkuva "Toimenkuva" :tunnit "Tunnit/kk, h" :tuntipalkka "Tuntipalkka, €" :yhteensa "Yhteensä/kk" :kk-v "kk/v"}
           :yhteenveto (reduce (fn [yhteenveto-otsikot {:keys [toimenkuva maksukausi] :as toimenkuva-kuvaus}]
                                 (assoc-in yhteenveto-otsikot [toimenkuva maksukausi :toimenkuva] (toimenkuva-formatoitu toimenkuva-kuvaus)))
                         {}
                         johto-ja-hallintokorvaukset-pohjadata)})
        (assoc-in [:gridit :johto-ja-hallintokorvaukset-yhteenveto]
          {:otsikot {:toimenkuva "Toimenkuva" :kk-v "kk/v" :hoitovuosi-1 "1.vuosi/€" :hoitovuosi-2 "2.vuosi/€" :hoitovuosi-3 "3.vuosi/€" :hoitovuosi-4 "4.vuosi/€" :hoitovuosi-5 "5.vuosi/€"}})
        (assoc-in [:gridit :suunnitellut-hankinnat]
          {:otsikot {:nimi "Kiinteät" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
           :yhteensa {:nimi "Yhteensä"}})
        (assoc-in [:gridit :toimistokulut]
          {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
           :yhteenveto {:nimi "Toimistokulut, Pientarvikevarasto"}
           :yhteensa {:nimi "Yhteensä"}
           :kuukausitasolla? false})
        (assoc-in [:gridit :hoidonjohtopalkkio]
          {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
           :yhteenveto {:nimi "Hoidonjohtopalkkio"}
           :yhteensa {:nimi "Yhteensä"}
           :kuukausitasolla? false})
        (assoc-in [:gridit :tilaajan-varaukset]
          {:otsikot {:nimi "" :maara "Määrä €/kk" :yhteensa "Yhteensä" :indeksikorjattu "Indeksikorjattu"}
           :yhteenveto {:nimi "Tavoitehinnan ulkopuoliset rahavaraukset"}
           :yhteensa {:nimi "Yhteensä"}
           :kuukausitasolla? false})
        (assoc-in [:kattohinta :grid 0]
          (merge {:rivi :kattohinta}
            (into {} (map (fn [avain]
                            {avain 0}) kattohinta-grid-avaimet))))
        (assoc-in [:kattohinta :grid 1]
          (merge {:rivi :indeksikorjaukset}
            (into {} (map (fn [avain]
                            {avain 0}) kattohinta-grid-avaimet)))))))


  FiltereidenAloitusarvot
  (process-event [_ app]
    (-> app
      (assoc-in [:suodattimet :hankinnat :toimenpide] :talvihoito)))


  Oikeudet
  (process-event [_ app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)
          kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-suunnittelu-kustannussuunnittelu urakka-id)]
      (assoc-in app [:domain :kirjoitusoikeus?] kirjoitusoikeus?)))

  Hoitokausi
  (process-event [_ app]
    (assoc-in app [:domain :kuluva-hoitokausi] (kuluva-hoitokausi)))

  YleisSuodatinArvot
  (process-event [_ app]
    (let [urakan-alkupvm (-> @tiedot/tila :yleiset :urakka :alkupvm)
          ;; Jos urakan alkuvuosi sama kuin kuluva vuosi, asetetaann hoitokauden oletusnumero ykköseksi
          ;; Samposta voi tulla virheellisesti urakoita, joiden alkupvm määritelty 1.1.202X, vaikka oikeasti
          ;; alkavat 1.10.202X. Tämä käsittely estää UI:n kaatumisen moisessa tapauksessa, muuten toimii normaalisti
          default-hoitokausi (if (= (pvm/vuosi (pvm/nyt))
                                   (pvm/vuosi urakan-alkupvm))
                               1
                               (get-in app [:domain :kuluva-hoitokausi :hoitokauden-numero]))]
      (-> app
        (assoc-in [:suodattimet :hoitokauden-numero] default-hoitokausi)
        (assoc-in [:suodattimet :kopioidaan-tuleville-vuosille?] false))))

  HaeIndeksitOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc-in app [:domain :indeksit] vastaus))

  HaeIndeksitEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Indeksien haku epäonnistui!" :warning viesti/viestin-nayttoaika-pitka)
    app)

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

  ;; TODO: Tässä käsitellään myös paljon muutakin kuin "hankintakustannuksia"
  ;;       Eventin nimi pitäisi muotoilla paremmin...
  HaeHankintakustannuksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (let [{urakan-aloituspvm :alkupvm} (-> @tiedot/tila :yleiset :urakka)
          pohjadata (urakan-ajat)]
      (when pohjadata
        (let [;; Kiinteähintaiset hankinnat
              hankinnat (:kiinteahintaiset-tyot vastaus)
              ;; Kustannusarvioidut hankinnat
              hankinnat-laskutukseen-perustuen (filter #(and (= (:tyyppi %) "laskutettava-tyo")
                                                          (nil? (:haettu-asia %)))
                                                 (:kustannusarvioidut-tyot vastaus))
              toimenpiteet-joilla-laskutukseen-perustuvia-suunnitelmia (into #{}
                                                                         (distinct (sequence (comp
                                                                                               (remove #(= 0 (:summa %)))
                                                                                               (map :toimenpide-avain))
                                                                                     hankinnat-laskutukseen-perustuen)))
              rahavaraukset (distinct
                              (keep #(when (#{:rahavaraus-lupaukseen-1 :kolmansien-osapuolten-aiheuttamat-vahingot
                                              :akilliset-hoitotyot}
                                            (:haettu-asia %))
                                       (select-keys % #{:tyyppi :summa :summa-indeksikorjattu :toimenpide-avain
                                                        :vuosi :kuukausi}))
                                (:kustannusarvioidut-tyot vastaus)))
              hankinnat-toimenpiteittain (pohjadatan-taydennys-toimenpiteittain-fn pohjadata
                                           hankinnat
                                           toimenpiteet
                                           (fn [{:keys [vuosi kuukausi summa summa-indeksikorjattu] :as data}]
                                             #_(println "### hankinnat-toimenpiteittain: vuosi:" vuosi " kuukausi: " kuukausi " summa: " summa " indeksikorjattu: " summa-indeksikorjattu)
                                             (-> data
                                               ;; TODO: Assoc :indeksikorjattu <- summa-indeksikorjattu
                                               (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                      :maara summa)
                                               (dissoc :summa)
                                               #_(dissoc :summa-indeksikorjattu))))
              hankinnat-hoitokausille (into {}
                                        (map (fn [[toimenpide hankinnat]]
                                               [toimenpide (vec (vals (sort-by #(-> % key first)
                                                                        (fn [aika-1 aika-2]
                                                                          (pvm/ennen? aika-1 aika-2))
                                                                        (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                          hankinnat))))])
                                          hankinnat-toimenpiteittain))

              hankinnat-laskutukseen-perustuen-toimenpiteittain
              (pohjadatan-taydennys-toimenpiteittain-fn pohjadata hankinnat-laskutukseen-perustuen
                toimenpiteet
                (fn [{:keys [vuosi kuukausi summa summa-indeksikorjattu] :as data}]
                  #_(println "### hankinnat-laskutukseen-perustuen-toimenpiteittain: vuosi:" vuosi " kuukausi: " kuukausi " summa: " summa " indeksikorjattu: " summa-indeksikorjattu)

                  (-> data
                    ;; TODO: Assoc :indeksikorjattu <- summa-indeksikorjattu
                    (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                           :maara summa)
                    (dissoc :summa)
                    #_(dissoc :summa-indeksikorjattu))))

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
                                                        (pohjadatan-taydennys-toimenpiteittain-fn pohjadata rahavaraukset-tyypille
                                                          tyypin-toimenpiteet
                                                          (fn [{:keys [vuosi kuukausi summa summa-indeksikorjattu] :as data}]
                                                            #_(println "### rahavaraukset-toimenpiteittain: vuosi:" vuosi " kuukausi: " kuukausi " summa: " summa " indeksikorjattu: " summa-indeksikorjattu)

                                                            (-> data
                                                              ;; TODO: Assoc :indeksikorjattu <- summa-indeksikorjattu
                                                              (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                     :maara summa
                                                                     :tyyppi tyyppi)
                                                              (dissoc :summa)
                                                              #_(dissoc :summa-indeksikorjattu))))))
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

              ;; -- Määrätaulukoiden datan alustaminen --
              hoidon-johto-kustannukset (filter #(= (:toimenpide-avain %) :mhu-johto)
                                          (:kustannusarvioidut-tyot vastaus))
              erillishankinnat-hoitokausittain (hoidonjohto-jarjestys-fn
                                                 (maarataulukon-kk-data-alustus-fn
                                                   pohjadata hoidon-johto-kustannukset :erillishankinnat))
              toimistokulut-hoitokausittain (hoidonjohto-jarjestys-fn
                                              (maarataulukon-kk-data-alustus-fn
                                                pohjadata hoidon-johto-kustannukset :toimistokulut))
              hoidonjohtopalkkio-hoitokausittain (hoidonjohto-jarjestys-fn
                                                   (maarataulukon-kk-data-alustus-fn
                                                     pohjadata hoidon-johto-kustannukset :hoidonjohtopalkkio))
              ;; Tilaajan varauksia ei tarvitse erikseen hakea tietokannasta myöhemmin, koska sille ei lasketa indeksikorjauksia.
              tilaajan-varaukset-hoitokausittain (hoidonjohto-jarjestys-fn
                                                   (maarataulukon-kk-data-alustus-fn
                                                     pohjadata hoidon-johto-kustannukset :tilaajan-varaukset))


              ;; -- Johto- ja hallintokorvausten datan alustaminen --
              omat-jh-korvaukset (vec (reverse (sort-by #(get-in % [1 0 :toimenkuva])
                                                 (group-by :toimenkuva-id
                                                   (get-in vastaus [:johto-ja-hallintokorvaukset :omat])))))
              ;; Omat toimenkuvat = custom toimenkuvat
              vapaat-omien-toimekuvien-idt (clj-set/difference
                                             (into #{} (map :toimenkuva-id
                                                         (get-in vastaus [:johto-ja-hallintokorvaukset :omat-toimenkuvat])))
                                             (into #{} (distinct (map :toimenkuva-id (get-in vastaus [:johto-ja-hallintokorvaukset :omat])))))
              ;; Omia toimenkuvia saa olla kaksi riviä gridissä
              _ (when (> (count omat-jh-korvaukset) 2)
                  (modal/nayta! {:otsikko "Omia toimenkuvia liikaa!"}
                    [:div
                     [:span (str "Löytyi seuraavat omat toimenkuvat kannasta vaikka maksimi määrä on "
                              jh-korvausten-omiariveja-lkm ". Ota yhteyttä Harja-tiimiin.")]
                     [:ul
                      (doall (for [[_ data] omat-jh-korvaukset
                                   :let [toimenkuva (get-in data [0 :toimenkuva])]]
                               ^{:key toimenkuva}
                               [:li (str toimenkuva)]))]]))
              jh-korvaukset (merge
                              (reduce
                                (fn [korvaukset {:keys [toimenkuva kk-v maksukausi hoitokaudet]}]
                                  (let [asia-kannasta (reverse (sort-by :osa-kuukaudesta
                                                                 (filter (fn [jh-korvaus]
                                                                           (and (= (:toimenkuva jh-korvaus) toimenkuva)
                                                                             (= (:maksukausi jh-korvaus) maksukausi)))
                                                                   (get-in vastaus [:johto-ja-hallintokorvaukset :vakiot]))))
                                        data-koskee-ennen-urakkaa? (toimenpide-koskee-ennen-urakkaa? hoitokaudet)
                                        taytetty-jh-data
                                        (if data-koskee-ennen-urakkaa?
                                          (let [kannasta (filterv :ennen-urakkaa asia-kannasta)]
                                            (if (empty? kannasta)
                                              (let [arvot {:aika (pvm/luo-pvm (pvm/vuosi urakan-aloituspvm) 9 15)
                                                           :vuosi (pvm/vuosi urakan-aloituspvm)
                                                           :kk-v kk-v
                                                           :osa-kuukaudesta 1
                                                           :kuukausi 10}
                                                    kokonaiset (vec (repeat (js/Math.floor kk-v) arvot))
                                                    osittainen? (not= 0 (- kk-v (count kokonaiset)))]
                                                (if osittainen?
                                                  (conj kokonaiset (assoc arvot :osa-kuukaudesta (- kk-v (count kokonaiset))))
                                                  kokonaiset))
                                              kannasta))
                                          (pohjadatan-taydennys-fn pohjadata
                                            (vec (sort-by (juxt :vuosi :kuukausi) asia-kannasta))
                                            (fn [vuosi kuukausi]
                                              (cond
                                                (= maksukausi :kesa) (<= 5 kuukausi 9)
                                                (= maksukausi :talvi) (or (<= 1 kuukausi 4)
                                                                        (<= 10 kuukausi 12))
                                                (= toimenkuva "harjoittelija") (<= 5 kuukausi 8)
                                                (= toimenkuva "viherhoidosta vastaava henkilö") (<= 4 kuukausi 8)
                                                :else true))
                                            (fn [{:keys [vuosi kuukausi tunnit tuntipalkka tuntipalkka-indeksikorjattu] :as data}]
                                              #_(println "### jh-korvaukset toimenkuvat:" vuosi " kuukausi: " kuukausi " tuntipalkka: " tuntipalkka " indeksikorjattu: " tuntipalkka-indeksikorjattu)

                                              (-> data
                                                (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                       :tunnit (or tunnit nil)
                                                       :tuntipalkka (or tuntipalkka nil)
                                                       :tuntipalkka-indeksikorjattu (or tuntipalkka-indeksikorjattu nil)
                                                       :kk-v kk-v)
                                                (select-keys #{:aika :kk-v :tunnit :tuntipalkka :tuntipalkka-indeksikorjattu
                                                               :kuukausi :vuosi :osa-kuukaudesta})))))]
                                    (if data-koskee-ennen-urakkaa?
                                      (assoc korvaukset toimenkuva {maksukausi [taytetty-jh-data]})
                                      (update korvaukset toimenkuva
                                        (fn [maksukausien-arvot]
                                          (assoc maksukausien-arvot
                                            maksukausi (vec (vals (sort-by #(-> % key first)
                                                                    (fn [aika-1 aika-2]
                                                                      (pvm/ennen? aika-1 aika-2))
                                                                    (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                      taytetty-jh-data))))))))))
                                {}
                                johto-ja-hallintokorvaukset-pohjadata)
                              ;; Omat toimenkuvat
                              (first
                                (reduce
                                  (fn [[omat-korvaukset vapaat-omien-toimekuvien-idt] jarjestysnumero]
                                    (let [omanimi (jh-omienrivien-nimi jarjestysnumero)
                                          asia-kannasta (get-in omat-jh-korvaukset [(dec jarjestysnumero) 1])
                                          toimenkuva-id (if-let [tallennetun-korvauksen-toimenkuva-id (get-in asia-kannasta [0 :toimenkuva-id])]
                                                          tallennetun-korvauksen-toimenkuva-id
                                                          ;; Jos kantaan on tallennettu vain toimenkuvan nimi, muttei yhtään korvauksia kaivetaan
                                                          ;; toimenkuva-id siten, että täytetty kenttä tulee ylimmäksi. Jos mitään ei olla tallennettu,
                                                          ;; otetaan vain ensimmäinen vapaa id.
                                                          (or (some (fn [{:keys [toimenkuva-id toimenkuva]}]
                                                                      (when (and (contains? vapaat-omien-toimekuvien-idt toimenkuva-id)
                                                                              (not (nil? toimenkuva)))
                                                                        toimenkuva-id))
                                                                (get-in vastaus [:johto-ja-hallintokorvaukset :omat-toimenkuvat]))
                                                            (first vapaat-omien-toimekuvien-idt)))
                                          toimenkuva (some #(when (= (:toimenkuva-id %) toimenkuva-id)
                                                              (:toimenkuva %))
                                                       (get-in vastaus [:johto-ja-hallintokorvaukset :omat-toimenkuvat]))
                                          kuukaudet (into #{} (:maksukuukaudet (first asia-kannasta)))
                                          taytetty-jh-data
                                          (pohjadatan-taydennys-fn pohjadata
                                            (vec (sort-by (juxt :vuosi :kuukausi) asia-kannasta))
                                            (constantly true)
                                            (fn [{:keys [vuosi kuukausi tunnit tuntipalkka tuntipalkka-indeksikorjattu] :as data}]
                                              #_(println "### jh-korvaukset omat toimenkuvat:" vuosi " kuukausi: " kuukausi " tuntipalkka: " tuntipalkka " indeksikorjattu: " tuntipalkka-indeksikorjattu)

                                              (let [data (-> data
                                                           (assoc :aika (pvm/luo-pvm vuosi (dec kuukausi) 15)
                                                                  :toimenkuva toimenkuva
                                                                  :toimenkuva-id toimenkuva-id
                                                                  :tunnit (or tunnit nil)
                                                                  :tuntipalkka (or tuntipalkka nil)
                                                                  :tuntipalkka-indeksikorjattu (or tuntipalkka-indeksikorjattu nil)
                                                                  :maksukuukaudet kuukaudet)
                                                           (select-keys #{:aika :toimenkuva-id :toimenkuva :tunnit
                                                                          :tuntipalkka :tuntipalkka-indeksikorjattu
                                                                          :kuukausi :vuosi :osa-kuukaudesta :maksukuukaudet}))]
                                                (if (contains? kuukaudet kuukausi)
                                                  data
                                                  (dissoc data :tunnit :tuntipalkka :tuntipalkka-indeksikorjattu)))))]
                                      [(assoc omat-korvaukset omanimi (vec (vals (sort-by #(-> % key first)
                                                                                   (fn [aika-1 aika-2]
                                                                                     (pvm/ennen? aika-1 aika-2))
                                                                                   (group-by #(pvm/paivamaaran-hoitokausi (:aika %))
                                                                                     taytetty-jh-data)))))
                                       (disj vapaat-omien-toimekuvien-idt toimenkuva-id)]))
                                  [{} vapaat-omien-toimekuvien-idt]
                                  (range 1 (inc jh-korvausten-omiariveja-lkm)))))
              kuluva-hoitokauden-numero (get-in app [:domain :kuluva-hoitokausi :hoitokauden-numero])

              ;; -- App-tila --
              app (reduce (fn [app jarjestysnumero]
                            (let [nimi (jh-omienrivien-nimi jarjestysnumero)
                                  maksukausi (mhu/kuukausi->maksukausi
                                               (count (get-in omat-jh-korvaukset [(dec jarjestysnumero) 1 0 :maksukuukaudet])))
                                  ;; Jos maksukuukausia ei löydy, niin default = :molemmat
                                  maksukausi (or maksukausi :molemmat)
                                  toimenkuva (get-in jh-korvaukset [nimi 0 0 :toimenkuva])]
                              (-> app
                                (assoc-in [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :maksukausi] maksukausi)
                                (assoc-in [:gridit :johto-ja-hallintokorvaukset :yhteenveto nimi :toimenkuva] toimenkuva))))
                    app
                    (range 1 (inc jh-korvausten-omiariveja-lkm)))]
          (-> app
            ;; Koosta domain tilat
            (assoc-in [:domain :suunnitellut-hankinnat] hankinnat-hoitokausille)
            (assoc-in [:domain :laskutukseen-perustuvat-hankinnat] hankinnat-laskutukseen-perustuen)
            (assoc-in [:domain :rahavaraukset] rahavaraukset-hoitokausille)
            (assoc-in [:domain :erillishankinnat] erillishankinnat-hoitokausittain)
            (assoc-in [:domain :johto-ja-hallintokorvaukset] jh-korvaukset)
            (assoc-in [:domain :toimistokulut] toimistokulut-hoitokausittain)
            (assoc-in [:domain :hoidonjohtopalkkio] hoidonjohtopalkkio-hoitokausittain)
            (assoc-in [:domain :tilaajan-varaukset] tilaajan-varaukset-hoitokausittain)

            ;; Koosta yhteenvedot
            (assoc-in [:yhteenvedot :hankintakustannukset :summat :suunnitellut-hankinnat]
              (reduce (fn [summat [toimenpide summat-hoitokausittain]]
                        (assoc summat toimenpide (mapv (fn [summat-kuukausittain]
                                                         (reduce #(+ %1 (:maara %2)) 0 summat-kuukausittain))
                                                   summat-hoitokausittain)))
                {}
                hankinnat-hoitokausille))

            ;; TODO: Indeksikorjatut summat talteen
            #_(assoc-in [:yhteenvedot :hankintakustannukset :indeksikorjatut-summat :suunnitellut-hankinnat]
              (reduce (fn [summat [toimenpide summat-hoitokausittain]]
                        (assoc summat toimenpide (mapv (fn [summat-kuukausittain]
                                                         (reduce #(+ %1 (:indeksikorjattu %2)) 0 summat-kuukausittain))
                                                   summat-hoitokausittain)))
                {}
                hankinnat-hoitokausille))

            (assoc-in [:yhteenvedot :hankintakustannukset :summat :laskutukseen-perustuvat-hankinnat]
              (reduce (fn [summat [toimenpide summat-hoitokausittain]]
                        (assoc summat toimenpide (mapv (fn [summat-kuukausittain]
                                                         (reduce #(+ %1 (:maara %2)) 0 summat-kuukausittain))
                                                   summat-hoitokausittain)))
                {}
                hankinnat-laskutukseen-perustuen))

            ;; TODO: Indeksikorjatut summat talteen
            #_(assoc-in [:yhteenvedot :hankintakustannukset :indeksikorjatut-summat :laskutukseen-perustuvat-hankinnat]
              (reduce (fn [summat [toimenpide summat-hoitokausittain]]
                        (assoc summat toimenpide (mapv (fn [summat-kuukausittain]
                                                         (reduce #(+ %1 (:indeksikorjattu %2)) 0 summat-kuukausittain))
                                                   summat-hoitokausittain)))
                {}
                hankinnat-laskutukseen-perustuen))

            (assoc-in [:yhteenvedot :hankintakustannukset :summat :rahavaraukset]
              (reduce (fn [summat [toimenpide toimenpiteen-rahavaraukset]]
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

            ;; TODO: Indeksikorjatut summat talteen. (Yhteenvedon koostamista voisi samalla yksinkertaistaa, että ei toisteta samaa koodia...)
            #_(assoc-in [:yhteenvedot :hankintakustannukset :summat :rahavaraukset]
              (reduce (fn [summat [toimenpide toimenpiteen-rahavaraukset]]
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
                                                   (reduce #(+ %1 (:indeksikorjattu %2)) 0 hoitokauden-maarat))
                                              maarat-hoitokausittain)))))
                              toimenpiteen-summat
                              toimenpiteen-rahavaraukset))))
                {}
                rahavaraukset-hoitokausille))

            (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :erillishankinnat]
              (mapv #(summaa-mapin-arvot % :maara) erillishankinnat-hoitokausittain))
            (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :indeksikorjatut-summat :erillishankinnat]
              (mapv #(summaa-mapin-arvot % :indeksikorjattu) erillishankinnat-hoitokausittain))

            (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :toimistokulut]
              (mapv #(summaa-mapin-arvot % :maara) toimistokulut-hoitokausittain))
            (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :indeksikorjatut-summat :toimistokulut]
              (mapv #(summaa-mapin-arvot % :indeksikorjattu) toimistokulut-hoitokausittain))

            (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :summat :hoidonjohtopalkkio]
              (mapv #(summaa-mapin-arvot % :maara) hoidonjohtopalkkio-hoitokausittain))
            (assoc-in [:yhteenvedot :johto-ja-hallintokorvaukset :indeksikorjatut-summat :hoidonjohtopalkkio]
              (mapv #(summaa-mapin-arvot % :indeksikorjattu) hoidonjohtopalkkio-hoitokausittain))

            ;; Suodattimet
            (assoc-in [:suodattimet :hankinnat :laskutukseen-perustuen-valinta]
              toimenpiteet-joilla-laskutukseen-perustuvia-suunnitelmia)

            (assoc :kantahaku-valmis? true))))))

  HaeHankintakustannuksetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    ;; TODO
    (viesti/nayta! "Hankintakustannusten haku epäonnistui..."
      :warning viesti/viestin-nayttoaika-pitka)
    app)

  HaeKustannussuunnitelmanTilat
  (process-event [_ app]
    (let [urakka-id (-> @tiedot/tila :yleiset :urakka :id)]
      (tuck-apurit/post! app
        :hae-suunnitelman-tilat
        {:urakka-id urakka-id}
        {:onnistui ->HaeKustannussuunnitelmanTilatOnnistui
         :epaonnistui ->HaeKustannussuunnitelmanTilatEpaonnistui
         :paasta-virhe-lapi? true})))

  HaeKustannussuunnitelmanTilatOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc-in app [:domain :osioiden-tilat] vastaus))

  HaeKustannussuunnitelmanTilatEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Tilojen haku epäonnistui!" :warning viesti/viestin-nayttoaika-pitka)
    app)

  HaeBudjettitavoite
  (process-event [_ app]
    (tuck-apurit/post! :budjettitavoite
      {:urakka-id (-> @tiedot/yleiset :urakka :id)}
      {:onnistui ->HaeBudjettitavoiteOnnistui
       :epaonnistui ->HaeBudjettitavoiteEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeBudjettitavoiteOnnistui
  (process-event [{vastaus :vastaus} app]
    (if (seq vastaus)
      (-> app
        (assoc-in [:kattohinta :grid 0] (merge {:rivi :kattohinta}
                                          (into {} (map (fn [{:keys [kattohinta hoitokausi]}]
                                                          {(keyword (str "kattohinta-vuosi-" hoitokausi))
                                                           kattohinta}) vastaus))))
        (assoc-in [:kattohinta :grid 1] (merge {:rivi :indeksikorjaukset}
                                          (into {} (map (fn [{:keys [kattohinta_indeksikorjattu hoitokausi]}]
                                                          {(keyword (str "kattohinta-vuosi-" hoitokausi))
                                                           kattohinta_indeksikorjattu}) vastaus)))))
      app))

  HaeBudjettitavoiteEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Kattohinnan ja tavoitteen haku epäonnistui!" :danger)
    app)


  ;; ----


  MaksukausiValittu
  (process-event [_ app]
    (let [maksetaan (get-in app [:suodattimet :hankinnat :maksetaan])
          maksu-kk (case maksetaan
                     :kesakausi [5 9]
                     :talvikausi [10 4]
                     :molemmat [1 12])
          piilotetaan? (fn [kk]
                         (case maksetaan
                           :kesakausi (or (< kk (first maksu-kk))
                                        (> kk (second maksu-kk)))
                           :talvikausi (and (< kk (first maksu-kk))
                                         (> kk (second maksu-kk)))
                           :molemmat false))
          g-sh (get-in app [:gridit :suunnitellut-hankinnat :grid])
          g-hlp (get-in app [:gridit :laskutukseen-perustuvat-hankinnat :grid])
          nakyvyydet-fn! (fn [g]
                           (doseq [otsikko-datasisalto (grid/hae-grid (grid/get-in-grid g [::g-pohjat/data]) :lapset)]
                             (grid/paivita-grid! (grid/get-in-grid otsikko-datasisalto
                                                   ;; NOTE: Tämä on sama viittaus kuin kustannussuunnitelman viewin osioissa on ::t/data-sisalto,
                                                   ;;       jossa "t" viittaa tähän mhu_kustannussuunnitelma.cljs nimiavaruuteen.
                                                   ;;       Näiden viittausten kanssa saa olla tarkkana, varsinkin jos tiedostoja jakaa useampaan osaan!
                                                   [:harja.tiedot.urakka.suunnittelu.mhu-kustannussuunnitelma/data-sisalto])
                               :lapset
                               (fn [rivit]
                                 (mapv (fn [rivi]
                                         (if (-> rivi (grid/get-in-grid [0]) grid/solun-arvo pvm/kuukausi piilotetaan?)
                                           (grid/piilota! rivi)
                                           (grid/nayta! rivi))
                                         rivi)
                                   rivit)))))]
      (nakyvyydet-fn! g-sh)
      (nakyvyydet-fn! g-hlp)
      app))

  TallennaHankintojenArvot
  (process-event [{:keys [tallennettava-asia hoitokauden-numero tunnisteet]} app]
    (if-not (get-in app [:domain :muutosten-vahvistus :vaaditaan-muutosten-vahvistus?]) ; jos vahvistusikkuna on auki, niin vahvistusikkunan klikkaus triggaa blureventin. se tulee tänne ja me ei haluta sitä, kun sitten tulee väärät tiedot vahvistettavaksi. skipataan siis koko roska.
      (let [osio-kw (mhu/tallennettava-asia->suunnitelman-osio tallennettava-asia)
            {urakka-id :id} (:urakka @tiedot/yleiset)
            post-kutsu (case tallennettava-asia
                         :hankintakustannus :tallenna-kiinteahintaiset-tyot
                         :laskutukseen-perustuva-hankinta :tallenna-kustannusarvioitu-tyo)
            valittu-toimenpide (get-in app [:suodattimet :hankinnat :toimenpide])
            kopioidaan-tuleville-vuosille? (get-in app [:suodattimet :hankinnat :kopioidaan-tuleville-vuosille?])
            paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                                (range hoitokauden-numero 6)
                                                [hoitokauden-numero])
            summa (case tallennettava-asia
                    :hankintakustannus
                    (get-in app [:domain :suunnitellut-hankinnat valittu-toimenpide (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])

                    :laskutukseen-perustuva-hankinta
                    (get-in app [:domain :laskutukseen-perustuvat-hankinnat valittu-toimenpide (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara]))
            ajat (vec (mapcat (fn [{:keys [osan-paikka]}]
                                (mapv (fn [hoitokauden-numero]
                                        (let [polun-osa (case tallennettava-asia
                                                          :hankintakustannus :suunnitellut-hankinnat
                                                          :laskutukseen-perustuva-hankinta :laskutukseen-perustuvat-hankinnat)]
                                          (select-keys (get-in app [:domain polun-osa valittu-toimenpide (dec hoitokauden-numero) (first osan-paikka)])
                                            #{:vuosi :kuukausi})))
                                  paivitettavat-hoitokauden-numerot))
                        tunnisteet))
            lahetettava-data (case tallennettava-asia
                               :hankintakustannus {:osio osio-kw
                                                   :urakka-id urakka-id
                                                   :toimenpide-avain valittu-toimenpide
                                                   :summa summa
                                                   :ajat ajat}
                               :laskutukseen-perustuva-hankinta {:osio osio-kw
                                                                 :urakka-id urakka-id
                                                                 :toimenpide-avain valittu-toimenpide
                                                                 :tallennettava-asia :toimenpiteen-maaramitattavat-tyot
                                                                 :summa summa
                                                                 :ajat ajat})
            onko-osiolla-tila? (hae-osion-tilat app
                                 (mhu/tallennettava-asia->suunnitelman-osio tallennettava-asia)
                                 paivitettavat-hoitokauden-numerot)
            vahvistettavat-vuodet (osion-vahvistettavat-vuodet app osio-kw paivitettavat-hoitokauden-numerot)
            tiedot {:palvelu post-kutsu
                    :payload (dissoc-nils lahetettava-data)
                    :onnistui ->TallennaHankintojenArvotOnnistui
                    :epaonnistui ->TallennaHankintojenArvotEpaonnistui}]
        (println "tallenna hankintojen arvot" tallennettava-asia lahetettava-data)

        (when-not onko-osiolla-tila?
          (laheta-ja-odota-vastaus app
            {:palvelu :tallenna-suunnitelman-osalle-tila
             ;; TODO: Tyyppi = osio, korjaa termi
             :payload {:tyyppi osio-kw
                       :urakka-id urakka-id
                       :hoitovuodet paivitettavat-hoitokauden-numerot}
             :onnistui ->TallennaKustannussuunnitelmanOsalleTilaOnnistui
             :epaonnistui ->TallennaKustannussuunnitelmanOsalleTilaEpaonnistui}))

        (if (empty? vahvistettavat-vuodet)
          (do
            (tallenna-tavoite-ja-kattohinnat app)
            (laheta-ja-odota-vastaus app tiedot))
          (kysy-muutosten-vahvistus app osio-kw vahvistettavat-vuodet tiedot)))
      app))
  TallennaHankintojenArvotOnnistui
  (process-event [{:keys [vastaus]} app]
    app)

  TallennaHankintojenArvotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
      :warning viesti/viestin-nayttoaika-pitka)
    app)



  ;; ----


  TallennaKustannusarvoitu
  (process-event [{tallennettava-asia :tallennettava-asia tunnisteet :tunnisteet
                   {:keys [onnistui-event epaonnistui-event] :as opts} :opts} app]
    (let [osio-kw (mhu/tallennettava-asia->suunnitelman-osio tallennettava-asia)
          {urakka-id :id} (:urakka @tiedot/yleiset)
          post-kutsu :tallenna-kustannusarvioitu-tyo
          hoitokauden-numero (get-in app [:suodattimet :hoitokauden-numero])
          valittu-toimenpide (get-in app [:suodattimet :hankinnat :toimenpide])
          kopioidaan-tuleville-vuosille? (get-in app [:suodattimet :kopioidaan-tuleville-vuosille?])
          paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                              (range hoitokauden-numero 6)
                                              [hoitokauden-numero])
          summa (case tallennettava-asia
                  :kolmansien-osapuolten-aiheuttamat-vahingot
                  (get-in app [:domain :rahavaraukset valittu-toimenpide "vahinkojen-korjaukset" (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])

                  :akilliset-hoitotyot
                  (get-in app [:domain :rahavaraukset valittu-toimenpide "akillinen-hoitotyo" (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])

                  :rahavaraus-lupaukseen-1
                  (get-in app [:domain :rahavaraukset valittu-toimenpide "muut-rahavaraukset" (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])

                  :erillishankinnat
                  (get-in app [:domain :erillishankinnat (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])

                  :toimistokulut
                  (get-in app [:domain :toimistokulut (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])

                  :hoidonjohtopalkkio
                  (get-in app [:domain :hoidonjohtopalkkio (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara])

                  :tilaajan-varaukset
                  (get-in app [:domain :tilaajan-varaukset (dec hoitokauden-numero) (get-in tunnisteet [0 :osan-paikka 0]) :maara]))
          ajat (vec (mapcat
                      (fn [{:keys [osan-paikka]}]
                        (mapv
                          (fn [hoitokauden-numero]
                            (let [polku (case tallennettava-asia
                                          :kolmansien-osapuolten-aiheuttamat-vahingot
                                          [:domain :rahavaraukset valittu-toimenpide "vahinkojen-korjaukset" (dec hoitokauden-numero) (first osan-paikka)]

                                          :akilliset-hoitotyot
                                          [:domain :rahavaraukset valittu-toimenpide "akillinen-hoitotyo" (dec hoitokauden-numero) (first osan-paikka)]

                                          :rahavaraus-lupaukseen-1
                                          [:domain :rahavaraukset valittu-toimenpide "muut-rahavaraukset" (dec hoitokauden-numero) (first osan-paikka)]

                                          :erillishankinnat
                                          [:domain :erillishankinnat (dec hoitokauden-numero) (first osan-paikka)]

                                          :toimistokulut
                                          [:domain :toimistokulut (dec hoitokauden-numero) (first osan-paikka)]

                                          :hoidonjohtopalkkio
                                          [:domain :hoidonjohtopalkkio (dec hoitokauden-numero) (first osan-paikka)]

                                          :tilaajan-varaukset
                                          [:domain :tilaajan-varaukset (dec hoitokauden-numero) (first osan-paikka)])]
                              (select-keys (get-in app polku)
                                #{:vuosi :kuukausi})))
                          paivitettavat-hoitokauden-numerot))
                      tunnisteet))

          lahetettava-data (merge
                             ;; Kaikkiin case conditioneihin mukaan tuleva data.
                             {:osio osio-kw}
                             (case tallennettava-asia
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
                                                       :ajat ajat}))
          onko-osiolla-tila? (hae-osion-tilat app
                               (mhu/tallennettava-asia->suunnitelman-osio tallennettava-asia)
                               paivitettavat-hoitokauden-numerot)
          vahvistettavat-vuodet (osion-vahvistettavat-vuodet app osio-kw paivitettavat-hoitokauden-numerot)
          tiedot {:palvelu post-kutsu
                  :payload (dissoc-nils lahetettava-data)
                  :onnistui (or onnistui-event ->TallennaKustannusarvoituOnnistui)
                  :epaonnistui (or epaonnistui-event ->TallennaKustannusarvoituEpaonnistui)}]

      (log/debug "Tallenna kustannusarvioitu" tallennettava-asia lahetettava-data)

      (when-not onko-osiolla-tila?
        (laheta-ja-odota-vastaus app
          {:palvelu :tallenna-suunnitelman-osalle-tila
           ;; TODO: Tyyppi = osio, korjaa termi
           :payload {:tyyppi osio-kw
                     :urakka-id urakka-id
                     :hoitovuodet paivitettavat-hoitokauden-numerot}
           :onnistui ->TallennaKustannussuunnitelmanOsalleTilaOnnistui
           :epaonnistui ->TallennaKustannussuunnitelmanOsalleTilaEpaonnistui}))

      (if (empty? vahvistettavat-vuodet)
        (do
          (tallenna-tavoite-ja-kattohinnat app)
          (laheta-ja-odota-vastaus app tiedot))
        (kysy-muutosten-vahvistus app osio-kw vahvistettavat-vuodet tiedot))))

  TallennaKustannusarvoituOnnistui
  (process-event [{:keys [vastaus]} app]
    (log/debug "TallennaKustannusArvioituOnnistui")
    app)

  TallennaKustannusarvoituEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
      :warning viesti/viestin-nayttoaika-pitka)
    app)

  TallennaErillishankinnatOnnistui
  (process-event [{:keys [vastaus]} app]
    (log/debug "TallennaErillishankinnatOnnistui")

    (let [pohjadata (urakan-ajat)]
      (when pohjadata
        (let [;; -- Määrätaulukoiden datan alustaminen --
              hoidon-johto-kustannukset (filter #(= (:toimenpide-avain %) :mhu-johto)
                                          (:kustannusarvioidut-tyot vastaus))
              erillishankinnat-hoitokausittain (hoidonjohto-jarjestys-fn
                                                 (maarataulukon-kk-data-alustus-fn pohjadata
                                                   hoidon-johto-kustannukset :erillishankinnat))]

          ;; -- App-tila --
          (-> app
            (assoc-in [:domain :erillishankinnat] erillishankinnat-hoitokausittain))))))

  TallennaHoidonjohtopalkkioOnnistui
  (process-event [{:keys [vastaus]} app]
    (log/debug "TallennaHoidonjohtopalkkioOnnistui")

    (let [pohjadata (urakan-ajat)]
      (when pohjadata
        (let [;; -- Määrätaulukoiden datan alustaminen --
              hoidon-johto-kustannukset (filter #(= (:toimenpide-avain %) :mhu-johto)
                                          (:kustannusarvioidut-tyot vastaus))
              hoidonjohtopalkkio-hoitokausittain (hoidonjohto-jarjestys-fn
                                                   (maarataulukon-kk-data-alustus-fn pohjadata
                                                     hoidon-johto-kustannukset :hoidonjohtopalkkio))]

          ;; -- App-tila --
          (-> app
            (assoc-in [:domain :hoidonjohtopalkkio] hoidonjohtopalkkio-hoitokausittain))))))

  TallennaToimistokulutOnnistui
  (process-event [{:keys [vastaus]} app]
    (log/debug "TallennaToimistokulutOnnistui")

    (let [pohjadata (urakan-ajat)]
      (when pohjadata
        (let [;; -- Määrätaulukoiden datan alustaminen --
              hoidon-johto-kustannukset (filter #(= (:toimenpide-avain %) :mhu-johto)
                                          (:kustannusarvioidut-tyot vastaus))
              toimistokulut-hoitokausittain (hoidonjohto-jarjestys-fn
                                              (maarataulukon-kk-data-alustus-fn pohjadata
                                                hoidon-johto-kustannukset :toimistokulut))]

          ;; -- App-tila --
          (-> app
            (assoc-in [:domain :toimistokulut] toimistokulut-hoitokausittain))))))

  ;;

  ;; NOTE: Johto- ja hallintokorvaukset sisältää vain yhdestä osiosta tulevaa dataa ja se tallennetaan vain yhteen tauluun.
  ;;       Toistaiseksi ei ole siis tarpeen tarkkailla mistä osiosta data on relevanttiin tauluun tallennettu.
  TallennaJohtoJaHallintokorvaukset
  (process-event [{:keys [tunnisteet]} app]
    (let [osio-kw :johto-ja-hallintokorvaus
          {urakka-id :id} (:urakka @tiedot/yleiset)
          post-kutsu :tallenna-johto-ja-hallintokorvaukset
          hoitokauden-numero (get-in app [:suodattimet :hoitokauden-numero])
          kopioidaan-tuleville-vuosille? (get-in app [:suodattimet :kopioidaan-tuleville-vuosille?])
          paivitettavat-hoitokauden-numerot (if kopioidaan-tuleville-vuosille?
                                              (range hoitokauden-numero 6)
                                              [hoitokauden-numero])
          {tunnisteen-maksukausi :maksukausi tunnisteen-toimenkuva :toimenkuva
           tunnisteen-osan-paikka :osan-paikka tunnisteen-omanimi :omanimi} (get tunnisteet 0)

          jhk-tiedot
          (vec (mapcat
                 (fn [{:keys [omanimi osan-paikka data-koskee-ennen-urakkaa?]}]
                   (if data-koskee-ennen-urakkaa?
                     (mapv (fn [m]
                             (select-keys m #{:vuosi :kuukausi :osa-kuukaudesta :tunnit :tuntipalkka}))
                       (get-in app [:domain :johto-ja-hallintokorvaukset tunnisteen-toimenkuva tunnisteen-maksukausi
                                    (dec hoitokauden-numero)]))

                     (keep (fn [hoitokauden-numero]
                             (let [tiedot (if omanimi
                                            (select-keys
                                              (get-in app [:domain :johto-ja-hallintokorvaukset omanimi (dec hoitokauden-numero) (first osan-paikka)])
                                              #{:vuosi :kuukausi :tunnit :tuntipalkka :toimenkuva-id :osa-kuukaudesta})
                                            (select-keys
                                              (get-in app [:domain :johto-ja-hallintokorvaukset tunnisteen-toimenkuva tunnisteen-maksukausi (dec hoitokauden-numero) (first osan-paikka)])
                                              #{:vuosi :kuukausi :osa-kuukaudesta :tunnit :tuntipalkka}))]
                               (cond
                                 (empty? tiedot) nil
                                 (contains? tiedot :osa-kuukaudesta) tiedot
                                 :else (assoc tiedot :osa-kuukaudesta 1))))
                       paivitettavat-hoitokauden-numerot)))
                 tunnisteet))
          omanimi (get-in tunnisteet [0 :omanimi])
          toimenkuva-id (when omanimi
                          (get-in app [:domain :johto-ja-hallintokorvaukset omanimi
                                       (dec hoitokauden-numero) (first tunnisteen-osan-paikka)
                                       :toimenkuva-id]))
          oman-rivin-maksukausi (when omanimi
                                  (or
                                    (mhu/kuukausi->maksukausi
                                      (count (get-in app [:domain :johto-ja-hallintokorvaukset omanimi
                                                          (dec hoitokauden-numero) (first tunnisteen-osan-paikka)
                                                          :maksukuukaudet])))
                                    ;; Jos maksukautta ei löydy kuukaudella, niin default on :molemmat.
                                    :molemmat))
          lahetettava-data (merge {:urakka-id urakka-id
                                   :ennen-urakkaa? (and (nil? tunnisteen-maksukausi)
                                                     (nil? tunnisteen-omanimi))
                                   :jhk-tiedot (mapv dissoc-nils jhk-tiedot)}
                             ;; Itsetäytetyillä rivillä on id. Vakioilla ei.
                             (if toimenkuva-id
                               {:toimenkuva-id toimenkuva-id}
                               {:toimenkuva tunnisteen-toimenkuva})
                             (when oman-rivin-maksukausi
                               {:maksukausi oman-rivin-maksukausi}))
          onko-osiolla-tila? (hae-osion-tilat app osio-kw paivitettavat-hoitokauden-numerot)
          vahvistettavat-vuodet (osion-vahvistettavat-vuodet app osio-kw paivitettavat-hoitokauden-numerot)
          tiedot {:palvelu post-kutsu
                  :payload lahetettava-data
                  :onnistui ->TallennaJohtoJaHallintokorvauksetOnnistui
                  :epaonnistui ->TallennaJohtoJaHallintokorvauksetEpaonnistui}]
      (when-not onko-osiolla-tila?
        (laheta-ja-odota-vastaus app
          {:palvelu :tallenna-suunnitelman-osalle-tila
           ;; TODO: Tyyppi = osio, korjaa termi
           :payload {:tyyppi osio-kw
                     :urakka-id urakka-id
                     :hoitovuodet paivitettavat-hoitokauden-numerot}
           :onnistui ->TallennaKustannussuunnitelmanOsalleTilaOnnistui
           :epaonnistui ->TallennaKustannussuunnitelmanOsalleTilaEpaonnistui}))
      (if (empty? vahvistettavat-vuodet)
        (do
          (tallenna-tavoite-ja-kattohinnat app)
          (laheta-ja-odota-vastaus app tiedot))
        (kysy-muutosten-vahvistus app osio-kw vahvistettavat-vuodet tiedot))))

  TallennaJohtoJaHallintokorvauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    app)

  TallennaJohtoJaHallintokorvauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
      :warning viesti/viestin-nayttoaika-pitka)
    app)

  PoistaOmaJHDdata
  (process-event [{:keys [sarake nimi maksukausi piilota-modal! paivita-ui! modal-fn!]} app]
    (let [poistettava-kustannus? (fn [{:keys [tunnit kuukausi]}]
                                   (and tunnit
                                     (not= 0 tunnit)
                                     (or (= sarake :toimenkuva)
                                       (not (kuukausi-kuuluu-maksukauteen? kuukausi maksukausi)))))
          data-hoitokausittain (vec
                                 (keep (fn [hoitokauden-korvaukset]
                                         (let [hoitokauden-korvaukset (filterv (fn [kustannus]
                                                                                 (poistettava-kustannus? kustannus))
                                                                        hoitokauden-korvaukset)]
                                           (when-not (empty? hoitokauden-korvaukset)
                                             hoitokauden-korvaukset)))
                                   (get-in app [:domain :johto-ja-hallintokorvaukset nimi])))
          toimenkuva (get-in data-hoitokausittain [0 0 :toimenkuva])
          poista! (fn []
                    (*e!* (reify tuck/Event
                            (process-event [_ app]
                              (let [{urakka-id :id} (:urakka @tiedot/yleiset)
                                    jhk-tiedot (vec (mapcat (fn [hoitokauden-data]
                                                              (map (fn [data]
                                                                     (assoc (select-keys data #{:vuosi :kuukausi :toimenkuva-id})
                                                                       :osa-kuukaudesta 1))
                                                                hoitokauden-data))
                                                      data-hoitokausittain))
                                    toimenkuva-id (get-in jhk-tiedot [0 :toimenkuva-id])
                                    lahetettava-data {:urakka-id urakka-id
                                                      :ennen-urakkaa? false
                                                      :jhk-tiedot jhk-tiedot
                                                      :toimenkuva-id toimenkuva-id
                                                      :maksukausi maksukausi}
                                    app (update-in app
                                          [:domain :johto-ja-hallintokorvaukset nimi]
                                          (fn [jh-korvaukset]
                                            (mapv (fn [hoitokausikohtaiset]
                                                    (vec (keep (fn [kustannus]
                                                                 (when-not (poistettava-kustannus? kustannus)
                                                                   kustannus))
                                                           hoitokausikohtaiset)))
                                              jh-korvaukset)))]
                                (piilota-modal!)
                                (paivita-ui!)
                                (laheta-ja-odota-vastaus app
                                  {:palvelu :tallenna-johto-ja-hallintokorvaukset
                                   :payload lahetettava-data
                                   :onnistui ->PoistaOmaJHDdataOnnistui
                                   :epaonnistui ->PoistaOmaJHDdataEpaonnistui}))))))
          valittu-hoitokauden-numero (get-in app [:suodattimet :hoitokauden-numero])
          vanhat-arvot (get-in app [:domain :johto-ja-hallintokorvaukset nimi (dec valittu-hoitokauden-numero)])]
      (if-not (empty? data-hoitokausittain)
        (modal-fn! toimenkuva
          (mapv (fn [hoitokauden-korvaukset]
                  (mapv (fn [korvaus]
                          (clj-set/rename-keys korvaus {:tunnit :maara}))
                    hoitokauden-korvaukset))
            data-hoitokausittain)
          poista!
          vanhat-arvot)
        (paivita-ui!))
      app))

  PoistaOmaJHDdataOnnistui
  (process-event [_ app]
    app)

  PoistaOmaJHDdataEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Tallennus epäonnistui..."
      :warning viesti/viestin-nayttoaika-pitka)
    app)

  MuutaOmanJohtoJaHallintokorvauksenArvoa
  (process-event [{:keys [nimi sarake arvo]} app]
    (let [hoitokauden-numero (get-in app [:domain :kuluva-hoitokausi :hoitokauden-numero])]
      (assoc-in app [:domain :johto-ja-hallintokorvaukset nimi (dec hoitokauden-numero) sarake] arvo)))


  ;; ----

  TallennaJaPaivitaTavoiteSekaKattohinta
  (process-event [_ {:keys [yhteenvedot] :as app}]
    (tallenna-tavoite-ja-kattohinnat app))

  TallennaJaPaivitaTavoiteSekaKattohintaOnnistui
  (process-event [_ app]
    app)

  TallennaJaPaivitaTavoiteSekaKattohintaEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Tallennus epäonnistui..."
      :warning viesti/viestin-nayttoaika-pitka)
    app)


  ;; ----

  TallennaToimenkuva
  (process-event [{:keys [rivin-nimi]} app]
    (let [{urakka-id :id} (:urakka @tiedot/yleiset)
          toimenkuva-id (get-in app [:domain :johto-ja-hallintokorvaukset rivin-nimi 0 0 :toimenkuva-id])
          toimenkuva-nimi (get-in app [:domain :johto-ja-hallintokorvaukset rivin-nimi 0 0 :toimenkuva])
          lahetettava-data {:urakka-id urakka-id
                            :toimenkuva-id toimenkuva-id
                            :toimenkuva toimenkuva-nimi}]
      (println "Tallenna toimenkuva")
      (laheta-ja-odota-vastaus app
        {:palvelu :tallenna-toimenkuva
         :payload lahetettava-data
         :onnistui ->TallennaToimenkuvaOnnistui
         :epaonnistui ->TallennaToimenkuvaEpaonnistui})))

  TallennaToimenkuvaOnnistui
  (process-event [{:keys [vastaus]} app]
    app)

  TallennaToimenkuvaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Tallennus epäonnistui..."
      :warning viesti/viestin-nayttoaika-pitka)
    app)


  ;; ----

  TallennaKustannussuunnitelmanOsalleTila
  (process-event [{:keys [parametrit]} app]
    (let [palvelu :tallenna-suunnitelman-osalle-tila
          urakka (-> @tiedot/tila :yleiset :urakka)
          {:keys [hoitovuosi]} parametrit
          payload {:urakka-id urakka}]
      (println "tilan tallennus")

      (laheta-ja-odota-vastaus app
        {:palvelu palvelu
         :payload payload
         :onnistui ->TallennaKustannussuunnitelmanOsalleTilaOnnistui
         :epaonnistui ->TallennaKustannussuunnitelmanOsalleTilaEpaonnistui})))

  TallennaKustannussuunnitelmanOsalleTilaOnnistui
  (process-event [{:keys [vastaus]} app]
    (println "vastaus" vastaus)
    (assoc-in app [:domain :osioiden-tilat] vastaus))

  TallennaKustannussuunnitelmanOsalleTilaEpaonnistui
  (process-event [_ app]
    (viesti/nayta! "Tilojen tallennus epäonnistui!" :warning viesti/viestin-nayttoaika-pitka)
    app)


  ;; ---- Vahvistaminen ja muutokset ---

  VahvistaSuunnitelmanOsioVuodella
  (process-event [{{:keys [tyyppi hoitovuosi]} :parametrit} app]
    (let [urakka (-> @tiedot/tila :yleiset :urakka :id)]
      (laheta-ja-odota-vastaus app
        {:palvelu :vahvista-kustannussuunnitelman-osa-vuodella
         :payload {:urakka-id urakka
                   :hoitovuosi hoitovuosi
                   ;; TODO: Tyyppi = osio, korjaa termi
                   :tyyppi tyyppi}
         :onnistui ->VahvistaSuunnitelmanOsioVuodellaOnnistui
         :epaonnistui ->VahvistaSuunnitelmanOsioVuodellaEpaonnistui})))

  VahvistaSuunnitelmanOsioVuodellaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! (str "Suunnitelman vahvistus onnistui!") :success viesti/viestin-nayttoaika-pitka)
    (assoc-in app [:domain :osioiden-tilat] vastaus))

  VahvistaSuunnitelmanOsioVuodellaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Suunnitelman vahvistus epäonnistui!" :warning viesti/viestin-nayttoaika-pitka)
    app)

  KumoaOsionVahvistusVuodelta
  (process-event [{{:keys [tyyppi hoitovuosi]} :parametrit} app]
    (let [urakka (-> @tiedot/tila :yleiset :urakka :id)]
      (laheta-ja-odota-vastaus app
        {:palvelu :kumoa-suunnitelman-osan-vahvistus-hoitovuodelle
         :payload {:urakka-id urakka
                   :hoitovuosi hoitovuosi
                   ;; TODO: Tyyppi = osio, korjaa termi
                   :tyyppi tyyppi}
         :onnistui ->KumoaOsionVahvistusVuodeltaOnnistui
         :epaonnistui ->KumoaOsionVahvistusVuodeltaEpaonnistui})))

  KumoaOsionVahvistusVuodeltaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! (str "Suunnitelman vahvistuksen kumoaminen onnistui!") :success viesti/viestin-nayttoaika-pitka)
    (assoc-in app [:domain :osioiden-tilat] vastaus))

  KumoaOsionVahvistusVuodeltaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta! "Suunnitelman vahvistuksen kumoaminen epäonnistui!" :warning viesti/viestin-nayttoaika-pitka)
    app)

  PaivitaKattohintaGrid
  (process-event [{grid :grid} app]
    (let [gridin-tila (grid-protokolla/hae-muokkaustila grid)]
      (as-> app app
        (assoc-in app [:kattohinta :grid 0 :koskettu?] true)
        (assoc-in app [:kattohinta :grid 1]
          (merge {:rivi :indeksikorjaukset}
            (into {}
              (map-indexed (fn [idx [hoitovuosi-nro kattohinta]]
                             {hoitovuosi-nro (indeksikorjaa kattohinta (inc idx))})
                (select-keys (get gridin-tila 0) kattohinta-grid-avaimet)))))
        (assoc-in app [:kattohinta :grid 1 :yhteensa]
          (apply + (vals
                     (select-keys (get-in app [:kattohinta :grid 1]) kattohinta-grid-avaimet)))))))

  TallennaSeliteMuutokselle
  (process-event [_ app]
    ;;TODO:
    )

  TallennaSeliteMuutokselleOnnistui
  (process-event [_ app]
    ;;TODO:
    )

  TallennaSeliteMuutokselleEpaonnistui
  (process-event [_ app]
    ;;TODO:
    )

  VahvistaMuutoksetJaTallenna
  (process-event [{{:keys [palvelu payload onnistui epaonnistui]} :tiedot} app]
    (tallenna-tavoite-ja-kattohinnat (:yhteenvedot app))
    (-> app
      (assoc-in [:domain :muutosten-vahvistus] {:vaaditaan-muutosten-vahvistus? false
                                                :muutos-vahvistettu-fn nil
                                                :tiedot {}})
      (laheta-ja-odota-vastaus
        {:palvelu palvelu
         :payload payload
         :onnistui onnistui
         :epaonnistui epaonnistui})))

  SuljeMuutostenVahvistusModal
  (process-event [_ app]
    (assoc-in app [:domain :muutosten-vahvistus] {:vaaditaan-muutosten-vahvistus? false
                                                  :muutos-vahvistettu-fn nil
                                                  :tiedot {}}))

  ;; FIXME: Tätä ei käytetä missään tällä hetkellä? Liittyy ilmeisesti osion vahvistamisen jälkeisten muutosten vahvistamiesen.
  TarkistaTarvitaankoMuutostenVahvistus
  (process-event [{:keys [asia hoitovuosi toiminto-fn!]} app]
    (let [vahvistus-modaali-auki? (get-in app [:domain :muutosten-vahvistus :vaaditaan-muutosten-vahvistus?])
          hoitovuosi (or hoitovuosi
                       (get-in app [:suodattimet :hoitokauden-numero]))
          tarvitaan-vahvistus? (pitaako-osion-muutokset-vahvistaa? app (or (tyyppi->osio asia)
                                                                         asia) hoitovuosi)
          e! (tuck/current-send-function)]
      (println "tarvitaanko? " (tyyppi->osio asia) asia (or (tyyppi->osio asia)
                                                          asia) hoitovuosi tarvitaan-vahvistus?)
      (cond
        vahvistus-modaali-auki? ; jos on mahdollista, et modaalin avaaminen triggeröi uuden blur-eventin esim kun vaihdetaan inputtia tabilla toiseen inputtiin ja tällöin uusi blurri ylikirjoittaa edellisen. skipataan siis kaikki, jos tää ikkuna on auki.
        app

        tarvitaan-vahvistus?
        (assoc-in app [:domain :muutosten-vahvistus] {:vaaditaan-muutosten-vahvistus? true
                                                      :asia (or (tyyppi->osio asia) asia)
                                                      :muutos-vahvistettu-fn (fn [e! muutos]
                                                                               (e! (->SuljeMuutostenVahvistusModal))
                                                                               (toiminto-fn! e! muutos))})
        :else
        (do
          (toiminto-fn! e!)
          app)))))