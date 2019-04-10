(ns harja.views.vesivaylat.hallinta.urakoiden-luonti
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.vesivaylat.hallinta.urakoiden-luonti :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [namespacefy.core :refer [get-un]]
            [harja.domain.urakka :as u]
            [harja.domain.organisaatio :as o]
            [harja.domain.hanke :as h]
            [harja.domain.sopimus :as s]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.napit :as napit]

            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :refer [debug]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.ui.modal :as modal]
            [harja.ui.valinnat :as valinnat])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]))

(defn- sopimukset-grid [e! urakka haetut-sopimukset]
  (let [urakan-sopimukset (remove :poistettu (::u/sopimukset urakka))]
    [grid/muokkaus-grid
     {:tyhja "Liitä urakkaan ainakin yksi sopimus!"
      :voi-kumota? false ;; FIXME Kaatuu tällä hetkellä, ei ole olennaisen tärkeä
      :voi-poistaa? (fn [rivi] (> (count urakan-sopimukset) 1))}
     [{:otsikko "Nimi"
       :nimi ::s/sopimus
       :leveys 3
       :tyyppi :valinta
       :valinnat (tiedot/vapaat-sopimukset urakka haetut-sopimukset urakan-sopimukset)
       :virheet-dataan? true
       :valinta-nayta #(or (::s/nimi %) "- Valitse sopimus -")
       :hae identity
       :jos-tyhja-fn #(or (::s/nimi %) "Ei sopimuksia")
       :aseta (fn [_ arvo] arvo)}
      {:otsikko "Alku" :leveys 2 :nimi ::s/alkupvm :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? (constantly false)}
      {:otsikko "Loppu" :leveys 2 :nimi ::s/loppupvm :tyyppi :pvm :fmt pvm/pvm-opt :muokattava? (constantly false)}
      {:otsikko "Pääsopimus"
       :nimi ::s/paasopimus-id
       :leveys 1
       :tyyppi :string
       :fmt #(if (s/paasopimus-jokaiselle? urakan-sopimukset %)
               (ikonit/check)
               (ikonit/unchecked))
       :muokattava? (constantly false)
       :hae identity}]
     (r/wrap
       (zipmap (range) urakan-sopimukset)
       #(e! (tiedot/->PaivitaSopimuksetGrid (vals %))))]))

(defn voi-tallentaa? [urakka]
  (> (count (filter (comp id-olemassa? ::s/id)
                    (remove :poistettu (::u/sopimukset urakka))))
     0))

(defn luontilomake [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeLomakevaihtoehdot)))
    (fn [e! {:keys [valittu-urakka tallennus-kaynnissa?
                    haetut-hallintayksikot haetut-hankkeet haetut-urakoitsijat
                    haetut-sopimukset] :as app}]
      [:div
       [debug app]
       [napit/takaisin "Takaisin luetteloon"
        #(e! (tiedot/->ValitseUrakka nil))
        {:disabled tallennus-kaynnissa?}]
       (let [ilman-poistettuja #(remove :poistettu %)
             urakan-sopimukset (ilman-poistettuja (::u/sopimukset valittu-urakka))]
         [lomake/lomake
          {:otsikko (if (::u/id valittu-urakka)
                      "Muokkaa urakkaa"
                      "Luo uusi urakka")
           :muokkaa! #(e! (tiedot/->UrakkaaMuokattu (lomake/ilman-lomaketietoja %)))
           :voi-muokata? (oikeudet/hallinta-vesivaylat)
           :footer-fn (fn [urakka]
                        [napit/tallenna
                         "Tallenna urakka"
                         #(e! (tiedot/->TallennaUrakka (lomake/ilman-lomaketietoja urakka)))
                         {:ikoni (ikonit/tallenna)
                          :disabled (or tallennus-kaynnissa?
                                        (not (oikeudet/hallinta-vesivaylat))
                                        (not (voi-tallentaa? urakka))
                                        (not (lomake/voi-tallentaa? urakka)))
                          :tallennus-kaynnissa? tallennus-kaynnissa?}])}
          [(lomake/rivi
             {:otsikko "Nimi" :nimi ::u/nimi :tyyppi :string :pakollinen? true}
             {:otsikko "Urakka-aluenumero(t)" :nimi ::u/turvalaiteryhmat :tyyppi :string :pakollinen? true :placeholder "509, 510"}
             {:otsikko "Sampo-ID" :nimi ::u/sampoid :tyyppi :string :pakollinen? false :muokattava? (constantly false)})
           (lomake/rivi
             {:otsikko "Alkupäivämäärä" :nimi ::u/alkupvm :tyyppi :pvm :pakollinen? true
              :muokattava? (constantly (nil? (::u/id valittu-urakka)))}
             {:otsikko "Loppupäivämäärä" :nimi ::u/loppupvm :tyyppi :pvm :pakollinen? true
              :muokattava? (constantly (nil? (::u/id valittu-urakka)))
              :validoi [[:pvm-kentan-jalkeen ::u/alkupvm "Loppu ei voi olla ennen alkua"]]})
           (lomake/rivi
             (if haetut-hallintayksikot
               {:otsikko "Hallintayksikkö"
                :nimi ::u/hallintayksikko
                :tyyppi :valinta
                :pakollinen? true
                :valinnat haetut-hallintayksikot
                :valinta-nayta #(if % (::o/nimi %) "- Valitse hallintayksikkö -")
                :aseta (fn [rivi arvo] (assoc rivi ::u/hallintayksikko (dissoc arvo :alue :type)))}
               {:otsikko "Hallintayksikkö"
                :nimi ::u/hallintayksikko
                :tyyppi :komponentti
                :komponentti (fn [_] [ajax-loader-pieni "Haetaan hallintayksiköitä"])})
             (if haetut-urakoitsijat
               {:otsikko "Urakoitsija"
                :nimi ::u/urakoitsija
                :tyyppi :valinta
                :pakollinen? true
                :valinnat haetut-urakoitsijat
                :valinta-nayta #(if % (::o/nimi %) "- Valitse urakoitsija -")
                :aseta (fn [rivi arvo] (assoc rivi ::u/urakoitsija arvo))}
               {:otsikko "Urakoitsija"
                :nimi ::u/urakoitsija
                :tyyppi :komponentti
                :komponentti (fn [_] [ajax-loader-pieni "Haetaan urakoitsijoita"])}))
           (lomake/rivi
             {:otsikko "Sopimukset"
              :nimi ::u/sopimukset
              :palstoja 2
              :tyyppi :komponentti
              :komponentti (fn [{urakka :data}]
                             [sopimukset-grid e!
                              (lomake/ilman-lomaketietoja urakka) haetut-sopimukset])})
           {:otsikko "Pääsopimus"
            :nimi :paasopimus-id
            :tyyppi :valinta
            :valinnat (filter (comp id-olemassa? ::s/id) urakan-sopimukset)
            :valinta-nayta #(do
                              (cond
                                %
                                (::s/nimi %)

                                (> (count (filter (comp id-olemassa? ::s/id) urakan-sopimukset)) 1)
                                "Määrittele pääsopimus"

                                :else "Valitse urakalle sopimus"))
            :jos-tyhja "Urakalla ei sopimuksia"
            :muokattava? #(> (count (filter (comp id-olemassa? ::s/id) urakan-sopimukset)) 1)
            :pakollinen? (> (count (filter (comp id-olemassa? ::s/id) urakan-sopimukset)) 1)
            :aseta (fn [rivi arvo]
                     (assoc rivi ::u/sopimukset (tiedot/sopimukset-paasopimuksella
                                                  (::u/sopimukset rivi)
                                                  arvo)))
            :hae (fn [rivi]
                   (s/ainoa-paasopimus (ilman-poistettuja (::u/sopimukset rivi))))}]
          valittu-urakka])])))

