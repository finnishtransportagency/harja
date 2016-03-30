(ns harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon kartalla-xf]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def valittu-kokonaishintainen-toteuma (atom nil))
(def uusi-kokonaishintainen-toteuma {})

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

(defonce kokonaishintainen-toteuma-kartalla
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               sopimus-id (first @urakka/valittu-sopimusnumero)
               taso-paalla? @karttataso-kokonaishintainen-toteuma
               valittu-paivakohtainen-tehtava @valittu-paivakohtainen-tehtava]
              (when (and taso-paalla? valittu-paivakohtainen-tehtava)
                (go (kartalla-esitettavaan-muotoon
                     (<! (hae-toteumareitit urakka-id sopimus-id
                                            (pvm/paivan-aikavali (:pvm valittu-paivakohtainen-tehtava))
                                            (:toimenpidekoodi valittu-paivakohtainen-tehtava)))
                     nil nil
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
