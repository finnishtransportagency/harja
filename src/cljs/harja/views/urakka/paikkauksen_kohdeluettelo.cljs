(ns harja.views.urakka.paikkauksen-kohdeluettelo
  "Paikkauksen 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.views.urakka.kohdeluettelo.paallystyskohteet :as paallystyskohteet-yhteenveto]
            [harja.views.urakka.kohdeluettelo.paikkausilmoitukset :as paikkausilmoitukset]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.views.kartta.popupit :as popupit]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.tiedot.urakka.kohdeluettelo.paallystys :as paallystys])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kohdeluettelo-valilehti (atom :paikkauskohteet))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))

(defn kohdeosan-reitti-klikattu [_ kohde]
  (let [paikkauskohde-id (get-in kohde [:osa :paikkauskohde-id])]
    (popupit/nayta-popup (-> kohde
                             (assoc :aihe :paikkaus-klikattu)
                             (assoc :kohde {:nimi (get-in kohde [:kohde :nimi])})
                             (assoc :kohdeosa {:nimi (get-in kohde [:osa :nimi])})
                             (assoc :nykyinen_paallyste (get-in kohde [:osa :nykyinen_paallyste]))
                             (assoc :toimenpide (get-in kohde [:osa :toimenpide]))
                             (assoc :paikkausilmoitus {:tila (:tila kohde)})
                             (assoc :tr {:numero        (get-in kohde [:osa :tr_numero])
                                         :alkuosa       (get-in kohde [:osa :tr_alkuosa])
                                         :alkuetaisyys  (get-in kohde [:osa :tr_alkuetaisyys])
                                         :loppuosa      (get-in kohde [:osa :tr_loppuosa])
                                         :loppuetaisyys (get-in kohde [:osa :tr_loppuetaisyys])})
                             (assoc :kohde-click (do (kartta/poista-popup!)
                                                     (reset! kohdeluettelo-valilehti :paikkausilmoitukset)
                                                     (tapahtumat/julkaise! {:aihe :avaa-paikkausilmoitus :paikkauskohde-id paikkauskohde-id})))))))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  (komp/luo
    (komp/kuuntelija :paikkaus-klikattu kohdeosan-reitti-klikattu)
    (komp/lippu paallystys/karttataso-paikkauskohteet)
    (fn [ur]
      [bs/tabs {:style :tabs :classes "tabs-taso2" :active kohdeluettelo-valilehti}

       "Paikkauskohteet"
       :paikkauskohteet
       [paallystyskohteet-yhteenveto/paallystyskohteet]

       "Paikkausilmoitukset"
       :paikkausilmoitukset
       [paikkausilmoitukset/paikkausilmoitukset ur]])))