(defn- muokkaus-otsikko [asia muokattu luotu]
  (if (pvm/jalkeen? (get-un asia :muokattu) (get-un asia :luotu))
    [(str muokattu " '" (get-un asia :nimi) "' muokattu:") (pvm/pvm-aika-opt (get-un asia :muokattu))]
    [(str luotu " '" (get-un asia :nimi) "' luotu:") (pvm/pvm-aika-opt (get-un asia :luotu))]))

(defn muokkaus-tiedot [{::u/keys [hanke sopimukset urakoitsija sahkelahetykset] :as urakka}]
  [:div "Tänne tulee metatietoja"]
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
  (let [lahetys-kaynnissa? (some? (lahetykset (::u/id urakka)))
        tila (tiedot/urakan-sahke-tila urakka)]
    [:button
     ;; TODO Käytä harja.ui.napit rajapintaa
     {:disabled (or (not (oikeudet/hallinta-vesivaylat)) lahetys-kaynnissa?)
      :class (str (case tila
                    :lahetetty "nappi-toissijainen "
                    :epaonnistunut "nappi-kielteinen "
                    :lahettamatta "nappi-ensisijainen ")
                  "nappi-grid")
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
       [valinnat/urakkatoiminnot {}
        [napit/uusi "Lisää urakka"
        #(e! (tiedot/->UusiUrakka))
        {:disabled (or (not (oikeudet/hallinta-vesivaylat)) (nil? haetut-urakat))}]]
       [grid/grid
        {:otsikko (if (and (some? haetut-urakat) urakoiden-haku-kaynnissa?)
                    [ajax-loader-pieni "Päivitetään listaa"] ;; Listassa on jo jotain, mutta sitä päivitetään
                    "Harjaan perustetut vesiväyläurakat")
         :tunniste ::u/id
         :tyhja (if (nil? haetut-urakat)
                  [ajax-loader "Haetaan urakoita"]
                  "Urakoita ei löytynyt")
         :rivi-klikattu #(e! (tiedot/->ValitseUrakka %))}
        [{:otsikko "Nimi" :nimi ::u/nimi}
         {:otsikko "Hallintayksikko" :nimi :hallintayksikon-nimi
          :hae #(get-in % [::u/hallintayksikko ::o/nimi])}
         {:otsikko "Hanke" :nimi :hankkeen-nimi :hae #(get-in % [::u/hanke ::h/nimi])}
         {:otsikko "Urakoitsija" :nimi :urakoitsijan-nimi :hae #(get-in % [::u/urakoitsija ::o/nimi])}
         {:otsikko "Sopimukset (kpl)" :nimi :sopimukset-lkm :hae #(count (get % ::u/sopimukset))}
         {:otsikko "Alku" :nimi ::u/alkupvm :tyyppi :pvm :fmt pvm/pvm}
         {:otsikko "Loppu" :nimi ::u/loppupvm :tyyppi :pvm :fmt pvm/pvm}
         {:otsikko "FIM lähetys" :nimi :sahke-lahetys :tyyppi :komponentti
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
