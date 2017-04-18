(ns harja.views.vesivaylat.urakoiden-luonti
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.vesivaylat.urakoiden-luonti :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
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
            [harja.id :refer [id-olemassa?]]

            [harja.ui.debug :as debug]
            [harja.ui.modal :as modal])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn- sopimukset-grid [e! urakka haetut-sopimukset]
  (let [urakan-sopimukset (remove :poistettu (:sopimukset urakka))]
    [grid/muokkaus-grid
    {:tyhja "Liitä urakkaan ainakin yksi sopimus!"
     :voi-poistaa? (fn [rivi] (> (count urakan-sopimukset) 1))}
    [{:otsikko "Nimi"
      :nimi :sopimus
      :leveys 3
      :tyyppi :valinta
      :valinnat (tiedot/vapaat-sopimukset haetut-sopimukset urakan-sopimukset)
      :virheet-dataan? true
      :valinta-nayta #(or (:nimi %) "- Valitse sopimus -")
      :hae identity
      :jos-tyhja-fn #(or (:nimi %) "Ei sopimuksia")
      :aseta (fn [_ arvo] arvo)}
     {:otsikko "Alku" :leveys 2 :nimi :alkupvm :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? (constantly false)}
     {:otsikko "Loppu" :leveys 2 :nimi :loppupvm :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? (constantly false)}
     {:otsikko "Pääsopimus"
      :nimi :paasopimus
      :leveys 1
      :tyyppi :string
      :fmt #(if (tiedot/paasopimus? urakan-sopimukset %) (ikonit/check) (ikonit/unchecked))
      :muokattava? (constantly false)
      :hae identity}]
    (r/wrap
      (zipmap (range) urakan-sopimukset)
      #(e! (tiedot/->PaivitaSopimuksetGrid (vals %))))]))

(defn voi-tallentaa? [urakka]
  (and (> (count (filter (comp id-olemassa? :id) (:sopimukset urakka))) 0)))

(defn luontilomake [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeLomakevaihtoehdot)))
    (fn [e! {:keys [valittu-urakka tallennus-kaynnissa?
                    haetut-hallintayksikot haetut-hankkeet haetut-urakoitsijat
                    haetut-sopimukset] :as app}]
      [:div
       [napit/takaisin "Takaisin luetteloon"
        #(e! (tiedot/->ValitseUrakka nil))
        {:disabled (or (not (oikeudet/hallinta-vesivaylat)) tallennus-kaynnissa?)}]
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
                                       (not (oikeudet/hallinta-vesivaylat))
                                       (not (voi-tallentaa? urakka))
                                       (not (lomake/voi-tallentaa? urakka)))
                         :tallennus-kaynnissa? tallennus-kaynnissa?
                         }])}
         [{:otsikko "Nimi" :nimi :nimi :tyyppi :string
           :pakollinen? true}
          (lomake/rivi
            {:otsikko "Alkupäivämäärä" :nimi :alkupvm :tyyppi :pvm :pakollinen? true}
            {:otsikko "Loppupäivämäärä" :nimi :loppupvm :tyyppi :pvm :pakollinen? true
             :validoi [[:pvm-kentan-jalkeen :alkupvm "Loppu ei voi olla ennen alkua"]]})
          (lomake/rivi
            (if haetut-hallintayksikot
              {:otsikko "Hallintayksikkö"
               :nimi :hallintayksikko
               :tyyppi :valinta
               :pakollinen? true
               :valinnat haetut-hallintayksikot
               :valinta-nayta #(if % (:nimi %) "- Valitse hallintayksikkö -")
               :aseta (fn [rivi arvo] (assoc rivi :hallintayksikko (dissoc arvo :alue :type)))}
              {:otsikko "Hallintayksikkö"
               :nimi :hallintayksikko
               :tyyppi :komponentti
               :komponentti (fn [_] [ajax-loader-pieni "Haetaan hallintayksiköitä"])})
            (if haetut-urakoitsijat
              {:otsikko "Urakoitsija"
               :nimi :urakoitsija
               :tyyppi :valinta
               :pakollinen? true
               :valinnat haetut-urakoitsijat
               :valinta-nayta #(if % (:nimi %) "- Valitse urakoitsija -")
               :aseta (fn [rivi arvo] (assoc rivi :urakoitsija arvo))}
              {:otsikko "Urakoitsija"
               :nimi :urakoitsija
               :tyyppi :komponentti
               :komponentti (fn [_] [ajax-loader-pieni "Haetaan urakoitsijoita"])}))
          (lomake/ryhma
            {:otsikko "Hanke"
             :rivi? true}
            (if haetut-hankkeet
              {:otsikko "Nimi"
               :nimi :hanke
               :tyyppi :valinta
               :pakollinen? true
               :valinnat (remove (comp :id :urakka) haetut-hankkeet)
               :valinta-nayta #(if % (:nimi %) "- Valitse hanke -")
               :aseta (fn [rivi arvo] (assoc rivi :hanke arvo))}
             {:otsikko "Nimi"
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
             :muokattava? (constantly false)})
          (lomake/rivi
            {:otsikko "Sopimukset"
            :nimi :sopimukset
             :palstoja 2
            :tyyppi :komponentti
            :komponentti (fn [{urakka :data}] [sopimukset-grid e!
                                               (lomake/ilman-lomaketietoja urakka) haetut-sopimukset])})
          {:otsikko "Pääsopimus"
           :nimi :paasopimus
           :tyyppi :valinta
           :valinnat (filter (comp id-olemassa? :id) urakan-sopimukset)
           :valinta-nayta #(cond
                             %
                             (:nimi %)

                             (> (count (filter (comp id-olemassa? :id) urakan-sopimukset)) 1)
                             "Määrittele pääsopimus"

                             (= (count (filter (comp id-olemassa? :id) urakan-sopimukset)) 1)
                             "Urakalla vain yksi sopimus"

                             :else "Valitse urakalle sopimus")
           :jos-tyhja "Urakalla ei sopimuksia"
           :muokattava? #(> (count (filter (comp id-olemassa? :id) urakan-sopimukset)) 1)
           :pakollinen? (> (count (filter (comp id-olemassa? :id) urakan-sopimukset)) 1)
           :aseta (fn [rivi arvo] (assoc rivi :sopimukset (tiedot/aseta-paasopimus
                                                            (ilman-poistettuja (:sopimukset rivi))
                                                            arvo)))
           :hae (fn [rivi] (tiedot/paasopimus (ilman-poistettuja (:sopimukset rivi))))}]
         valittu-urakka])])))

