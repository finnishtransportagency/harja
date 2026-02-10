(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-apurit
  (:require [reagent.core :as r]
            [harja.ui.kentat :as kentat]
            [tuck.core :as tuck]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]))

(defn nayta-tilaa-paikkauskohteet-modal! [e! valitut-tilattavat-kohteet]
  (modal/nayta!
    {:otsikko (str "Tilataanko " (count valitut-tilattavat-kohteet) " kpl kohteita?")
     :footer [:div
              [:div.pull-left
               [napit/yleinen-ensisijainen (if (= 1 (count valitut-tilattavat-kohteet)) "Tilaa kohde" "Tilaa kohteet")
                #(e! (t-paikkauskohteet/->TilaaValitutPaikkauskohteet))
                {:paksu? true}]]
              [:div.pull-right
               [napit/yleinen-toissijainen "Kumoa" (fn [] (modal/piilota!)) {:paksu? true}]]]}
    [:div.tilaus-vahvistus-modal
     [:p "Urakoitsija saa sähköpostiin ilmoituksen kohteen tilauksesta."]]))

(defn nayta-tilaus-vahvistus-modal!
  "Näyttää modalin, jossa käyttäjä voi vahvistaa valittujen paikkauskohteiden raportointitavan.
   Parametrit:
   - valitut-kohteet: Vektori paikkauskohteita, jotka käyttäjä on valinnut tilaukseen"
  [e! valitut-kohteet]
  (let [raportointitapa-teksti (fn [kohde]
                                 (case (:toteumatyyppi kohde)
                                   :pot "POT-lomake"
                                   :normaali "Toteumat"
                                   "Ei määritetty"))]
    (modal/nayta!
      {:otsikko "Vahvista raportointitapa seuraaville kohteille"
       :footer [:div
                [:div.pull-left
                 [napit/yleinen-ensisijainen "Vahvista"
                  #(nayta-tilaa-paikkauskohteet-modal! e! valitut-kohteet)
                  #_(e! (t-paikkauskohteet/->VahvistaRaportointitavatModalissa))
                  {:paksu? true}]]
                [:div.pull-right
                 [napit/yleinen-toissijainen "Kumoa" (fn [] (modal/piilota!)) {:paksu? true}]]]}
      [:div.tilaus-vahvistus-modal
       [:p (str "Osa kohteista (" (count valitut-kohteet) ") vaatii raportointitavan vahvistamisen ennen tilaamista.")]
       [grid/grid
        {:otsikko "Valitut kohteet"
         :tunniste :id
         :tyhja "Ei valittuja kohteita"
         :voi-muokata? false}
        [{:otsikko "Nro"
          :nimi :id
          :tyyppi :string
          :leveys 1}
         {:otsikko "Nimi"
          :nimi :nimi
          :tyyppi :string
          :leveys 3}
         {:otsikko "Raportointitapa"
          :leveys 3
          :tyyppi :komponentti
          :komponentti (fn [rivi]
                         [kentat/tee-kentta {:tyyppi :radio-group
                                             :nimi :toteumatyyppi
                                             :otsikko ""
                                             :vaihtoehdot [:normaali :pot]
                                             :nayta-rivina? true
                                             :vayla-tyyli? true
                                             :vaihtoehto-nayta {:pot "POT-lomake"
                                                                :normaali "Toteumat"}
                                             :valitse-fn #(e! (t-paikkauskohteet/->AsetaToteumatyyppiKohteelle rivi %))}
                          (r/atom (:toteumatyyppi rivi))])}]
        valitut-kohteet]])))

