(ns harja.views.urakka.toteumat.maarien-toteuma-lomake
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.toteumat.maarien-toteuma-lomake :as tiedot]
            [harja.ui.lomake :as ui-lomake]
            [harja.domain.toteuma :as t]
            [harja.ui.debug :as debug]
            [harja.loki :as loki]
            [harja.ui.napit :as napit]
            [reagent.core :as r]
            [harja.ui.kentat :as kentat]))


(defn- laheta! [e! data]
  (loki/log "data " data)
  (e! (tiedot/->LahetaLomake data)))

(defn- tyhjenna! [e! data]
  (loki/log "tyhjään")
  (e! (tiedot/->TyhjennaLomake data)))

(defn- maaramitattavat-toteumat
  [{:keys [e! toimenpiteet tehtavat]} {{toteumat ::t/toteumat :as lomake} :data :as kaikki}]
  (loki/log "data" kaikki)
  [:div
   (doall
     (map-indexed
       (fn [indeksi {tehtava      ::t/tehtava
                     maara        ::t/maara
                     lisatieto    ::t/lisatieto
                     sijainti     ::t/sijainti
                     ei-sijaintia ::t/ei-sijaintia
                     :as          _toteuma}]
         [(if (= 1 (count toteumat))
            :<>
            :div.row.lomakerivi.lomakepalstat)
          [(if (= 1 (count toteumat))
             :<>
             :div.lomakepalsta)
           [:div.row.lomakerivi
            [:label "Tehtävä"]
            [kentat/tee-kentta
             {:pakollinen?           true
              ::ui-lomake/col-luokka ""
              :vayla-tyyli?          true
              :tyyppi                :valinta
              :valinnat              tehtavat
              :valinta-nayta         :tehtava}
             (r/wrap tehtava
                     (fn [arvo]
                       (e! (tiedot/->PaivitaLomake (assoc-in lomake [::t/toteumat indeksi ::t/tehtava] arvo)))))]]
           [:div.row.lomakerivi
            [:label "Toteutunut määrä"]
            [kentat/tee-kentta
             {::ui-lomake/col-luokka ""
              :vayla-tyyli?          true
              :pakollinen?           true
              :tyyppi                :numero}
             (r/wrap maara
                     (fn [arvo]
                       (e! (tiedot/->PaivitaLomake (assoc-in lomake [::t/toteumat indeksi ::t/maara] arvo)))))]]
           [:div.row.lomakerivi
            [:label "Lisätieto"]
            [kentat/tee-kentta
             {::ui-lomake/col-luokka ""
              :vayla-tyyli?          true
              :pakollinen?           true
              :tyyppi                :string}
             (r/wrap lisatieto
                     (fn [arvo]
                       (e! (tiedot/->PaivitaLomake (assoc-in lomake [::t/toteumat indeksi ::t/lisatieto] arvo)))))]]]
          (when (not= (count toteumat) 1)
            [:div.lomakepalsta
             [:div.row.lomakerivi
              [:label "Lisätieto"]
              [kentat/tee-kentta
               {::ui-lomake/col-luokka ""
                :teksti                "Kyseiseen tehtävään ei ole sijaintia"
                :pakollinen?           (not ei-sijaintia)
                :disabled?             ei-sijaintia
                :tyyppi                :tierekisteriosoite}
               (r/wrap sijainti
                       (fn [arvo]
                         (e! (tiedot/->PaivitaLomake (assoc-in lomake [::t/toteumat indeksi ::t/sijainti] arvo)))))]]
             [:div.row.lomakerivi
              [:label "Lisätieto"]
              [kentat/tee-kentta
               {::ui-lomake/col-luokka ""
                :teksti                "Kyseiseen tehtävään ei ole sijaintia"
                :tyyppi                :checkbox}
               (r/wrap sijainti
                       (fn [arvo]
                         (e! (tiedot/->PaivitaLomake (assoc-in lomake [::t/toteumat indeksi ::t/ei-sijaintia] arvo)))))]]])])
       toteumat))
   (when (> (count toteumat) 1)
     [napit/tallenna
      "Lisää tehtävä"
      #(e! (tiedot/->LisaaToteuma lomake))
      {:ikoni         [harja.ui.ikonit/plus-sign]
       :vayla-tyyli?  true
       :teksti-nappi? true}])])

