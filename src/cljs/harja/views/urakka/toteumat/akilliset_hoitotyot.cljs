(ns harja.views.urakka.toteumat.akilliset-hoitotyot
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.toteumat.mhu-akilliset-hoitotyot :as tiedot]
            [harja.ui.lomake :as ui-lomake]
            [harja.domain.toteuma :as t]
            [harja.ui.debug :as debug]
            [harja.loki :as loki]
            [harja.ui.napit :as napit]
            [reagent.core :as r]
            [harja.ui.kentat :as kentat]))


(defn- laheta! [e! data]
  (loki/log "data " data)
  (e! (tiedot/->LahetaLomake {})))

(defn- tyhjenna! [e! data]
  (loki/log "tyhjään"))

(defn- maaramitattavat-toteumat
  [{{toteumat ::t/toteumat} :data :as kaikki}]
  (loki/log "data" kaikki)
  [:<>
   [:div "Dippadii"]
   [kentat/tee-kentta
    {:otsikko               "Tehtävä"
     :nimi                  ::t/tehtava
     :pakollinen?           true
     ::ui-lomake/col-luokka ""
     :tyyppi                :valinta
     :valinnat              #{:yks :kaks :kol}}
    (r/wrap :yks #(loki/log "tehtävä"))]
   [kentat/tee-kentta
    {:otsikko               "Toteutunut määrä"
     ::ui-lomake/col-luokka ""
     :nimi                  ::t/maara
     :pakollinen?           true
     :tyyppi                :numero}
    (r/wrap :yks #(loki/log "tehtävä"))]
   [kentat/tee-kentta
    {:otsikko               "Lisätieto"
     ::ui-lomake/col-luokka ""
     :nimi                  ::t/lisatieto
     :pakollinen?           true
     :tyyppi                :string}
    (r/wrap :yks #(loki/log "tehtävä"))]])

(defn- akilliset-hoitotyot*
  [e! {lomake :lomake :as app}]
  (let [{ei-sijaintia ::t/ei-sijaintia
         tyyppi       ::t/tyyppi} lomake
        laheta-lomake! (r/partial laheta! e!)
        tyhjenna-lomake! (r/partial tyhjenna! e!)
        maaramitattava [{:otsikko               "Työ valmis"
                         :nimi                  ::t/pvm
                         ::ui-lomake/col-luokka ""
                         :pakollinen?           true
                         :tyyppi                :pvm}
                        {:otsikko     "Päivittäinen työaika"
                         :nimi        ::t/toteumat
                         :tyyppi      :komponentti
                         :komponentti maaramitattavat-toteumat}]
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
                  :valinnat              [:lisatyo]}
                 {:otsikko               "Kuvaus"
                  ::ui-lomake/col-luokka ""
                  :nimi                  ::t/kuvaus
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
                                  :valinnat              [:akilliset-hoitotyot :vahinkojen-korjaukset :tilaajan-varaukset]}
                                 {:otsikko               "Kuvaus"
                                  ::ui-lomake/col-luokka ""
                                  :nimi                  ::t/kuvaus
                                  :pakollinen?           false
                                  :tyyppi                :string}]]
    [:div#vayla
     [debug/debug lomake]
     [:div (pr-str ei-sijaintia)]
     [ui-lomake/lomake
      {:muokkaa!     #(e! (tiedot/->PaivitaLomake %))
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
           :valinnat              #{:eka :toka}
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
         {:otsikko "Sijainti *"}
         [{:nimi                  ::t/sijainti
           ::ui-lomake/col-luokka ""
           :teksti                "Kyseiseen tehtävään ei ole sijaintia"
           :pakollinen?           (not ei-sijaintia)
           :disabled?             ei-sijaintia
           :tyyppi                :tierekisteriosoite}
          {:nimi                  ::t/ei-sijaintia
           ::ui-lomake/col-luokka ""
           :teksti                "Kyseiseen tehtävään ei ole sijaintia"
           :tyyppi                :checkbox}])]
      lomake]]))

(defn akilliset-hoitotyot
  []
  [tuck/tuck tila/akilliset-hoitotyot-ja-vaurioiden-korjaukset akilliset-hoitotyot*])