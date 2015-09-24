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
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.ui.komponentti :as komp]
            [harja.views.kartta :as kartta]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.paallystys.pot :as paallystys-pot])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kohdeluettelo-valilehti (atom :paikkauskohteet))

(defn kohdeosan-reitti-klikattu [_ {:keys [klikkaus-koordinaatit] :as kohdeosa}]
  (let [osa (:osa kohdeosa)
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
        "Tila" (case (:tila kohdeosa)
                 :valmis "Valmis"
                 :aloitettu "Aloitettu"
                 "Ei aloitettu")]
       (if (:tila kohdeosa)
         [:button.nappi-ensisijainen {:on-click avaa-ilmoitus}
          (ikonit/eye-open) " Paikkausilmoitus"]
         [:button.nappi-ensisijainen {:on-click avaa-ilmoitus}
          "Aloita paikkausilmoitus"])])))

(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]
  (komp/luo
    (komp/kuuntelija :paikkauskohde-klikattu kohdeosan-reitti-klikattu)
    (fn [ur]
      [bs/tabs {:active kohdeluettelo-valilehti}

       "Paikkauskohteet"
       :paikkauskohteet
       [paallystyskohteet-yhteenveto/paallystyskohteet]

       "Paikkausilmoitukset"
       :paikkausilmoitukset
       [paikkausilmoitukset/paikkausilmoitukset ur]])))