(defn- maarien-toteuman-syottolomake*
  [e! {lomake :lomake toimenpiteet :toimenpiteet tehtavat :tehtavat :as app}]
  (let [{tyyppi   ::t/tyyppi
         toteumat ::t/toteumat} lomake
        {ei-sijaintia ::t/ei-sijaintia
         sijainti     ::t/sijainti} (-> toteumat first)
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
                                                                                     :tehtavat     tehtavat})}]
        lisatyo [{:otsikko               "Pvm"
                  :nimi                  ::t/pvm
                  ::ui-lomake/col-luokka ""
                  :pakollinen?           true
                  :tyyppi                :pvm}
                 {:otsikko               "Tehtävä"
                  :nimi                  ::t/tehtava
                  :pakollinen?           true
                  ::ui-lomake/col-luokka ""
                  :tyyppi                :valinta
                  :valinta-nayta         :tehtava
                  :valinnat              tehtavat}
                 {:otsikko               "Kuvaus"
                  ::ui-lomake/col-luokka ""
                  :nimi                  ::t/lisatieto
                  :pakollinen?           false
                  :tyyppi                :string}]
        akilliset-ja-korjaukset [{:otsikko               "Pvm"
                                  :nimi                  ::t/pvm
                                  ::ui-lomake/col-luokka ""
                                  :pakollinen?           true
                                  :tyyppi                :pvm}
                                 {:otsikko               "Tehtävä"
                                  :nimi                  ::t/tehtava
                                  :pakollinen?           true
                                  ::ui-lomake/col-luokka ""
                                  :tyyppi                :valinta
                                  :valinnat              tehtavat
                                  :valinta-nayta         :tehtava}
                                 {:otsikko               "Kuvaus"
                                  ::ui-lomake/col-luokka ""
                                  :nimi                  ::t/lisatieto
                                  :pakollinen?           false
                                  :tyyppi                :string}]]
    (loki/log "tyyppi" tyyppi)
    [:div#vayla
     [debug/debug app]
     [debug/debug lomake]
     [:div (pr-str ei-sijaintia)]
     [ui-lomake/lomake
      {:muokkaa!     (fn [data]
                       (loki/log "dataa " data)
                       (e! (tiedot/->PaivitaLomake data)))
       :voi-muokata? true
       :palstoja     2
       :footer-fn    (fn [data]
                       [:div.flex-row
                        [napit/tallenna
                         "Tallenna"
                         #(laheta-lomake! data)
                         {:vayla-tyyli? true
                          :luokka       "suuri"}]
                        [napit/peruuta
                         "Peruuta"
                         #(tyhjenna-lomake! data)
                         {:vayla-tyyli? true
                          :luokka       "suuri"}]])
       :vayla-tyyli? true}
      [(ui-lomake/palstat
         {}
         {:otsikko "Mihin toimenpiteeseen työ liittyy?"}
         [{:otsikko               "Toimenpide"
           :nimi                  ::t/toimenpide
           ::ui-lomake/col-luokka ""
           :pakollinen?           true
           :valinnat              toimenpiteet
           :valinta-nayta         :otsikko
           :tyyppi                :valinta}])
       {:tyyppi           :radio-group
        :nimi             ::t/tyyppi
        :oletusarvo       :maaramitattava
        :otsikko          ""
        :vaihtoehdot      [:maaramitattava :akillinen-hoitotyo :lisatyo]
        :pakollinen?      true
        :vaihtoehto-nayta {:maaramitattava     "Määrämitattava tehtävä"
                           :akillinen-hoitotyo "Äkillinen hoitotyö, vahingon korjaus, rahavaraus"
                           :lisatyo            "Lisätyö"}}
       {:tyyppi  :checkbox
        :nimi    ::t/useampi-toteuma
        :otsikko "Haluan syöttää useamman toteuman tälle toimenpiteelle"}
       (ui-lomake/palstat
         {}
         {:otsikko "Tehtävän tiedot"}
         (case tyyppi
           :maaramitattava maaramitattava
           :lisatyo lisatyo
           :akillinen-hoitotyo akilliset-ja-korjaukset
           [])
         (when (= (count toteumat) 1)
           {:otsikko "Sijainti *"})
         (when (= (count toteumat) 1)
           [{:nimi                  [::t/toteumat 0 ::t/sijainti]
             ::ui-lomake/col-luokka ""
             :teksti                "Kyseiseen tehtävään ei ole sijaintia"
             :pakollinen?           (not ei-sijaintia)
             :disabled?             ei-sijaintia
             :tyyppi                :tierekisteriosoite
             :sijainti              (r/wrap sijainti
                                            (constantly true)) ; lomake päivittyy eri funkkarilla, niin never mind this, mutta annetaan sijainti silti
             }
            {:nimi                  [::t/toteumat 0 ::t/ei-sijaintia]
             ::ui-lomake/col-luokka ""
             :teksti                "Kyseiseen tehtävään ei ole sijaintia"
             :tyyppi                :checkbox}]))]
      lomake]]))

(defn akilliset-hoitotyot
  []
  [tuck/tuck tila/toteumat-maarat maarien-toteuman-syottolomake*])