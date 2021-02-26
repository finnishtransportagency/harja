(ns harja.views.urakka.toteumat.maarien-toteuma-lomake
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.toteumat.maarien-toteumat :as tiedot]
            [harja.ui.lomake :as ui-lomake]
            [harja.domain.toteuma :as t]
            [harja.ui.debug :as debug]
            [harja.loki :as loki]
            [harja.ui.napit :as napit]
            [reagent.core :as r]
            [harja.ui.kentat :as kentat]
            [harja.ui.modal :as modal]
            [harja.pvm :as pvm]
            [harja.views.kartta.tasot :as tasot]))


(defn- laheta! [e! data]
  (e! (tiedot/->LahetaLomake data)))

(defn- tyhjenna! [e! data]
  (e! (tiedot/->TyhjennaLomake data)))

(defn- toteuman-poiston-varmistus-modaali [{:keys [varmistus-fn pvm tehtava maara tyyppi] :as mod}]
  [:div.row
   [:div.col-md-12 {:style {:padding-bottom "1rem"}} (pvm/pvm pvm)]
   [:div.col-md-12 {:style {:padding-bottom "1rem"}} (:tehtava tehtava)]
   [:div.col-md-12 {:style {:padding-bottom "1rem"}}
    (str maara " " (cond
                     (or
                       (= tyyppi :kokonaishintainen)
                       (= tyyppi :maaramitattava))
                     (:yksikko tehtava)

                     (or (= tyyppi :lisatyo)
                         (= tyyppi :akillinen-hoitotyo)
                         (= tyyppi :vahinkojen-korjaukset)
                         (= tyyppi :muut-rahavaraukset))
                     "kpl"

                     :else
                     ""))]

   [:div {:style {:padding-bottom "1rem"}}
    [:span {:style {:padding-right "1rem"}}
     [napit/yleinen-toissijainen
      "Peruuta"
      (r/partial (fn []
                   (modal/piilota!)))
      {:vayla-tyyli? true
       :luokka       "suuri"}]]
    [:span
     [napit/poista
      "Poista tiedot"
      varmistus-fn
      {:vayla-tyyli? true
       :luokka       "suuri"}]]]])

(defn- poista-fn [{:keys [toteuma-id indeksi e! lomake paivita!]}]
  (let [indeksi (or indeksi
                    0)]
    (if toteuma-id
      (r/partial paivita! ::t/poistettu indeksi)
      (fn [_]
        (e! (tiedot/->PaivitaLomake (update lomake
                                            ::t/toteumat
                                            (fn [toteumat]
                                              (let [toteumat (vec toteumat)]
                                                (vec (concat (subvec toteumat 0 indeksi)
                                                             (subvec toteumat (inc indeksi)))))))
                                    ::t/poistettu
                                    indeksi))))))

