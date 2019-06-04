(ns harja.views.urakka.yllapitokohteet.paallystyksen-kohdeluettelo
  "Päällystysurakan 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko vihje] :as yleiset]
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.komponentti :as komp]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.views.urakka.paallystyskohteet :as paallystyskohteet]
            [harja.views.urakka.paallystysilmoitukset :as paallystysilmoitukset]
            [harja.views.urakka.paallystyksen-maksuerat :as maksuerat]

            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.kartta :as tiedot-kartta]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.muokkauslukko :as lukko]

            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn tarkkaile-toisen-tilaa
  [e! toinen-tila referoinnin-avain polku]
  (e! (paallystys/->MuutaTila polku (deref toinen-tila)))
  (add-watch toinen-tila referoinnin-avain
             (fn [_ _ _ new-sate]
               (e! (paallystys/->MuutaTila polku new-sate)))))

(defn kohdeluettelo*
  "Kohdeluettelo-pääkomponentti"
  [e! app]
  (komp/luo
    (komp/lippu paallystys/karttataso-paallystyskohteet)
    (komp/lippu paallystys/kohdeluettelossa?)

    (komp/sisaan-ulos
      #(do
         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
         (nav/vaihda-kartan-koko! :M)
         (tiedot-kartta/kasittele-infopaneelin-linkit!
           (when (oikeudet/voi-lukea?
                   oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                   (:id @nav/valittu-urakka) @istunto/kayttaja)
             {:paallystys
              {:toiminto (fn [kohde]
                           (siirtymat/avaa-paallystysilmoitus! {:paallystyskohde-id (:yllapitokohde-id kohde)
                                                                :paallystysilmoitus-id (get-in kohde [:yllapitokohde :paallystysilmoitus-id])
                                                                :kohteen-urakka-id (get-in kohde [:yllapitokohde :urakka-id])
                                                                :valittu-urakka-id (:id @nav/valittu-urakka)}))
               :teksti "Avaa päällystysilmoitus"}}))
         ;; TUCK Lisäykset
         (e! (paallystys/->MuutaTila [:kohdeluettelossa?] true))
         (tarkkaile-toisen-tilaa e! yllapito-tiedot/kohdenumero :pot-kohdeluettelo-yllapito-kohdenumero [:yllapito-tila :kohdenumero])
         (tarkkaile-toisen-tilaa e! yllapito-tiedot/tienumero :pot-kohdeluettelo-yllapito-tienumero [:yllapito-tila :tienumero])
         (tarkkaile-toisen-tilaa e! u/valittu-sopimusnumero :pot-kohdeluettelo-urakka-valittu-sopimusnumero [:urakka-tila :valittu-sopimusnumero])
         (tarkkaile-toisen-tilaa e! u/valittu-urakan-vuosi :pot-kohdeluettelo-valittu-urakan-vuosi [:urakka-tila :valittu-urakan-vuosi])
         (tarkkaile-toisen-tilaa e! lukko/nykyinen-lukko :pot-kohdeluettelo-nykyinen-lukko [:lukko])
         (tarkkaile-toisen-tilaa e! istunto/kayttaja :pot-kohdeluettelo-kayttaja [:kayttaja]))
      #(do
         (nav/vaihda-kartan-koko! @nav/kartan-edellinen-koko)
         (tiedot-kartta/kasittele-infopaneelin-linkit! nil)
         ;;TUCK Lisäykset
         (remove-watch yllapito-tiedot/kohdenumero :pot-kohdeluettelo-yllapito-kohdenumero)
         (remove-watch yllapito-tiedot/kohdenumero :pot-kohdeluettelo-yllapito-tienumero)
         (remove-watch u/valittu-sopimusnumero :pot-kohdeluettelo-valittu-urakan-vuosi)
         (remove-watch lukko/nykyinen-lukko :pot-kohdeluettelo-nykyinen-lukko)
         (remove-watch istunto/kayttaja :pot-kohdeluettelo-kayttaja)))
    (fn [e! {ur :urakka :as app}]
      (if (:yhatiedot ur)
        [:span.kohdeluettelo
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :kohdeluettelo-paallystys)}

          "Päällystyskohteet"
          :paallystyskohteet
          (when (oikeudet/urakat-kohdeluettelo-paallystyskohteet (:id ur))
            [paallystyskohteet/paallystyskohteet ur])

          "Päällystysilmoitukset"
          :paallystysilmoitukset
          (when (oikeudet/urakat-kohdeluettelo-paallystysilmoitukset (:id ur))
            [paallystysilmoitukset/paallystysilmoitukset e! app])

          "Maksuerät"
          :maksuerat
          (when (oikeudet/urakat-kohdeluettelo-maksuerat (:id ur))
            [maksuerat/maksuerat])]]
        [vihje "Päällystysurakka täytyy sitoa YHA-urakkaan ennen kuin sen kohteita voi hallita."]))))

(defn kohdeluettelo [ur]
  (swap! paallystys/tila assoc :urakka ur)
  [tuck/tuck paallystys/tila kohdeluettelo*])
