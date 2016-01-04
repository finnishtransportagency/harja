(ns harja.views.urakka.paallystyksen-kohdeluettelo
  "Päällystysurakan 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko] :as yleiset]
            [harja.views.urakka.paallystyskohteet :as paallystyskohteet-yhteenveto]
            [harja.views.urakka.paallystysilmoitukset :as paallystysilmoitukset]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.popupit :as popupit]

            [harja.ui.lomake :refer [lomake]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]

            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.tiedot.urakka.paallystys :as paallystys])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kohdeluettelo-valilehti (atom :paallystyskohteet))

(defn kohdeosan-reitti-klikattu [_ kohde]
  (let [paallystyskohde-id (:paallystyskohde-id kohde)]
    (popupit/nayta-popup (-> kohde
                             (assoc :aihe :paallystys-klikattu)
                             (assoc :kohde {:nimi (get-in kohde [:kohde :nimi])})
                             (assoc :kohdeosa {:nimi (get-in kohde [:osa :nimi])})
                             (assoc :nykyinen_paallyste (get-in kohde [:osa :nykyinen_paallyste]))
                             (assoc :toimenpide (get-in kohde [:osa :toimenpide]))
                             (assoc :paallystysilmoitus {:tila (:tila kohde)})
                             (assoc :tr {:numero        (get-in kohde [:osa :tr_numero])
                                         :alkuosa       (get-in kohde [:osa :tr_alkuosa])
                                         :alkuetaisyys  (get-in kohde [:osa :tr_alkuetaisyys])
                                         :loppuosa      (get-in kohde [:osa :tr_loppuosa])
                                         :loppuetaisyys (get-in kohde [:osa :tr_loppuetaisyys])})
                             (assoc :kohde-click #(do (kartta/poista-popup!)
                                                      (reset! kohdeluettelo-valilehti :paallystysilmoitukset)
                                                      (tapahtumat/julkaise! {:aihe :avaa-paallystysilmoitus :paallystyskohde-id paallystyskohde-id})))))))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  (komp/luo
    (komp/kuuntelija :paallystys-klikattu kohdeosan-reitti-klikattu)
    (komp/lippu paallystys/karttataso-paallystyskohteet)
    (fn [ur]
      [bs/tabs {:style :tabs :classes "tabs-taso2" :active kohdeluettelo-valilehti}

       "Päällystyskohteet"
       :paallystyskohteet
       [paallystyskohteet-yhteenveto/paallystyskohteet]

       "Päällystysilmoitukset"
       :paallystysilmoitukset
       [paallystysilmoitukset/paallystysilmoitukset]])))