(defn- maaramitattavat-toteumat
  [{:keys [e! tehtavat app]} {{toteumat ::t/toteumat
                               validius ::tila/validius
                               :as      lomake} :data}]
  (let [paivita! (fn [polku indeksi arvo]
                   (if-not
                     (= polku :tierekisteriosoite)
                     (e! (tiedot/->PaivitaLomake (assoc-in lomake [::t/toteumat indeksi polku] arvo) polku indeksi))
                     (e! (tiedot/->PaivitaSijaintiMonelle arvo indeksi))))
        useampi? (> (count toteumat) 1)
        yksittainen? (= (count toteumat) 1)
        validi? (fn [polku]
                  (if validius
                    (not (get-in validius [polku :validi?]))
                    false))]
    [:div
     (doall
       (map-indexed
         (fn [indeksi {tehtava      ::t/tehtava
                       maara        ::t/maara
                       lisatieto    ::t/lisatieto
                       ei-sijaintia ::t/ei-sijaintia
                       toteuma-id   ::t/toteuma-id
                       poistettu    ::t/poistettu
                       :as          _toteuma}]
           (let [yksikko (:yksikko tehtava)
                 sijainti (get-in app [:sijainti indeksi])
                 palstat-tagi (if yksittainen?
                                :<>
                                :div.row.lomakepalstat)
                 palsta-tagi (if yksittainen?
                               :<>
                               :div.lomakepalsta)
                 otsikko-tagi (if useampi?
                                :div.useampi-tehtava-osio
                                :<>)
                 poista! (poista-fn {:toteuma-id toteuma-id
                                     :indeksi    indeksi
                                     :e!         e!
                                     :lomake     lomake
                                     :paivita!   paivita!})]
             [otsikko-tagi {:key (str "toteuma-" indeksi)}
              (when useampi?
                [:div.flex-row
                 [:div.row
                  (str "TEHTÄVÄ " (inc indeksi))]
                 [:div.row
                  [napit/poista
                   "Poista toteuma"
                   (r/partial #(modal/nayta! {:otsikko "Haluatko varmasti poistaa toteuman?"}
                                             [toteuman-poiston-varmistus-modaali
                                              {:varmistus-fn (fn []
                                                               (modal/piilota!)
                                                               (poista! true))
                                               :pvm          (get-in lomake [::t/pvm])
                                               :tehtava      (get-in lomake [::t/toteumat indeksi ::t/tehtava])
                                               :maara        (get-in lomake [::t/toteumat indeksi ::t/maara])
                                               :tyyppi       (::t/tyyppi lomake)}]))
                   {:vayla-tyyli? true :teksti-nappi? true}]]])
              [palstat-tagi
               [palsta-tagi
                [:div.row.form-group.required
                 [:label.control-label [:span [:span.kentan-label "Tehtävä"]]]
                 [kentat/tee-kentta
                  {:otsikko               "Tehtävä"
                   :nimi                  [::t/toteumat indeksi ::t/tehtava]
                   ::ui-lomake/col-luokka ""
                   :virhe?                (validi? [::t/toteumat indeksi ::t/tehtava])
                   :tyyppi                :valinta
                   :valinta-nayta         :tehtava
                   :vayla-tyyli?          true
                   :valinta-arvo          identity
                   :valinnat              tehtavat
                   :pakollinen?           true
                   :elementin-id          (str "tehtava-valikko-" indeksi)}
                  (r/wrap tehtava
                          (r/partial paivita! ::t/tehtava indeksi))]]
                [:div.row.form-group.required
                 [:label.control-label [:span [:span.kentan-label "Toteutunut määrä"]]]
                 [kentat/tee-kentta
                  {::ui-lomake/col-luokka ""
                   :vayla-tyyli?          true
                   :virhe?                (validi? [::t/toteumat indeksi ::t/maara])
                   :tyyppi                :numero
                   :yksikko               yksikko
                   :pakollinen?           true}
                  (r/wrap maara
                          (r/partial paivita! ::t/maara indeksi))]]
                [:div.row
                 [:label "Lisätieto"]
                 [kentat/tee-kentta
                  {::ui-lomake/col-luokka ""
                   :vayla-tyyli?          true
                   :virhe?                (validi? [::t/toteumat indeksi ::t/lisatieto])
                   :tyyppi                :string}
                  (r/wrap lisatieto
                          (r/partial paivita! ::t/lisatieto indeksi))]]]
               (when useampi?
                 [:div.lomakepalsta
                  [:div.row
                   [kentat/tee-kentta
                    {:nimi                  [indeksi :tierekisteriosoite]
                     ::ui-lomake/col-luokka ""
                     :teksti                "Kyseiseen tehtävään ei ole sijaintia"
                     :pakollinen?           (not ei-sijaintia)
                     :disabled?             ei-sijaintia
                     :tyyppi                :tierekisteriosoite
                     :vayla-tyyli?          true
                     :sijainti              (r/wrap sijainti (constantly true))}
                    (r/wrap sijainti
                            (r/partial paivita! :tierekisteriosoite indeksi))]]
                  [:div.row
                   [kentat/tee-kentta
                    {::ui-lomake/col-luokka ""
                     :vayla-tyyli?          true
                     :teksti                "Kyseiseen tehtävään ei ole sijaintia"
                     :tyyppi                :checkbox}
                    (r/wrap ei-sijaintia
                            (r/partial paivita! ::t/ei-sijaintia indeksi))]]])]]))
         toteumat))
     (when useampi?
       [napit/tallenna
        "Lisää tehtävä"
        #(e! (tiedot/->LisaaToteuma lomake))
        {:ikoni         [harja.ui.ikonit/plus-sign]
         :vayla-tyyli?  true
         :teksti-nappi? true}])]))

