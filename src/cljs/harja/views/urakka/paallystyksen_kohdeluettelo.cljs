(ns harja.views.urakka.paallystyksen-kohdeluettelo
  "Päällystysurakan 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko] :as yleiset]
            [harja.views.urakka.kohdeluettelo.paallystyskohteet :as paallystyskohteet-yhteenveto]
            [harja.views.urakka.kohdeluettelo.paallystysilmoitukset :as paallystysilmoitukset]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.popupit :as popupit]

            [harja.ui.lomake :refer [lomake]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]

            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.domain.paallystys.pot :as paallystys-pot]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.tiedot.urakka.kohdeluettelo.paallystys :as paallystys])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kohdeluettelo-valilehti (atom :paallystyskohteet))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))

(defn kohdeosan-reitti-klikattu [_ kohde]
  (popupit/nayta-popup (-> kohde
                           (assoc :aihe :paallystys-klikattu)
                           (assoc :kohde {:nimi (get-in kohde [:kohde :nimi])})
                           (assoc :kohdeosa {:nimi (get-in kohde [:osa :nimi])})
                           (assoc :nykyinen_paallyste (get-in kohde [:osa :nykyinen_paallyste]))
                           (assoc :toimenpide (get-in kohde [:osa :toimenpide]))
                           (assoc :tila (:tila kohde))
                           (assoc :tr {:numero (get-in kohde [:osa :tr_numero])
                                       :alkuosa (get-in kohde [:osa :tr_alkuosa])
                                       :alkuetaisyys (get-in kohde [:osa :tr_alkuetaisyys])
                                       :loppuosa (get-in kohde [:osa :tr_loppuosa])
                                       :loppuetaisyys (get-in kohde [:osa :tr_loppuetaisyys])})))
  ; FIXME Puuttuu vielä: aloituspvm, valmispvm kohde, valmispvm päällyste ja linkki kohteeseen

  ; TODO Wanha versio alla, poistettava kun uusi toimii
  #_(let [osa (:osa kohdeosa)
        kohde (:kohde kohdeosa)
        paallystyskohde-id (:paallystyskohde-id kohdeosa)
        {:keys [tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys]} osa
        avaa-ilmoitus #(do (kartta/poista-popup!)
                           (reset! kohdeluettelo-valilehti :paallystysilmoitukset)
                           (tapahtumat/julkaise! {:aihe :avaa-paallystysilmoitus :paallystyskohde-id paallystyskohde-id}))]

    (kartta/nayta-popup!
      klikkaus-koordinaatit
      [:div.paallystyskohde
       [yleiset/tietoja {:otsikot-omalla-rivilla? true}
        "Kohde" (:nimi kohde)
        "Tierekisterikohde" (:nimi osa)
        "Osoite" (yleiset/tierekisteriosoite tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys)
        "Nykyinen päällyste" (paallystys-pot/hae-paallyste-koodilla (:nykyinen_paallyste osa))
        "Toimenpide" (:toimenpide osa)
        "Tila" (kuvaile-kohteen-tila (:tila kohdeosa))]
       (if (:tila kohdeosa)
         [:button.nappi-ensisijainen {:on-click avaa-ilmoitus}
          (ikonit/eye-open) " Päällystysilmoitus"]
         [:button.nappi-ensisijainen {:on-click avaa-ilmoitus}
          "Aloita päällystysilmoitus"])])))

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

