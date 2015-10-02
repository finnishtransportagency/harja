(ns harja.views.urakka.paikkauksen-kohdeluettelo
  "Paikkauksen 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.views.urakka.toteumat.lampotilat :refer [suolasakot]]
            [harja.views.urakka.toteumat.materiaalit :refer [materiaalit-nakyma]]
            [harja.views.urakka.kohdeluettelo.paallystyskohteet :as paallystyskohteet-yhteenveto]
            [harja.views.urakka.kohdeluettelo.paikkausilmoitukset :as paikkausilmoitukset]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kohdeluettelo-valilehti (atom :paikkauskohteet))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  [bs/tabs {:style :tabs :classes "tabs-taso2" :active kohdeluettelo-valilehti}

   "Paikkauskohteet"
   :paikkauskohteet
   [paallystyskohteet-yhteenveto/paallystyskohteet]

   "Paikkausilmoitukset"
   :paikkausilmoitukset
   [paikkausilmoitukset/paikkausilmoitukset ur]])

