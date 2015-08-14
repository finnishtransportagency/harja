(ns harja.views.urakka.paallystyksen-kohdeluettelo
  "Päällystysurakan 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.views.urakka.toteumat.lampotilat :refer [suolasakot]]
            [harja.views.urakka.toteumat.materiaalit :refer [materiaalit-nakyma]]
            [harja.views.urakka.kohdeluettelo.paallystyskohteet :as paallystyskohteet-yhteenveto]
            [harja.views.urakka.kohdeluettelo.paallystysilmoitukset :as paallystysilmoitukset]
            
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kohdeluettelo-valilehti (atom :paallystyskohteet))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  [bs/tabs {:active kohdeluettelo-valilehti}

   "Päällystyskohteet"
   :paallystyskohteet
   [paallystyskohteet-yhteenveto/paallystyskohteet]

   "Päällystysilmoitukset"
   :paallystysilmoitukset
   [paallystysilmoitukset/paallystysilmoitukset]])

