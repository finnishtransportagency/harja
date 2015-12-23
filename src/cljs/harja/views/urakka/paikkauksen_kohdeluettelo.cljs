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
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.paallystys.pot :as paallystys-pot]
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
  (log "Klikkasit paikkausta")
  (popupit/nayta-popup (-> kohde
                           (assoc :aihe :paikkaus-klikattu)
                           (assoc :kohde {:nimi (get-in kohde [:kohde :nimi])})
                           (assoc :kohdeosa {:nimi (get-in kohde [:osa :nimi])})
                           (assoc :nykyinen_paallyste (get-in kohde [:osa :nykyinen_paallyste]))
                           (assoc :toimenpide (get-in kohde [:osa :toimenpide]))
                           (assoc :paikkausilmoitus {:tila (:tila kohde)})
                           (assoc :tr {:numero (get-in kohde [:osa :tr_numero])
                                       :alkuosa (get-in kohde [:osa :tr_alkuosa])
                                       :alkuetaisyys (get-in kohde [:osa :tr_alkuetaisyys])
                                       :loppuosa (get-in kohde [:osa :tr_loppuosa])
                                       :loppuetaisyys (get-in kohde [:osa :tr_loppuetaisyys])})))
  ; FIXME Puuttuu vielä: aloituspvm, valmispvm kohde, valmispvm päällyste ja linkki kohteeseen

  ; TODO Wanha versio, poista kun ylempi toimii
  #_(let [osa (:osa kohdeosa)
        kohde (:kohde kohdeosa)
        paikkauskohde-id (:paikkauskohde-id kohdeosa)
        {:keys [tr_numero tr_alkuosa tr_alkuetaisyys tr_loppuosa tr_loppuetaisyys]} osa
        avaa-ilmoitus #(do (kartta/poista-popup!)
                           (reset! kohdeluettelo-valilehti :paikkausilmoitukset)
                           (tapahtumat/julkaise! {:aihe :avaa-paikkausilmoitus :paikkauskohde-id paikkauskohde-id}))]

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
          (ikonit/eye-open) " Paikkausilmoitus"]
         [:button.nappi-ensisijainen {:on-click avaa-ilmoitus}
          "Aloita paikkausilmoitus"])])))

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

