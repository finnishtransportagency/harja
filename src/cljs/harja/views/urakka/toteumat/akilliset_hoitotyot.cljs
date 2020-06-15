(ns harja.views.urakka.toteumat.akilliset-hoitotyot
  (:require [tuck.core :as tuck]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.toteumat.mhu-akilliset-hoitotyot :as tiedot]
            [harja.ui.lomake :as ui-lomake]
            [harja.ui.debug :as debug]
            [harja.loki :as loki]
            [harja.ui.napit :as napit]))


(defn- akilliset-hoitotyot*
  [e! {:keys [lomake] :as app}]
  [:div#vayla
   [debug/debug lomake]
   [ui-lomake/lomake
    {:muokkaa!     #(e! (tiedot/->PaivitaLomake %))
     :voi-muokata? true
     :palstoja     2
     :footer-fn    (fn [data]
                     [:div.flex-row
                      [napit/tallenna
                       "Tallenna"
                       #(loki/log "tallenna" data)
                       {:vayla-tyyli? true
                        :luokka "suuri"}]
                      [napit/peruuta
                       "Peruuta"
                       #(loki/log "peruuta" data)
                       {:vayla-tyyli? true
                        :luokka "suuri"}]])
     :vayla-tyyli? true}
    [(ui-lomake/palsta
       {}
       {:otsikko "Tieto"}
       [{:otsikko     "Lisätieto"
         :nimi        :lisatieto
         :harja.ui.lomake/col-luokka ""
         :pakollinen? true
         :tyyppi      :string}
        {:otsikko     "Pvm"
         :nimi        :pvm
         :harja.ui.lomake/col-luokka ""
         :pakollinen? true
         :tyyppi      :string}])
     (ui-lomake/palsta
       {}
       {:otsikko "Tehtävän tiedot"}
       [{:otsikko                    "Tehtävä"
         :nimi                       :tehtava
         :pakollinen?                true
         :harja.ui.lomake/col-luokka ""
         :tyyppi                     :string}
        {:otsikko                    "Pvm"
         :nimi                       :pvm
         :harja.ui.lomake/col-luokka ""
         :pakollinen?                true
         :tyyppi                     :string}
        {:otsikko                    "Lisätiedot"
         :harja.ui.lomake/col-luokka ""
         :nimi                       :lisatiedot
         :pakollinen?                false
         :tyyppi                     :string}]
       {:otsikko "Sijainti *"}
       [{:nimi                       :sijainti
         :harja.ui.lomake/col-luokka ""
         :teksti                     "Kyseiseen tehtävään ei ole sijaintia"
         :tyyppi                     :tierekisteriosoite}
        {:nimi                       :ei-sijaintia
         :harja.ui.lomake/col-luokka ""
         :teksti                     "Kyseiseen tehtävään ei ole sijaintia"
         :tyyppi                     :checkbox}])]
    lomake]])

(defn akilliset-hoitotyot
  []
  [tuck/tuck tila/akilliset-hoitotyot-ja-vaurioiden-korjaukset akilliset-hoitotyot*])