(ns harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.viesti :as viesti]))

(def hint-kopioi-kaistoille "Kopioi rivin sisältö kaikille rinnakkaisille kaistoille. Jos kaistaa ei vielä ole, se lisätään taulukkoon.")
(def hint-pilko-osoitevali "Pilko paalu\u00ADväli kahdeksi eri kohteeksi")
(def hint-poista-rivi "Poista rivi")

;; Tärkeää käytettävyyden kannalta, että kulutuskerroksen ja alustan sarakkeet ovat kohdikkain
;; siksi huomioitava tämä jos sarakkeita lisätään tai poistetaan jompaan kumpaan
(def gridin-leveydet
  {:toimenpide 3
   :perusleveys 2
   :materiaali 3
   :tp-tiedot 8
   :toiminnot 3})

(def kumoamiseen-kaytettavissa-oleva-aika-ms 10000)

(def tarjoa-undo? (atom nil))
(def edellinen-tila (atom nil))

(defn tarjoa-toiminnon-undo [vanha-tieto tyyppi index]
  (reset! tarjoa-undo? {:tyyppi tyyppi :index index})
  (reset! edellinen-tila vanha-tieto)
  (yleiset/fn-viiveella #(reset! tarjoa-undo? nil) kumoamiseen-kaytettavissa-oleva-aika-ms))

(defn rivin-toiminnot-sarake
  [rivi osa e! app kirjoitusoikeus? rivit-atom tyyppi voi-muokata?]
  (assert (#{:alusta :paallystekerros} tyyppi) "Tyypin on oltava päällystekerros tai alusta")
  (let [kohdeosat-muokkaa! (fn [uudet-kohdeosat-fn index]
                             (let [vanhat-kohdeosat @rivit-atom
                                   uudet-kohdeosat (uudet-kohdeosat-fn vanhat-kohdeosat)]
                               (tarjoa-toiminnon-undo vanhat-kohdeosat tyyppi index)
                               (swap! rivit-atom (fn [_]
                                                       uudet-kohdeosat))))
        pilko-osa-fn (fn [index tyyppi]
                       (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                             (if (= tyyppi :paallystekerros)
                                               (yllapitokohteet/pilko-paallystekohdeosa vanhat-kohdeosat (inc index) {})
                                               (yllapitokohteet/lisaa-uusi-pot2-alustarivi vanhat-kohdeosat (inc index) {})))
                                           index))
        poista-osa-fn (fn [index]
                        (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                              (yllapitokohteet/poista-kohdeosa vanhat-kohdeosat (inc index)))
                                            index))]
    (fn [rivi {:keys [index]} e! app kirjoitusoikeus? rivit-atom tyyppi voi-muokata?]
      (let [nappi-disabled? (or (not voi-muokata?)
                                (not kirjoitusoikeus?))]
        [:span.tasaa-oikealle.pot2-rivin-toiminnot
         ;; vain sille riville tarjotaan undo, missä on toimintoa painettu
         (if (and (= tyyppi (:tyyppi @tarjoa-undo?))
                  (= index (:index @tarjoa-undo?)))
           [:div
            [napit/yleinen-toissijainen "Peru toiminto"
             #(do
                (reset! rivit-atom @edellinen-tila)
                (reset! tarjoa-undo? nil))]]
           [:<>
            [yleiset/wrap-if true
             [yleiset/tooltip {} :% hint-kopioi-kaistoille]
             [napit/yleinen-ensisijainen ""
              #(do
                 (tarjoa-toiminnon-undo @rivit-atom tyyppi index)
                 (e! (pot2-tiedot/->KopioiToimenpiteetTaulukossa rivi rivit-atom)))
              {:ikoni (ikonit/copy-lane-svg)
               :disabled? nappi-disabled?
               :luokka "napiton-nappi btn-xs"
               :toiminto-args [rivi rivit-atom]}]]
            [yleiset/wrap-if true
             [yleiset/tooltip {} :% hint-pilko-osoitevali]
             [napit/yleinen-ensisijainen ""
              pilko-osa-fn
              {:ikoni (ikonit/action-add)
               :disabled nappi-disabled?
               :luokka "napiton-nappi btn-xs"
               :toiminto-args [index tyyppi]}]]
            [yleiset/wrap-if true
             [yleiset/tooltip {} :% hint-poista-rivi]
             [napit/yleinen-ensisijainen ""
              poista-osa-fn
              {:ikoni (ikonit/action-delete)
               :disabled nappi-disabled?
               :luokka "napiton-nappi btn-xs"
               :toiminto-args [index]}]]])]))))


