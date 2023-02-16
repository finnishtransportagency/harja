(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-yhteinen
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.domain.paikkaus :as paikkaus]
            [harja.tiedot.urakka.paikkaukset-yhteinen :as yhteiset-tiedot]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.ui.kentat :as kentat]
            [harja.ui.napit :as napit]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.kartta :as kartta]
            [harja.tiedot.navigaatio :as nav]))


(defn hakuehdot* [e! {:keys [valinnat]}]
  (let [nayta-atom (atom (get valinnat :nayta :kaikki-kohteet))
        tr-atom (atom (:tr valinnat))
        ;; Jos urakan tyyppi on päällystys käytetään 1.1 -> 31.12, muuten käytetään hoitokautta.
        aikavali-atom (if (= (:tyyppi @nav/valittu-urakka) :paallystys)
                        (atom (:aikavali-kuluva valinnat))
                        (atom (:aikavali-hoitokausi valinnat)))
        haku-fn (fn [] (e! (yhteiset-tiedot/->HaeItemit)))]
    (add-watch nayta-atom
               :nayta-haku
               (fn [_ _ _ uusi]
                 (e! (yhteiset-tiedot/->PaivitaValinnat {:nayta uusi}))))
    (add-watch tr-atom
               :tierekisteri-haku
               (fn [_ _ _ uusi]
                 (e! (yhteiset-tiedot/->PaivitaValinnat {:tr uusi}))))
    (add-watch aikavali-atom
               :aikavali-haku
               (fn [_ _ vanha uusi]
                 (when-not (and (pvm/sama-pvm? (first vanha) (first uusi))
                                (pvm/sama-pvm? (second vanha) (second uusi)))
                   (e! (yhteiset-tiedot/->PaivitaValinnat {:aikavali uusi})))))
    (fn [e! {:keys [valinnat aikavali-otsikko ] :as yhteinen-tila}]
      [:div.flex-row.flex-wrap.alkuun.valistys16.tasaa-alkuun.lapsille-nolla-margin.tiiviit-labelit
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Näytä"
         :luokka ""
         :otsikon-luokka "alasvedon-otsikko-vayla"
         :kentta-params {:tyyppi :radio-group
                         :vaihtoehdot [:kaikki-kohteet :kohteet-joilla-toteumia]
                         :vaihtoehto-nayta (fn [arvo]
                                             ({:kaikki-kohteet "Kaikki kohteet"
                                               :kohteet-joilla-toteumia "Kohteet, joilla toteumia"}
                                              arvo))
                         :radio-luokka "ei-marginia"}
         :arvo-atom nayta-atom
         :tyylit {:width "fit-content"}}]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Toteuman tierekisteriosoite"
         :luokka ""
         :otsikon-luokka "alasvedon-otsikko-vayla"
         :kentta-params {:tyyppi :tierekisteriosoite
                         :tr-otsikot? false
                         :voi-valita-kartalta? false
                         :alaotsikot? true
                         :vayla-tyyli? true
                         }
         :arvo-atom tr-atom
         :tyylit {:width "fit-content"
                  :margin-bottom "1rem"}}]
       [valinnat/aikavali aikavali-atom {:otsikko aikavali-otsikko
                                         :for-teksti "filtteri-aikavali"
                                         :luokka #{"label-ja-aikavali " "ei-tiukkaa-leveytta "}
                                         :ikoni-sisaan? true
                                         :vayla-tyyli? true}]
       [:span {:style {:width "500px"}}
        [:label.alasvedon-otsikko-vayla "Työmenetelmä"]
        [:div.kentta
         [valinnat/checkbox-pudotusvalikko
          (map (fn [t]
                 {:nimi (or (::paikkaus/tyomenetelma-nimi t) t)
                  :id (::paikkaus/tyomenetelma-id t)
                  :valittu? (or (some #(or (= t %)
                                           (= (::paikkaus/tyomenetelma-id t) %)) (:valitut-tyomenetelmat valinnat)) ;; Onko kyseinen työmenetelmä valittu
                                false)})
               (into ["Kaikki"] (:tyomenetelmat valinnat)))
          (fn [tyomenetelma valittu?]
            (e! (yhteiset-tiedot/->ValitseTyomenetelma tyomenetelma valittu?)))
          [" Työmenetelmä valittu" " Työmenetelmää valittu"]
          {:vayla-tyyli? true}]]] 
       [:span {:style {:align-self "flex-start"
                       :margin-top "2rem"
                       :padding-bottom "2px"}}
        [napit/yleinen-ensisijainen "Hae toteumia" haku-fn {:luokka "nappi-korkeus-36"}]]
       #_ [kartta/piilota-tai-nayta-kartta-nappula {:luokka #{"oikealle"}
                                                 :style {:align-self "flex-start"
                                                         :margin-top "2.5rem"}}]])))

(defn hakuehdot-pohja [e! app]
  (if (:ensimmainen-haku-tehty? app)
    [:div
     [hakuehdot* e! app]]
    [yleiset/ajax-loader "Haetaan paikkauksia.."]))

(defn hakuehdot [_]
  (fn [_]
    [tuck/tuck tila/paikkaustoteumat hakuehdot-pohja]))