(defn maarien-toteuman-syottolomake*
  [e! {lomake :lomake toimenpiteet :toimenpiteet tehtavat :tehtavat :as app}]
  (let [{tyyppi       ::t/tyyppi
         toteumat     ::t/toteumat
         validius     ::tila/validius
         koko-validi? ::tila/validi?} lomake
        {ei-sijaintia ::t/ei-sijaintia
         toteuma-id   ::t/toteuma-id
         sijainti     ::t/sijainti} (-> toteumat first)
        validi? (fn [polku]
                  (if validius
                    (not (get-in validius [polku :validi?]))
                    false))
        laheta-lomake! (r/partial laheta! e!)
        tyhjenna-lomake! (r/partial tyhjenna! e!)
        maaramitattava [{:otsikko               "Työ valmis"
                         :nimi                  ::t/pvm
                         ::ui-lomake/col-luokka ""
                         :pakollinen?           true
                         :tyyppi                :pvm}
                        {:nimi                  ::t/toteumat
                         ::ui-lomake/col-luokka ""
                         :tyyppi                :komponentti
                         :komponentti           (r/partial maaramitattavat-toteumat {:e!           e!
                                                                                     :toimenpiteet toimenpiteet
                                                                                     :tehtavat     tehtavat
                                                                                     :app          app})}]
        lisatyo [{:otsikko               "Pvm"
                  :nimi                  ::t/pvm
                  ::ui-lomake/col-luokka ""
                  :virhe?                (validi? [::t/pvm])
                  :tyyppi                :pvm
                  :pakollinen?           true}
                 {:otsikko               "Tehtävä"
                  :nimi                  [::t/toteumat 0 ::t/tehtava]
                  ::ui-lomake/col-luokka ""
                  :virhe?                (validi? [::t/toteumat 0 ::t/tehtava])
                  :tyyppi                :valinta
                  :valinta-nayta         :tehtava
                  :valinta-arvo          identity
                  :valinnat              tehtavat
                  :pakollinen?           true
                  :elementin-id          (str "lisatyot-tehtavat")}
                 {:otsikko               "Kuvaus"
                  ::ui-lomake/col-luokka ""
                  :nimi                  [::t/toteumat 0 ::t/lisatieto]
                  :virhe?                (validi? [::t/toteumat 0 ::t/lisatieto])
                  :tyyppi                :string
                  :vihje                 "Lyhyt kuvaus tehdystä työstä ja kustannuksesta."
                  :pakollinen?           true}]
        akilliset-ja-korjaukset [{:otsikko               "Pvm"
                                  :nimi                  ::t/pvm
                                  ::ui-lomake/col-luokka ""
                                  :virhe?                (validi? [::t/pvm])
                                  :tyyppi                :pvm
                                  :pakollinen?           true}
                                 {:otsikko               "Tehtävä"
                                  :nimi                  [::t/toteumat 0 ::t/tehtava]
                                  ::ui-lomake/col-luokka ""
                                  :tyyppi                :valinta
                                  :virhe?                (validi? [::t/toteumat 0 ::t/tehtava])
                                  :valinnat              tehtavat
                                  :valinta-arvo          identity
                                  :valinta-nayta         (fn [arvo]
                                                           (:tehtava arvo))
                                  :pakollinen?           true
                                  :elementin-id          (str "akilliset-tehtavat-")}
                                 {:otsikko               "Kuvaus"
                                  ::ui-lomake/col-luokka ""
                                  :nimi                  [::t/toteumat 0 ::t/lisatieto]
                                  :virhe?                (validi? [::t/toteumat 0 ::t/lisatieto])
                                  :tyyppi                :string
                                  :vihje                 "Lyhyt kuvaus tehdystä työstä ja kustannuksesta."}]
        poista! (poista-fn {:toteuma-id toteuma-id
                            :e!         e!
                            :lomake     lomake
                            :paivita!   (fn [polku indeksi arvo]
                                          (e! (tiedot/->PaivitaLomake (assoc-in lomake [::t/toteumat indeksi polku] arvo) polku indeksi)))})]
    [:div#vayla
     #_[debug/debug app]
     #_[debug/debug lomake]
     #_[debug/debug validius]
     [:div.flex-row {:style {:padding-left  "15px"
                             :padding-right "15px"}}
      [napit/takaisin "Takaisin" #(tyhjenna-lomake! nil) {:vayla-tyyli? true :teksti-nappi? true}]]
     [ui-lomake/lomake
      {:muokkaa!                          (fn [data]
                                            (if (and (not (keyword? (::ui-lomake/viimeksi-muokattu-kentta data)))
                                                     (> (count (::ui-lomake/viimeksi-muokattu-kentta data)) 1)
                                                     (= (second (::ui-lomake/viimeksi-muokattu-kentta data)) :tierekisteriosoite))
                                              (e! (tiedot/->PaivitaSijainti data 0))
                                              (e! (tiedot/->PaivitaLomake data nil 0))))
       :voi-muokata?                      true
       :tarkkaile-ulkopuolisia-muutoksia? true
       :palstoja                          2
       :header-fn                         (fn [data]
                                            [:<>
                                             [:div.flex-row {:style {:padding-left  "15px"
                                                                     :padding-right "15px"}}
                                              [:h2 (if toteuma-id
                                                     "Muokkaa toteumaa"
                                                     "Uusi toteuma")]
                                              (when (and toteuma-id
                                                         (= (count toteumat) 1))
                                                [napit/poista
                                                 "Poista toteuma"
                                                 #(modal/nayta! {:otsikko "Haluatko varmasti poistaa toteuman?"}
                                                                [toteuman-poiston-varmistus-modaali
                                                                 {:varmistus-fn (fn []
                                                                                  (modal/piilota!)
                                                                                  (if (> (count toteumat) 1)
                                                                                    (poista! true)
                                                                                    (e! (tiedot/->PoistaToteuma toteuma-id))))
                                                                  :pvm          (get-in lomake [::t/pvm])
                                                                  :tehtava      (get-in lomake [::t/toteumat 0 ::t/tehtava]) ;; Voi käyttää kova koodattua indeksiä, koska tämä ei ole käytössä, jos toteumia on useampi
                                                                  :maara        (get-in lomake [::t/toteumat 0 ::t/maara])
                                                                  :tyyppi       (::t/tyyppi lomake)}])
                                                 {:vayla-tyyli? true :teksti-nappi? true}])]])
       :footer-fn                         (fn [data]
                                            [:div.flex-row.alkuun
                                             [napit/tallenna
                                              "Tallenna"
                                              #(laheta-lomake! data)
                                              {:vayla-tyyli? true
                                               :luokka       "suuri"
                                               :disabled     (not koko-validi?)}]
                                             [napit/peruuta
                                              "Peruuta"
                                              #(tyhjenna-lomake! data)
                                              {:vayla-tyyli? true
                                               :luokka       "suuri"}]])
       :vayla-tyyli?                      true}
      [{:teksti                "Mihin toimenpiteeseen työ liittyy?"
        :tyyppi                :valiotsikko
        ::ui-lomake/col-luokka "col-xs-12"}
       {:otsikko               "Toimenpide"
        :nimi                  ::t/toimenpide
        ::ui-lomake/col-luokka "col-xs-12 col-sm-12 col-md-8"
        :virhe?                (validi? [::t/toimenpide])
        :valinnat              toimenpiteet
        :valinta-nayta         :otsikko
        :valinta-arvo          identity
        :aseta-vaikka-sama?    true
        :tyyppi                :valinta
        :vayla-tyyli?          true
        :palstoja              1
        :disabled?             (not (= :maaramitattava (::t/tyyppi lomake)))
        :pakollinen?           true
        :elementin-id          (str "toimenpiteet-")}
       {:tyyppi           :radio-group
        :nimi             ::t/tyyppi
        :otsikko          ""
        :vaihtoehdot      [:maaramitattava :akillinen-hoitotyo :lisatyo]
        :nayta-rivina?    true
        :palstoja         2
        :vayla-tyyli?     true
        :disabloitu?      (not (nil? (get-in lomake [::t/toteumat 0 ::t/toteuma-id])))
        :vaihtoehto-nayta {:maaramitattava     "Määrämitattava tehtävä"
                           :akillinen-hoitotyo "Äkillinen hoitotyö, vahingon korjaus, rahavaraus"
                           :lisatyo            "Lisätyö"}}
       (when (and
               (= :maaramitattava tyyppi)
               (nil? (get-in lomake [::t/toteumat 0 ::t/toteuma-id])))
         {:tyyppi    :checkbox
          :nimi      ::t/useampi-toteuma
          :disabled? (not= tyyppi :maaramitattava)
          :teksti    "Haluan syöttää useamman toteuman tälle toimenpiteelle"})
       (ui-lomake/palstat
         {}
         {:otsikko "Tehtävän tiedot"}
         (case tyyppi
           :maaramitattava maaramitattava
           :lisatyo lisatyo
           (:vahinkojen-korjaukset :tilaajan-varaukset :akillinen-hoitotyo) akilliset-ja-korjaukset
           maaramitattava)                                  ;; Default arvona oletetaan sen olevan määrämitattava
         (when (= (count toteumat) 1)
           {:otsikko "Sijainti *"})
         (when (= (count toteumat) 1)
           [{:nimi                  [0 :tierekisteriosoite]
             ::ui-lomake/col-luokka ""
             :teksti                "Kyseiseen tehtävään ei ole sijaintia"
             :pakollinen?           (not ei-sijaintia)
             :disabled?             ei-sijaintia
             :tyyppi                :tierekisteriosoite
             :vayla-tyyli?          true
             :sijainti              (r/wrap sijainti (constantly true))}
            {:nimi                  [::t/toteumat 0 ::t/ei-sijaintia]
             ::ui-lomake/col-luokka ""
             :teksti                "Kyseiseen tehtävään ei ole sijaintia"
             :tyyppi                :checkbox}]))]
      lomake]]))

(defn akilliset-hoitotyot
  []
  [tuck/tuck tila/toteumanakyma maarien-toteuman-syottolomake*])