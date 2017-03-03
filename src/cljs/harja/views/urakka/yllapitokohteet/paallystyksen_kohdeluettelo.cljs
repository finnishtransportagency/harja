(ns harja.views.urakka.yllapitokohteet.paallystyksen-kohdeluettelo
  "Päällystysurakan 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko vihje] :as yleiset]
            [harja.views.urakka.paallystyskohteet :as paallystyskohteet]
            [harja.views.urakka.paallystysilmoitukset :as paallystysilmoitukset]
            [harja.views.kartta :as kartta]

            [harja.ui.lomake :refer [lomake]]
            [harja.ui.komponentti :as komp]

            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.paallystys :as paallystys])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  (komp/luo
    (komp/lippu paallystys/karttataso-paallystyskohteet)
    (komp/lippu paallystys/kohdeluettelossa?)
    (fn [ur]
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
            [paallystysilmoitukset/paallystysilmoitukset ur])]]
        [vihje "Päällystysurakka täytyy sitoa YHA-urakkaan ennen kuin sen kohteita voi hallita."]))))
