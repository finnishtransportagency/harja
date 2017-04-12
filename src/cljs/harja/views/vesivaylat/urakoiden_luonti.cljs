(ns harja.views.vesivaylat.urakoiden-luonti
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.vesivaylat.urakoiden-luonti :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.domain.oikeudet :as oikeudet]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]

            [harja.ui.debug :as debug]
            [harja.ui.modal :as modal])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn- sopimukset-grid [e! urakka haetut-sopimukset]
  (let [urakan-sopimukset (remove :poistettu (:sopimukset urakka))]
    [grid/muokkaus-grid
    {:tyhja "Urakkaan ei ole liitetty sopimuksia"}
    [{:otsikko "Nimi"
      :nimi :sopimus
      :tyyppi :valinta
      :valinnat (tiedot/vapaat-sopimukset haetut-sopimukset)
      :valinta-nayta #(or (:nimi %) "- Valitse sopimus -")
      :hae identity
      :jos-tyhja-fn #(or (:nimi %) "Ei sopimuksia")
      :aseta (fn [_ arvo] arvo)}
     {:otsikko "Alku" :nimi :alkupvm :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? (constantly false)}
     {:otsikko "Loppu" :nimi :loppupvm :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? (constantly false)}
     {:otsikko "Pääsopimus"
      :nimi :paasopimus
      :tyyppi :string
      :fmt #(if (tiedot/paasopimus? urakan-sopimukset %) (ikonit/check) (ikonit/unchecked))
      :muokattava? (constantly false)
      :hae identity}]
    (r/wrap
      (zipmap (range) urakan-sopimukset)
      #(e! (tiedot/->PaivitaSopimuksetGrid (vals %))))]))

(defn luontilomake [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeLomakevaihtoehdot)))
    (fn [e! {:keys [valittu-urakka tallennus-kaynnissa?
                    haetut-hallintayksikot haetut-hankkeet haetut-urakoitsijat
                    haetut-sopimukset] :as app}]
      [:div
       [napit/takaisin "Takaisin luetteloon"
        #(e! (tiedot/->ValitseUrakka nil))
        {:disabled tallennus-kaynnissa?}]
       (let [ilman-poistettuja #(remove :poistettu %)
             urakan-sopimukset (ilman-poistettuja (:sopimukset valittu-urakka))]
         [lomake/lomake
         {:otsikko (if (:id valittu-urakka)
                     "Muokkaa urakkaa"
                     "Luo uusi urakka")
          :muokkaa! #(e! (tiedot/->UrakkaaMuokattu (lomake/ilman-lomaketietoja %)))
          :voi-muokata? #(oikeudet/hallinta-vesivaylat)
          :footer-fn (fn [urakka]
                       [napit/tallenna
                        "Tallenna urakka"
                        #(e! (tiedot/->TallennaUrakka (lomake/ilman-lomaketietoja urakka)))
                        {:ikoni (ikonit/tallenna)
                         :disabled (or tallennus-kaynnissa?
                                       (not (lomake/voi-tallentaa? urakka)))
                         :tallennus-kaynnissa? tallennus-kaynnissa?
                         }])}
         [{:otsikko "Nimi" :nimi :nimi :tyyppi :string}
          {:otsikko "Alkupäivämäärä" :nimi :alkupvm :tyyppi :pvm}
          {:otsikko "Loppupäivämäärä" :nimi :loppupvm :tyyppi :pvm}
          (if haetut-hallintayksikot
            {:otsikko "Hallintayksikkö"
             :nimi :hallintayksikko
             :tyyppi :valinta
             :valinnat haetut-hallintayksikot
             :valinta-nayta #(if % (:nimi %) "- Valitse hallintayksikkö -")
             :aseta (fn [rivi arvo] (assoc rivi :hallintayksikko (dissoc arvo :alue :type)))}
            {:otsikko "Hallintayksikkö"
             :nimi :hallintayksikko
             :tyyppi :komponentti
             :komponentti (fn [_] [ajax-loader-pieni "Haetaan hallintayksiköitä"])})
          (if haetut-hankkeet
            {:otsikko "Hanke"
             :nimi :hanke
             :tyyppi :valinta
             :valinnat haetut-hankkeet
             :valinta-nayta #(if % (:nimi %) "- Valitse hanke -")
             :aseta (fn [rivi arvo] (assoc rivi :hanke arvo))}
            {:otsikko "Hanke"
             :nimi :hanke
             :tyyppi :komponentti
             :komponentti (fn [_] [ajax-loader-pieni "Haetaan hankkeita"])})
          {:otsikko "Alkupvm"
           :tyyppi :pvm
           :fmt pvm/pvm-opt
           :nimi :hankkeen-alkupvm
           :hae (comp :alkupvm :hanke)
           :muokattava? (constantly false)}
          {:otsikko "Loppupvm"
           :tyyppi :pvm
           :fmt pvm/pvm-opt
           :nimi :hankkeen-loppupvm
           :hae (comp :loppupvm :hanke)
           :muokattava? (constantly false)}
          (if haetut-urakoitsijat
            {:otsikko "Urakoitsijat"
             :nimi :urakoitsija
             :tyyppi :valinta
             :valinnat haetut-urakoitsijat
             :valinta-nayta #(if % (:nimi %) "- Valitse urakoitsija -")
             :aseta (fn [rivi arvo] (assoc rivi :urakoitsija arvo))}
            {:otsikko "Urakoitsijat"
             :nimi :urakoitsija
             :tyyppi :komponentti
             :komponentti (fn [_] [ajax-loader-pieni "Haetaan urakoitsijoita"])})
          {:otsikko "Sopimukset"
           :nimi :sopimukset
           :tyyppi :komponentti
           :komponentti (fn [{urakka :data}] [sopimukset-grid e!
                                              (lomake/ilman-lomaketietoja urakka) haetut-sopimukset])}
          {:otsikko "Pääsopimus"
           :nimi :paasopimus
           :tyyppi :valinta
           :valinnat urakan-sopimukset
           :valinta-nayta #(cond
                             %
                             (:nimi %)

                             (> (count urakan-sopimukset) 1)
                             "Määrittele pääsopimus"

                             :else "Urakalla vain yksi sopimus")
           :jos-tyhja "Urakalla ei sopimuksia"
           :muokattava? #(> (count urakan-sopimukset) 1)
           :pakollinen? (> (count urakan-sopimukset) 1)
           :aseta (fn [rivi arvo] (assoc rivi :sopimukset (tiedot/aseta-paasopimus
                                                            (ilman-poistettuja (:sopimukset rivi))
                                                            arvo)))
           :hae (fn [rivi] (tiedot/paasopimus (ilman-poistettuja (:sopimukset rivi))))}]
         valittu-urakka])])))

