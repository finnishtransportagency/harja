(ns harja.views.urakka.yllapitokohteet.paikkauksen-kohdeluettelo
  "Paikkauksen 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko vihje]]
            [harja.views.urakka.paikkauskohteet :as paikkauskohteet]
            [harja.views.urakka.paikkausilmoitukset :as paikkausilmoitukset]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  (komp/luo
    (komp/lippu paikkaus/kohdeluettelossa?)
    (komp/lippu paikkaus/karttataso-paikkauskohteet)
    (fn [ur]
      (if (:yhatiedot ur)
        [:span.kohdeluettelo
         [bs/tabs {:style :tabs :classes "tabs-taso2"
                   :active (nav/valittu-valilehti-atom :kohdeluettelo-paikkaus)}

          "Paikkauskohteet"
          :paikkauskohteet
          (when (oikeudet/urakat-kohdeluettelo-paikkauskohteet (:id ur))
            [paikkauskohteet/paikkauskohteet ur])

          "Paikkausilmoitukset"
          :paikkausilmoitukset
          (when (oikeudet/urakat-kohdeluettelo-paikkausilmoitukset (:id ur))
            [paikkausilmoitukset/paikkausilmoitukset ur])]]
        [vihje "Paikkausurakka täytyy sitoa YHA-urakkaan ennen kuin sen kohteita voi hallita."]))))
