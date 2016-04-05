(ns harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]]
            [harja.pvm :as pvm]
            [harja.tiedot.istunto :refer [kayttaja]]
            [harja.tiedot.urakka :as u])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-kokonaishintainen-toteuma (atom nil))
(def uusi-kokonaishintainen-toteuma (reaction
                                      {:suorittaja {:nimi    (:nimi @u/urakan-organisaatio)
                                                    :ytunnus (:ytunnus @u/urakan-organisaatio)}}))
(def haetut-reitit (atom nil))

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

(defn hae-toteumareitit [urakka-id sopimus-id [alkupvm loppupvm] tehtava]
  (k/post! :urakan-kokonaishintaisten-toteumien-reitit
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :tehtava    tehtava}))

(def nakymassa? (atom false))
(def valittu-paivakohtainen-tehtava (atom nil))

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

;; Piirretään kartalle reitit, jotka haetaan kun summariviä klikataan JA
;; valitun toteuman reitti.
(def kokonaishintainen-toteuma-kartalla
         (reaction<!
           [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first @urakka/valittu-sopimusnumero)
            taso-paalla? @karttataso-kokonaishintainen-toteuma
            valittu-paivakohtainen-tehtava @valittu-paivakohtainen-tehtava
            valittu-toteuma @valittu-kokonaishintainen-toteuma]
           (when (and taso-paalla? (or valittu-toteuma valittu-paivakohtainen-tehtava))
             (go
               (kartalla-esitettavaan-muotoon
                 (let [haun-tulos (if valittu-paivakohtainen-tehtava
                                    (<!
                                     (hae-toteumareitit
                                       urakka-id sopimus-id
                                       (pvm/paivan-aikavali (:pvm valittu-paivakohtainen-tehtava))
                                       (:toimenpidekoodi valittu-paivakohtainen-tehtava)))
                                    [])
                       ;; Esitettävillä asioilla on tietty muoto mitä se odottaa
                       ;; Muokataan valittu-toteuma tähän muotoon
                       valittu-tehtavilla (assoc valittu-toteuma
                                            :tehtavat
                                            [{:id (get-in valittu-toteuma [:tehtava :toimenpidekoodi :id])
                                              :toimenpide (get-in valittu-toteuma [:tehtava :toimenpidekoodi :nimi])
                                              :maara (get-in valittu-toteuma [:tehtava :maara])}])
                       ;; Haetuissa reiteissä on klikatun summarivin reitit. Liitetään mukaan
                       ;; Valitun toteuman reitti jos se ei jo ole tässä joukossa.
                       yhdistetyt-reitit (if-not
                                           (some #(= (:id valittu-toteuma) (:toteumaid %)) haun-tulos)
                                           (conj haun-tulos valittu-tehtavilla)
                                           haun-tulos)]
                   (reset! haetut-reitit yhdistetyt-reitit))
                 valittu-toteuma [[:toteumaid] [:id]]
                 (map #(assoc % :tyyppi-kartalla :toteuma)))))))

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
