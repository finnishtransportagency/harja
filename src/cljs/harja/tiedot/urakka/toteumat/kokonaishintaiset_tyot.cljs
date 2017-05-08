(ns harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!] :as async]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :refer [kayttaja]]
            [harja.tiedot.urakka :as u]
            [harja.ui.openlayers :as openlayers]
            [clojure.set :as set])
  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))


(defonce
  ^{:doc "Päiväkohtaiset tehtävät, jotka on avattu (vetolaatikot).
Arvot ovat [urakka-id pvm tpk jarjestelma?] vektoreita."}
  avatut-toteumat (atom #{}))
(defonce
  ^{:doc "Avattujen toteumien haetut toteumalistat. Avaimena päiväkohtaisen
tehtävän avain (ks. avatut-toteumat)."}
  toteumien-paivakohtaiset-tiedot
  (atom {}))

(defonce
  ^{:doc "Toteumalomakkeelle avatun yksittäisen toteuman päiväkohtainen avain ja id."}
  valitun-toteuman-paiva-ja-id (atom nil))

(defonce valittu-kokonaishintainen-toteuma (atom nil))

(defonce ^:private valitse-paivan-toteuma-idlla
  (run!
   (let [paivakohtaiset-tiedot @toteumien-paivakohtaiset-tiedot
         [avain id] @valitun-toteuman-paiva-ja-id]
     (reset! valittu-kokonaishintainen-toteuma
             (first (filter #(= id (:id %))
                            (get paivakohtaiset-tiedot avain)))))))

(defn uusi-kokonaishintainen-toteuma []
  {:alkanut    (-> (pvm/nyt) (pvm/keskipaiva))
   :suorittaja {:nimi    (:nimi @u/urakan-organisaatio)
                :ytunnus (:ytunnus @u/urakan-organisaatio)}})
(def haetut-reitit (atom nil))

(defn hae-toteuman-tiedot
  "Hakee yksittäisen toteuman tiedot lomaketta varten"
  [urakka-id toteuma-id]
  (go
    (first
     (<! (k/post! :hae-kokonaishintaisen-toteuman-tiedot
                  {:urakka-id urakka-id
                   :toteuma-id toteuma-id})))))

(defn hae-kokonaishintaisen-toteuman-tiedot
  "Hakee annetun toimenpidekoodin ja päivämäärän yksityiskohtaiset tiedot."
  [urakka-id pvm toimenpidekoodi]
  (k/post! :hae-kokonaishintaisen-toteuman-tiedot {:urakka-id urakka-id
                                                   :pvm pvm
                                                   :toimenpidekoodi toimenpidekoodi}))

(defn hae-toteumatehtavien-paivakohtaiset-summat [hakuparametrit]
  (k/post! :hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
           hakuparametrit))

(defn kasaa-hakuparametrit
  ([]
   (kasaa-hakuparametrit (:id @nav/valittu-urakka)
                         (first @urakka/valittu-sopimusnumero)
                         (or @urakka/valittu-aikavali @urakka/valittu-hoitokausi)
                         (:tpi_id @urakka/valittu-toimenpideinstanssi)
                         (:id @urakka/valittu-kokonaishintainen-tehtava)))
  ([urakka-id sopimus-id [alkupvm loppupvm] toimenpide tehtava]
   {:urakka-id  urakka-id
    :sopimus-id sopimus-id
    :alkupvm    alkupvm
    :loppupvm   loppupvm
    :toimenpide toimenpide
    :tehtava    tehtava}))

(def nakymassa? (atom false))

(defonce
  ^{:doc "Jos päiväkohtainen tehtävä valittu, arvo on mäp jossa
:pvm ja :toimenpidekoodi avaimet."}
  valittu-paivakohtainen-tehtava (atom nil))

(def haetut-toteumat
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               sopimus-id (first @urakka/valittu-sopimusnumero)
               hoitokausi @urakka/valittu-hoitokausi
               aikavali @urakka/valittu-aikavali
               toimenpide (:tpi_id @urakka/valittu-toimenpideinstanssi)
               tehtava (:id @urakka/valittu-kokonaishintainen-tehtava)
               nakymassa? @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when nakymassa?
                (hae-toteumatehtavien-paivakohtaiset-summat
                  (kasaa-hakuparametrit urakka-id sopimus-id (or aikavali hoitokausi) toimenpide tehtava)))))

(def karttataso-kokonaishintainen-toteuma (atom false))

(defn luo-kokonaishintainen-toteuma-kuvataso
  [urakka-id
   sopimus-id
   taso-paalla?
   valittu-paivakohtainen-tehtava
   valittu-toteuma]
  (when (and taso-paalla? (or valittu-toteuma valittu-paivakohtainen-tehtava))
    (openlayers/luo-kuvataso
     :kokonaishintainen-toteuma [] ;; FIXME tee selite valinnan pohjalta
     "kht" (k/url-parametri
            (let [[alkupvm loppupvm] (if valittu-paivakohtainen-tehtava
                                       (pvm/paivan-aikavali (:pvm valittu-paivakohtainen-tehtava))
                                       [(:alkanut valittu-toteuma) (:paattynyt valittu-toteuma)])]
              {:urakka-id urakka-id
               :sopimus-id sopimus-id
               :toteuma-id (:id valittu-toteuma)
               :alkupvm alkupvm
               :loppupvm loppupvm
               :toimenpidekoodi (:toimenpidekoodi valittu-paivakohtainen-tehtava)})))))

;; Piirretään kartalle reitit, jotka haetaan kun summariviä klikataan JA
;; valitun toteuman reitti.
(def kokonaishintainen-toteuma-kartalla
  (reaction
   (luo-kokonaishintainen-toteuma-kuvataso (:id @nav/valittu-urakka)
                                           (first @urakka/valittu-sopimusnumero)
                                           @karttataso-kokonaishintainen-toteuma
                                           @valittu-paivakohtainen-tehtava
                                           @valittu-kokonaishintainen-toteuma)))

(defn valitse-paivakohtainen-tehtava! [pvm toimenpidekoodi]
  (reset! valittu-paivakohtainen-tehtava {:pvm pvm :toimenpidekoodi toimenpidekoodi}))

(defn hae-toteuman-reitti!
  "Hakee reitin toteumalle, jos toteumalla ei ole vielä reittiä."
  [toteuma]
  (when (or (empty? (:reitti toteuma)) (empty? (:tr toteuma)))
    (swap! valittu-kokonaishintainen-toteuma assoc :reitti :hakee)
    (go (let [tulos (<! (k/post! :hae-toteuman-reitti-ja-tr-osoite {:id        (:id toteuma)
                                                                 :urakka-id (:id @nav/valittu-urakka)}))]
          ;; Eihän olla vaihdettu valittua tässä välissä?
          (when (= (:id @valittu-kokonaishintainen-toteuma) (:id toteuma))
            (swap! valittu-kokonaishintainen-toteuma assoc
                   :reitti (:reitti tulos)
                   :tr (:tr tulos)))))))

(defn valitse-toteuma! [toteuma]
  (reset! valittu-kokonaishintainen-toteuma toteuma)
  (hae-toteuman-reitti! toteuma))

(defn valitse-paivan-toteuma-id!
  "Valitse päiväkohtaisen toteumalistauksen toteuma."
  [paiva-avain toteuma-id]
  (reset! valitun-toteuman-paiva-ja-id [paiva-avain toteuma-id]))

(defn poista-toteuman-valinta! []
  (reset! valitun-toteuman-paiva-ja-id nil)
  (reset! valittu-kokonaishintainen-toteuma nil))

(defn kasaa-toteuman-tiedot-tallennusta-varten [t]
  {:suorittajan-nimi (get-in t [:suorittaja :nimi])
   :suorittajan-ytunnus (get-in t [:suorittaja :ytunnus])
   :urakka-id (:id @nav/valittu-urakka)
   :sopimus-id (first @urakka/valittu-sopimusnumero)
   :alkanut (:alkanut t)
   :paattynyt (:paattynyt t)
   :tyyppi :kokonaishintainen
   :lisatieto (:lisatieto t)
   :reitti (:reitti t)
   :tr (:tr t)
   :toteuma-id (:id t)
   :tehtavat [{:tehtava-id (get-in t [:tehtava :id])
               :poistettu false
               :toimenpidekoodi (get-in t [:tehtava :toimenpidekoodi :id])
               :maara (get-in t [:tehtava :maara])}]})

(defn tallenna-kokonaishintainen-toteuma! [toteuma]
  (k/post! :tallenna-urakan-toteuma-ja-kokonaishintaiset-tehtavat
           (assoc {}
             :toteuma (kasaa-toteuman-tiedot-tallennusta-varten toteuma)
             :hakuparametrit (kasaa-hakuparametrit))))

(defn toteuman-tallennus-onnistui [tulos]
  (reset! haetut-toteumat tulos))

(defn avaa-toteuma! [urakka-id pvm tpk jarjestelmanlisaama?]
  (swap! avatut-toteumat conj [urakka-id pvm tpk jarjestelmanlisaama?]))