(defn- muokkaus-otsikko [asia muokattu luotu]
  (if (pvm/jalkeen? (:muokattu asia) (:luotu asia))
    [(str muokattu " '" (:nimi asia) "' muokattu:") (pvm/pvm-aika-opt (:muokattu asia))]
    [(str luotu " '" (:nimi asia) "' luotu:") (pvm/pvm-aika-opt (:luotu asia))]))

(defn muokkaus-tiedot [{:keys [hanke sopimukset urakoitsija sahkelahetykset] :as urakka}]
  [:div "TÄnne tulee metatietoja"]
  (let [uusin (tiedot/uusin-lahetys sahkelahetykset)]
    [apply tietoja
    {:otsikot-omalla-rivilla? true}
    (concat
      (if uusin
        (if (:onnistui uusin)
         ["Viimeisin lähetys" (pvm/pvm-aika-opt (:lahetetty uusin))]
         ["Viimeisin yritys" (pvm/pvm-aika-opt (:lahetetty uusin))
          "Viimeisin onnistunut lähetys" (pvm/pvm-aika-opt (tiedot/uusin-onnistunut-lahetys sahkelahetykset))])
        ["Urakan tietoja ei ole vielä lähetetty!" ""])
      (muokkaus-otsikko urakka "Urakkaa" "Urakka")
      (muokkaus-otsikko hanke "Hanketta" "Hanke")
      (apply
        concat
        (for [sopimus sopimukset]
         (muokkaus-otsikko sopimus "Sopimusta" "Sopimus")))
      (muokkaus-otsikko urakoitsija "Urakoitsijaa" "Urakoitsija"))]))

(defn sahke-nappi [e! {lahetykset :kaynnissa-olevat-sahkelahetykset} urakka]
  (let [lahetys-kaynnissa? (some? (lahetykset (:id urakka)))
        tila (tiedot/urakan-sahke-tila urakka)]
    [:button
     {:disabled (or (not (oikeudet/hallinta-vesivaylat)) lahetys-kaynnissa?)
      :class (case tila
               :lahetetty "nappi-toissijainen"
               :epaonnistunut "nappi-kielteinen"
               :lahettamatta "nappi-ensisijainen")
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
       [ikonit/ikoni-ja-teksti
        (case tila
          :lahetetty (ikonit/livicon-check)
          :epaonnistunut (ikonit/livicon-upload)
          :lahettamatta (ikonit/livicon-upload))
        (case tila
          :lahetetty "Lähetetty"
          :epaonnistunut "Yritä uudelleen"
          :lahettamatta "Tietoja lähettämättä")])]))

(defn urakkagrid [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeUrakat)))
    (fn [e! {:keys [haetut-urakat urakoiden-haku-kaynnissa?] :as app}]
      [:div
      [napit/uusi "Lisää urakka" ;; TODO Oikeustarkistuksen mukaan disabloi tarvittaessa
       #(e! (tiedot/->UusiUrakka))
       {:disabled (or (not (oikeudet/hallinta-vesivaylat)) (nil? haetut-urakat))}]
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