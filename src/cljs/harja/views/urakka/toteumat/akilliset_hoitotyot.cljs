(ns harja.views.urakka.toteumat.akilliset-hoitotyot
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.toteumat.mhu-akilliset-hoitotyot :as tiedot]
            [harja.ui.lomake :as ui-lomake]
            [harja.domain.toteuma :as t]
            [harja.ui.debug :as debug]
            [harja.loki :as loki]
            [harja.ui.napit :as napit]
            [reagent.core :as r]))


(defn- laheta! [e! data]
  (e! (tiedot/->LahetaLomake {})))

(defn- tyhjenna! [e! data]
  (loki/log "tyhjään"))

(defn- akilliset-hoitotyot*
  [e! {lomake :lomake :as app}]
  (let [{ei-sijaintia ::ei-sijaintia} lomake
        laheta-lomake! (r/partial laheta! e!)
        tyhjenna-lomake! (r/partial tyhjenna! e!)]
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
                         laheta-lomake!
                         {:vayla-tyyli? true
                          :luokka       "suuri"}]
                        [napit/peruuta
                         "Peruuta"
                         tyhjenna-lomake!
                         {:vayla-tyyli? true
                          :luokka       "suuri"}]])
       :vayla-tyyli? true}
      [(ui-lomake/palstat
         {}
         {:otsikko "Tieto"}
         [{:otsikko                    "Lisätieto"
           :nimi                       ::t/lisatieto
           :harja.ui.lomake/col-luokka ""
           :pakollinen?                true
           :tyyppi                     :string}
          {:otsikko                    "Pvm"
           :nimi                       ::t/pvm
           :harja.ui.lomake/col-luokka ""
           :pakollinen?                true
           :tyyppi                     :string}])
       (ui-lomake/palstat
         {}
         {:otsikko "Tehtävän tiedot"}
         [{:otsikko                    "Tehtävä"
           :nimi                       ::t/tehtava
           :pakollinen?                true
           :harja.ui.lomake/col-luokka ""
           :tyyppi                     :valinta
           :valinnat                   #{:yks :kaks :kol}}
          {:otsikko                    "Pvm"
           :nimi                       ::t/pvm
           :harja.ui.lomake/col-luokka ""
           :pakollinen?                true
           :tyyppi                     :pvm}
          {:otsikko                    "Kuvaus"
           :harja.ui.lomake/col-luokka ""
           :nimi                       ::t/kuvaus
           :pakollinen?                false
           :tyyppi                     :string}]
         {:otsikko "Sijainti *"}
         [{:nimi                       ::t/sijainti
           :harja.ui.lomake/col-luokka ""
           :teksti                     "Kyseiseen tehtävään ei ole sijaintia"
           :pakollinen?                (not ei-sijaintia)
           :disabled?                  ei-sijaintia
           :tyyppi                     :tierekisteriosoite}
          {:nimi                       ::t/ei-sijaintia
           :harja.ui.lomake/col-luokka ""
           :teksti                     "Kyseiseen tehtävään ei ole sijaintia"
           :tyyppi                     :checkbox}])]
      lomake]]))

(defn akilliset-hoitotyot
  []
  [tuck/tuck tila/akilliset-hoitotyot-ja-vaurioiden-korjaukset akilliset-hoitotyot*])