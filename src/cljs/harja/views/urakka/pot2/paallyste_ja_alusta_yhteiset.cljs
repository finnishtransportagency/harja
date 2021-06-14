(ns harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.viesti :as viesti]))

(def hint-kopioi-kaistoille "Kopioi rivin sisältö kaikille rinnakkaisille kaistoille. Jos kaistaa ei vielä ole, se lisätään taulukkoon.")

;; Tärkeää käytettävyyden kannalta, että kulutuskerroksen ja alustan sarakkeet ovat kohdikkain
;; siksi huomioitava tämä jos sarakkeita lisätään tai poistetaan jompaan kumpaan
(def gridin-leveydet
  {:toimenpide 3
   :perusleveys 2
   :materiaali 3
   :tp-tiedot 8
   :toiminnot 3})

(def undo-aikaikkuna-ms 10000)

(def undo-tiedot (atom nil))
(def edellinen-tila (atom nil))

(defn poista-undo-tiedot []
  (when (:timeout-id @undo-tiedot)
    (.clearTimeout js/window (:timeout-id @undo-tiedot)))
  (reset! undo-tiedot nil))

(defn tarjoa-toiminnon-undo [vanha-tieto tyyppi index]
  (poista-undo-tiedot)
  (reset! edellinen-tila vanha-tieto)
  (let [timeout-id (yleiset/fn-viiveella poista-undo-tiedot undo-aikaikkuna-ms)]
    (reset! undo-tiedot {:tyyppi tyyppi :index index :timeout-id timeout-id})))

(defn rivin-toiminnot-sarake
  [rivi osa e! app kirjoitusoikeus? rivit-atom tyyppi voi-muokata?]
  (assert (#{:alusta :paallystekerros} tyyppi) "Tyypin on oltava päällystekerros tai alusta")
  (let [kohdeosat-muokkaa! (fn [uudet-kohdeosat-fn index]
                             (let [vanhat-kohdeosat @rivit-atom
                                   uudet-kohdeosat (uudet-kohdeosat-fn vanhat-kohdeosat)]
                               (e! (pot2-tiedot/->Pot2Muokattu))
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
                                            ;; Jos poistetaan ylin rivi (index 0), lisätään yksi, jotta undo tarjotaan riville 1
                                            (if (= index 0)
                                              index
                                              ;; Jos poistetaan muu rivi, vähennetään indeksiä jotta undo ilmestyy edeltävälle riville
                                              (dec index))))]
    (fn [rivi {:keys [index]} e! app kirjoitusoikeus? rivit-atom tyyppi voi-muokata?]
      (let [nappi-disabled? (or (not voi-muokata?)
                                (not kirjoitusoikeus?))]
        [:span.tasaa-oikealle.pot2-rivin-toiminnot
         ;; vain sille riville tarjotaan undo, missä on toimintoa painettu
         (if (and (= tyyppi (:tyyppi @undo-tiedot))
                  (= index (:index @undo-tiedot)))
           [:div
            [napit/yleinen-toissijainen "Peru toiminto"
             #(do
                (reset! rivit-atom @edellinen-tila)
                (poista-undo-tiedot))]]
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
            [napit/nappi-hover-vihjeella {:tyyppi :lisaa
                                          :disabled? nappi-disabled?
                                          :hover-txt yllapitokohteet/hint-pilko-osoitevali
                                          :toiminto pilko-osa-fn
                                          :toiminto-args [index tyyppi]}]

            [napit/nappi-hover-vihjeella {:tyyppi :poista
                                          :disabled? nappi-disabled?
                                          :hover-txt yllapitokohteet/hint-poista-rivi
                                          :toiminto poista-osa-fn
                                          :toiminto-args [index]}]])]))))


