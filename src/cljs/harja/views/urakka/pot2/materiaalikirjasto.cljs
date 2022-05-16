(ns harja.views.urakka.pot2.materiaalikirjasto
  "POT2 materiaalikirjasto"
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<! chan]]
            [reagent.core :refer [atom] :as r]
            [harja.domain.pot2 :as pot2-domain]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as v]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.pot2.materiaalikirjasto :as mk-tiedot]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.views.urakka.pot2.massa-lomake :as massa-lomake]
            [harja.views.urakka.pot2.massat-taulukko :as massat-taulukko]
            [harja.views.urakka.pot2.murske-lomake :as murske-lomake]
            [harja.views.urakka.pot2.murskeet-taulukko :as murskeet-taulukko]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.grid :as grid])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(def tuo-materiaalit-txt "Voit tuoda materiaaleja vain omista urakoistasi.")

(def ei-tuotavia-materiaaleja "Ei tuotavia massoja tai materiaaleja")
(def ei-tuotavia-materiaaleja-tarkennus "Yhdessäkään urakassasi ei ole massoja tai murskeita tuontia varten")

(defn- tuotavat-massat
  [e! {:keys [materiaalit-toisesta-urakasta materiaalikoodistot] :as app}]
  (let [{massat :massat} materiaalit-toisesta-urakasta]
    [grid/grid
     {:otsikko "Massat"
      :tyhja "Ei massoja."
      :rivi-klikattu #(e! (mk-tiedot/->ValitseMassaTaiMurske (::pot2-domain/massa-id %) :massat))
      :tunniste :harja.domain.pot2/massa-id}
     [{:otsikko-komp (fn []
                       [kentat/raksiboksi {:disabled false
                                           :toiminto #(e! (mk-tiedot/->ValitseKaikkiMassatTaiMurskeet :massat))}
                        (mk-tiedot/materiaalien-ruksin-tila (:massat materiaalit-toisesta-urakasta))])
       :tyyppi :komponentti
       :komponentti (fn [rivi]
                      [kentat/raksiboksi {:disabled false
                                          :toiminto #(e! (mk-tiedot/->ValitseMassaTaiMurske (::pot2-domain/massa-id rivi) :massat))}
                       (:valittu? rivi)])
       :leveys 2}
      {:otsikko "Massan nimi" :nimi ::pot2-domain/massan-nimi :leveys 5
       :tyyppi :string}
      {:otsikko "Runkoaineet" :nimi ::pot2-domain/runkoaineet :leveys 5
       :tyyppi :komponentti
       :komponentti (fn [rivi]
                      [pot2-domain/massan-runkoaineet rivi (:runkoainetyypit materiaalikoodistot)])}]
     massat]))

(defn- tuotavat-murskeet
  [e! {:keys [materiaalit-toisesta-urakasta materiaalikoodistot] :as app}]
  (let [{murskeet :murskeet} materiaalit-toisesta-urakasta]
    [grid/grid
     {:otsikko "Murskeet"
      :tyhja "Ei murskeita."
      :rivi-klikattu #(e! (mk-tiedot/->ValitseMassaTaiMurske (::pot2-domain/murske-id %) :murskeet))
      :tunniste ::pot2-domain/murske-id}
     [{:otsikko-komp (fn []
                       [kentat/raksiboksi {:disabled false
                                           :toiminto #(e! (mk-tiedot/->ValitseKaikkiMassatTaiMurskeet :murskeet))}
                        (mk-tiedot/materiaalien-ruksin-tila (:murskeet materiaalit-toisesta-urakasta))])
       :tyyppi :komponentti
       :komponentti (fn [rivi]
                      [kentat/raksiboksi {:disabled false
                                          :toiminto #(e! (mk-tiedot/->ValitseMassaTaiMurske (::pot2-domain/murske-id rivi) :murskeet))}
                       (:valittu? rivi)])
       :leveys 2}
      {:otsikko "Murskeen nimi" :nimi ::pot2-domain/murskeen-nimi :leveys 5
       :tyyppi :string}
      {:otsikko "Tyyppi" :nimi ::pot2-domain/tyyppi :leveys 5 :tyyppi :string
       :hae (fn [rivi]
              (or
                (::pot2-domain/tyyppi-tarkenne rivi)
                (pot2-domain/ainetyypin-koodi->nimi (:mursketyypit materiaalikoodistot)
                                                    (::pot2-domain/tyyppi rivi))))}]
     murskeet]))

(defn- tuotavat-materiaalit [e! app]
  [:span
   [:h5 "Valitse tuotavat massat ja murskeet"]
   [tuotavat-massat e! (select-keys app [:materiaalit-toisesta-urakasta :materiaalikoodistot])]
   [tuotavat-murskeet e! (select-keys app [:materiaalit-toisesta-urakasta :materiaalikoodistot])]])

(defn tuo-materiaalit-modal [e! {:keys [tuonti-urakka
                                        nayta-muista-urakoista-tuonti?
                                        muut-urakat-joissa-materiaaleja
                                        materiaalit-toisesta-urakasta] :as app}]
  (let [sulje-fn #(e! (mk-tiedot/->SuljeMuistaUrakoistaTuonti))
        ei-loytynyt? (empty? muut-urakat-joissa-materiaaleja)]
    [modal/modal
     {:otsikko (if ei-loytynyt?
                 ei-tuotavia-materiaaleja
                 "Valitse urakka, josta haluat tuoda materiaaleja")
      :otsikon-alle-komp (fn []
                           (if ei-loytynyt?
                             [:div.ei-tuotavia-info
                              [:div.fontti-14 ei-tuotavia-materiaaleja-tarkennus]
                              [napit/yleinen-ensisijainen "OK" sulje-fn {:luokka "keskitettava"}]]
                             [:div.fontti-14 tuo-materiaalit-txt]))
      :luokka (yleiset/luokat "tuo-materiaalit-modal"
                              (when ei-loytynyt? "matala"))
      :nakyvissa? nayta-muista-urakoista-tuonti?
      :sulje-fn sulje-fn}
     (when-not ei-loytynyt?
       [:div
        [kentat/tee-otsikollinen-kentta {:otsikko "Valitse urakka"
                                         :arvo-atom tuonti-urakka
                                         :data-muokkaus-fn #(e! (mk-tiedot/->ValitseTuontiUrakka (:id %)))
                                         :kentta-params {:tyyppi :valinta
                                                         :valinta-nayta :nimi :valinta-arvo :id
                                                         :valinnat muut-urakat-joissa-materiaaleja}}]
        [:div.tuo-materiaalit-sisalto
         (when materiaalit-toisesta-urakasta
           [tuotavat-materiaalit e! app])

         [napit/yleinen-ensisijainen "Tuo materiaalit"
          (if materiaalit-toisesta-urakasta
            #(e! (mk-tiedot/->TuoMateriaalitToisestaUrakasta))
            #(e! (mk-tiedot/->HaeMateriaalitToisestaUrakasta tuonti-urakka)))
          {:ikoni (ikonit/harja-icon-action-copy)
           :luokka "tuo-materiaalit"
           :disabled (empty? materiaalit-toisesta-urakasta)}]
         [napit/yleinen-toissijainen "Kumoa" sulje-fn {:luokka "pull-right"}]]])]))

(defn- urakan-materiaalit [e! app]
  [:span
   [massat-taulukko/massat-taulukko e! app]
   [murskeet-taulukko/murskeet-taulukko e! app]
   ;; spacer, jotta alimpien rivien pop up ei piiloudu. Voi olla että voidaan poistaa kunhan murskeiden hallionta on tehty
   [:div {:style {:height "100px"}}]
   [napit/sulje "Sulje kirjasto"
    #(swap! mk-tiedot/nayta-materiaalikirjasto? not)]])

(def tuo-materiaalit-tooltip "Välttääksesi ylimääräistä työtä, voit tuoda massoja ja murskeita muista omista urakoistasi.")

(defn tuo-materiaalit-nappi [e!]
  [yleiset/wrap-if true
   [yleiset/tooltip {} :% tuo-materiaalit-tooltip]
   [napit/nappi "Tuo materiaalit toisesta urakasta"
    #(e! (mk-tiedot/->HaeMuutUrakatJoissaMateriaaleja))
    {:ikoni (ikonit/harja-icon-action-copy)
     :luokka "nappi-toissijainen tuo-materiaalit"
     :style {:margin-left "0"}}]])

(defn materiaalikirjasto [e! app]
  (komp/luo
    (komp/lippu mk-tiedot/materiaalikirjastossa?)
    (komp/piirretty (fn [this]
                      (e! (mk-tiedot/->AlustaTila))
                      (e! (mk-tiedot/->HaePot2MassatJaMurskeet))))
    (fn [e! app]
      [:div
       (cond
         (and (:pot2-massa-lomake app) (not (get-in app [:pot2-massa-lomake :sivulle?])))
         [massa-lomake/massa-lomake e! app]

         (and (:pot2-murske-lomake app) (not (get-in app [:pot2-murske-lomake :sivulle?])))
         [murske-lomake/murske-lomake e! app]

         :else
         [:span
          [tuo-materiaalit-nappi e!]
          [urakan-materiaalit e! app]])

       [tuo-materiaalit-modal e! app]
       [debug app {:otsikko "TUCK STATE"}]])))

(def materiaalikirjaston-otsikon-text
  "Voit käyttää materiaalikirjastoon lisättyjä massoja ja murskeita kaikissa urakan päällystysilmoituksissa.")

(defn materiaalikirjasto-modal [e! app]
  (let [lomake-auki? (or (:pot2-massa-lomake app)
                         (:pot2-murske-lomake app))]
    [modal/modal
     {:otsikko (if lomake-auki?
                 ""
                 "Materiaalikirjasto")
      :otsikon-alle-komp (when-not lomake-auki?
                           (fn []
                             [:span
                              [:div.fontti-14-vaaleampi {:style {:margin-bottom "24px"}}
                               (str (:nimi @nav/valittu-urakka))]
                              [:div.fontti-14 materiaalikirjaston-otsikon-text]]))
      :luokka "materiaalikirjasto-modal"
      :nakyvissa? @mk-tiedot/nayta-materiaalikirjasto?
      :sulje-ruksista-fn #(cond
                            (:pot2-massa-lomake app)
                            (e! (mk-tiedot/->TyhjennaLomake))

                            (:pot2-murske-lomake app)
                            (e! (mk-tiedot/->SuljeMurskeLomake))

                            :else
                            (e! (mk-tiedot/->SuljeMateriaaliModal)))
      :sulje-fn #(when-not lomake-auki?
                   (e! (mk-tiedot/->SuljeMateriaaliModal)))}
     [:div
      [materiaalikirjasto e! app]]]))


