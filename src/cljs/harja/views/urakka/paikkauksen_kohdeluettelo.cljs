(ns harja.views.urakka.paikkauksen-kohdeluettelo
  "Paikkauksen 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                                      livi-pudotusvalikko]]
            [harja.views.urakka.paikkauskohteet :as paikkauskohteet]
            [harja.views.urakka.paikkausilmoitukset :as paikkausilmoitukset]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.views.kartta.popupit :as popupit]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defn kohdeosan-reitti-klikattu [_ kohde]
  (let [paikkauskohde-id (:paikkauskohde_id kohde)]
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
                             (assoc :kohde-click #(do (kartta/poista-popup!)
                                                      (nav/aseta-valittu-valilehti! :kohdeluettelo-paikkaus :paikkausilmoitukset)
                                                      (tapahtumat/julkaise! {:aihe :avaa-paikkausilmoitus :paikkauskohde-id paikkauskohde-id})))))))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  []
  (komp/luo
    (komp/ulos #(kartta/poista-popup!))
    (komp/kuuntelija :paikkaus-klikattu kohdeosan-reitti-klikattu)
    (komp/lippu paikkaus/karttataso-paikkauskohteet)
    (fn []
      [:span.kohdeluettelo
       [bs/tabs {:style  :tabs :classes "tabs-taso2"
                 :active (nav/valittu-valilehti-atom :kohdeluettelo-paikkaus)}

        "Paikkauskohteet"
        :paikkauskohteet
        [paikkauskohteet/paikkauskohteet]

        "Paikkausilmoitukset"
        :paikkausilmoitukset
        [paikkausilmoitukset/paikkausilmoitukset]]])))

