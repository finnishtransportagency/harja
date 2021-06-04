(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset
  (:require
    [harja.tiedot.urakka :as u]
    [harja.tiedot.urakka.paallystys :as t-ur-paallystys]
    [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.tiedot.urakka.yllapito :as t-yllapito]
    [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paallystysilmoitukset :as t-paallystys]
    [harja.ui.debug :as debug]
    [harja.ui.grid :as grid]
    [harja.ui.komponentti :as komp]
    [harja.views.urakka.paallystysilmoitukset :as paallystys]
    [harja.views.urakka.pot2.materiaalikirjasto :as massat-view]
    [reagent.core :as r]
    [tuck.core :as tuck]))

(defn- lisaa-tarkkailija! [e! tarkkailijan-avain polku toinen]
  (e! (t-ur-paallystys/->MuutaTila polku @toinen))
  (add-watch
    toinen
    tarkkailijan-avain
    #(e! (t-ur-paallystys/->MuutaTila polku %4))))

;; Lisätään tarkkailijat ilmoitusluetteloa varten. Kuuntelija tallentaa halutun arvon tilaan, jotta
;; ilmoitusluettelo toimii oikein ilman isompia kikkailuita.
;; kuuntelijan avaimen prefiksi pkp tarkoittaa paikkauskohteiden päällystystä.
(defn- lisaa-tarkkailijat! [e!]
  (do
    (lisaa-tarkkailija! e! :pkp-urakan-vuosi [:urakka-tila :valittu-urakan-vuosi] u/valittu-urakan-vuosi)
    (lisaa-tarkkailija! e! :pkp-sopimusnro [:urakka-tila :valittu-sopimusnumero] u/valittu-sopimusnumero)
    (lisaa-tarkkailija! e! :pkp-tienumero [:yllapito-tila :tienumero] t-yllapito/tienumero)
    (lisaa-tarkkailija! e! :pkp-kohdenumero [:yllapito-tila :kohdenumero] t-yllapito/kohdenumero)))

(defn- poista-tarkkailijat! []
  (remove-watch u/valittu-urakan-vuosi :pkp-urakan-vuosi)
  (remove-watch u/valittu-sopimusnumero :pkp-sopimusnro)
  (remove-watch t-yllapito/tienumero :pkp-tienumero)
  (remove-watch t-yllapito/kohdenumero :pkp-kohdenumero))

(defn paallystysilmoitukset* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do
                           (e! (t-ur-paallystys/->MuutaTila [:urakka] (:urakka @tila/yleiset)))
                           (e! (t-ur-paallystys/->HaePaallystysilmoitukset)) ;; Ei tarvita, toistaiseksi tässä, jotta näkee taulukon.
                           (e! (t-paallystys/->HaePotPaikkaukset))
                           (e! (mk-tiedot/->HaePot2MassatJaMurskeet))
                           (e! (mk-tiedot/->HaeKoodistot))
                           (lisaa-tarkkailijat! e!))
                        #(do
                           (poista-tarkkailijat!)))
    (fn [e! app]
      [:div
       [:h1 "Paikkauskohteiden päällystysilmoitukset"]
       [debug/debug app]
       ;; Jostain syystä urakkaa ei aina keretä ladata kokonaan sovelluksen tilaan, mikä hajoittaa valinnat-komponetin.
       ;; Odotetaan siis, että urakalta löytyy varmasti alkupvm ennen kuin rendataan mitään.
       (when-not (nil? (:alkupvm (:urakka app)))
         [:div
          ;; Selvitettävä: Miten haetaan oikeat tiedot ilmoitusluetteloon?
          ;; Onko helpompaa tehdä suoraan figmassa näkyvä listausnäkymä kuin käyttää potin ilmoitusluetteloa?
          [paallystys/valinnat e! app]
          [paallystys/ilmoitusluettelo e! app]])
       [massat-view/materiaalikirjasto-modal e! app]])))

(defn paallystysilmoitukset []
  [tuck/tuck tila/paikkauspaallystykset paallystysilmoitukset*])
