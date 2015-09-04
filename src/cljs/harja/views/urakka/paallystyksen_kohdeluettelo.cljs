(ns harja.views.urakka.paallystyksen-kohdeluettelo
  "Päällystysurakan 'Kohdeluettelo' välilehti:"
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko] :as yleiset]
            [harja.views.urakka.toteumat.lampotilat :refer [suolasakot]]
            [harja.views.urakka.toteumat.materiaalit :refer [materiaalit-nakyma]]
            [harja.views.urakka.kohdeluettelo.paallystyskohteet :as paallystyskohteet-yhteenveto]
            [harja.views.urakka.kohdeluettelo.paallystysilmoitukset :as paallystysilmoitukset]
            [harja.views.kartta :as kartta]
            
            [harja.ui.lomake :refer [lomake]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            
            [harja.loki :refer [log logt]]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.domain.paallystys.pot :as paallystys-pot]
            [harja.asiakas.tapahtumat :as tapahtumat])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kohdeluettelo-valilehti (atom :paallystyskohteet))

(defn kohdeosan-reitti-klikattu [_ {:keys [klikkaus-koordinaatit] :as kohdeosa}]
  (let [osa (:osa kohdeosa)
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
       "Tila" (case (:tila kohdeosa)
                :valmis "Valmis"
                :aloitettu "Aloitettu"
                "Ei aloitettu")]
      (if (:tila kohdeosa)
        [:button.nappi-ensisijainen {:on-click avaa-ilmoitus}
         (ikonit/eye-open) " Päällystysilmoitus"]
        [:button.nappi-ensisijainen {:on-click avaa-ilmoitus}
         "Aloita päällystysilmoitus"])])))


                           
(defn kohdeluettelo
  "Kohdeluettelo-pääkomponentti"
  [ur]

  (komp/luo
   (komp/kuuntelija :paallystyskohde-klikattu kohdeosan-reitti-klikattu)
   (fn [ur]
     [bs/tabs {:active kohdeluettelo-valilehti}

      "Päällystyskohteet"
      :paallystyskohteet
      [paallystyskohteet-yhteenveto/paallystyskohteet]

      "Päällystysilmoitukset"
      :paallystysilmoitukset
      [paallystysilmoitukset/paallystysilmoitukset]])))