(defn muokkaus-tiedot [urakka]
  [:div
   [:h1 "Tänne urakan muokkaamisen ja sähkelähetyksen metatiedot"]
   [:div (pr-str urakka)]])

(defn sahke-nappi [e! {lahetykset :sahke-lahetykset} urakka]
  (let [lahetys-kaynnissa? (some? (lahetykset (:id urakka)))]
    [:button.nappi-toissijainen
     {:disabled lahetys-kaynnissa?
      :on-click #(do
                   (.preventDefault %)
                   (.stopPropagation %)
                   (modal/nayta!
                     {:otsikko "Urakan lähettäminen Sähkeeseen"
                      :footer
                      [:span
                       [:button.nappi-toissijainen {:on-click (fn [e]
                                                                (.preventDefault e)
                                                                (modal/piilota!))}
                        [ikonit/ikoni-ja-teksti (ikonit/livicon-chevron-left) "Sulje"]]
                       [:button.nappi-kielteinen {:on-click (fn [e]
                                                              (.preventDefault e)
                                                              (e! (tiedot/->LahetaUrakkaSahkeeseen urakka))
                                                              (modal/piilota!))}
                        [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Lähetä"]]]}
                     [muokkaus-tiedot urakka]))}
     (if lahetys-kaynnissa?
       [ajax-loader-pieni "Odota"]
       [ikonit/ikoni-ja-teksti (ikonit/livicon-upload) "Lähetä"])]))

(defn urakkagrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! {:keys [haetut-urakat urakoiden-haku-kaynnissa?] :as app}]
      [:div
      [napit/uusi "Lisää urakka"
       #(e! (tiedot/->UusiUrakka))
       {:disabled (nil? haetut-urakat)}]
      [grid/grid
       {:otsikko (if (and (some? haetut-urakat) urakoiden-haku-kaynnissa?)
                   [ajax-loader-pieni "Päivitetään listaa"] ;; Listassa on jo jotain, mutta sitä päivitetään
                   "Harjaan perustetut vesiväyläurakat")
        :tunniste :id
        :tyhja (if (nil? haetut-urakat)
                 [ajax-loader "Haetaan urakoita"]
                 "Urakoita ei löytynyt")
        :rivi-klikattu #(e! (tiedot/->ValitseUrakka %))}
       [{:otsikko "Nimi" :nimi :nimi}
        {:otsikko "Hallintayksikko" :nimi :hallintayksikon-nimi :hae #(get-in % [:hallintayksikko :nimi])}
        {:otsikko "Hanke" :nimi :hankkeen-nimi :hae #(get-in % [:hanke :nimi])}
        {:otsikko "Sopimukset (kpl)" :nimi :sopimukset-lkm :hae #(count (get % :sopimukset))}
        {:otsikko "Alku" :nimi :alkupvm :tyyppi :pvm :fmt pvm/pvm}
        {:otsikko "Loppu" :nimi :loppupvm :tyyppi :pvm :fmt pvm/pvm}
        {:otsikko "SÄHKE-lähetys" :nimi :sahke-lahetys :tyyppi :komponentti
         :komponentti (fn [urakka] [sahke-nappi e! app urakka])}]
       haetut-urakat]])))

(defn vesivaylaurakoiden-luonti* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {valittu-urakka :valittu-urakka :as app}]
      (if valittu-urakka
        [luontilomake e! app]
        [urakkagrid e! app]))))

(defc vesivaylaurakoiden-luonti []
  [tuck tiedot/tila vesivaylaurakoiden-luonti*])