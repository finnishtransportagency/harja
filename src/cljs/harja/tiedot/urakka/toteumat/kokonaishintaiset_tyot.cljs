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

(defn hae-toteumatehtavien-paivakohtaiset-summat [urakka-id sopimus-id [alkupvm loppupvm] toimenpide tehtava]
  (k/post! :hae-urakan-kokonaishintaisten-toteumien-tehtavien-paivakohtaiset-summat
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :toimenpide toimenpide
            :tehtava    tehtava}))

(defn hae-toteumareitit [urakka-id sopimus-id [alkupvm loppupvm] toimenpide tehtava]
  (k/post! :urakan-kokonaishintaisten-toteumien-reitit
           {:urakka-id  urakka-id
            :sopimus-id sopimus-id
            :alkupvm    alkupvm
            :loppupvm   loppupvm
            :toimenpide toimenpide
            :tehtava    tehtava}))

(def nakymassa? (atom false))
(def valittu-toteuma (atom nil))

(def haetut-toteumat
         (reaction<!
           [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first @urakka/valittu-sopimusnumero)
            hoitokausi @urakka/valittu-hoitokausi
            kuukausi @urakka/valittu-hoitokauden-kuukausi
            toimenpide (first (first @urakka/valittu-kokonaishintainen-toimenpide))
            tehtava (:t4_id @urakka/valittu-kokonaishintainen-tehtava)
            nakymassa? @nakymassa?]
           (when nakymassa?
             (hae-toteumatehtavien-paivakohtaiset-summat urakka-id sopimus-id (or kuukausi hoitokausi) toimenpide tehtava))))

(def haetut-reitit
  (reaction<!
    [urakka-id (:id @nav/valittu-urakka)
     sopimus-id (first @urakka/valittu-sopimusnumero)
     hoitokausi @urakka/valittu-hoitokausi
     kuukausi @urakka/valittu-hoitokauden-kuukausi
     toimenpide (first (first @urakka/valittu-kokonaishintainen-toimenpide))
     tehtava (:t4_id @urakka/valittu-kokonaishintainen-tehtava)
     nakymassa? @nakymassa?]
    (when nakymassa?
      (hae-toteumareitit urakka-id sopimus-id (or kuukausi hoitokausi) toimenpide tehtava))))

(def karttataso-kokonaishintainen-toteuma (atom false))

(def kokonaishintainen-toteuma-kartalla
         (reaction
           (when @karttataso-kokonaishintainen-toteuma
             (kartalla-esitettavaan-muotoon
               (map
                 #(assoc % :tyyppi-kartalla :toteuma)
                 @haetut-reitit)))))


(defn aseta-hoitokausi [hoitokaudet rivi]
  (let [p (pvm/luo-pvm (:vuosi rivi) (dec (:kuukausi rivi)) 1)]
    (loop [[hk & hoitokaudet] hoitokaudet]
      (if (nil? hk)
        ;; Ei löytynyt hoitokautta, palautetaan rivi
        (do (log "kokonaishintaiselle työlle ei löytynyt hoitokautta: " (pr-str rivi))
            nil)

        (let [[alkupvm loppupvm] hk]
          ;;(log "TESTAA ONKO " (pr-str p) " HOITOKAUDESSA " (pr-str hk) "? " (and (or (pvm/sama-pvm? alkupvm p)
          ;;                                                                           (pvm/jalkeen? p alkupvm))
          ;;                                                                       (or (pvm/sama-pvm? loppupvm p)
          ;;                                                                           (pvm/ennen? p loppupvm))))
          (if (and (or (pvm/sama-pvm? alkupvm p)
                       (pvm/jalkeen? p alkupvm))
                   (or (pvm/sama-pvm? loppupvm p)
                       (pvm/ennen? p loppupvm)))
            (assoc rivi
              :alkupvm alkupvm
              :loppupvm loppupvm)

            (recur hoitokaudet)))))))


(defn hae-urakan-kokonaishintaiset-tyot [{:keys [tyyppi id] :as ur}]
  (go (let [res (<! (k/post! :kokonaishintaiset-tyot id))
            hoitokaudet (u/hoitokaudet ur)]
        (keep #(aseta-hoitokausi hoitokaudet %) res))))


(defn tallenna-kokonaishintaiset-tyot
  "Tallentaa urakan yksikköhintaiset työt, palauttaa kanavan, josta vastauksen voi lukea."
  [urakka-id sopimusnumero tyot]
  (k/post! :tallenna-kokonaishintaiset-tyot
           {:urakka-id     urakka-id
            :sopimusnumero (first sopimusnumero)
            :tyot          (into [] tyot)}))
