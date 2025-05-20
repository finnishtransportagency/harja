(ns harja.views.urakka.muutos-nakyma
  "MHU-urakoiden muutosten välilehti. Hallinnoi ja näyttää tarjouksen pohjatietoihin ja tavoitehintaan tehtäviä muutoksia."
  (:require [cljs.core.async :refer [<!]]
            [harja.ui.napit :as napit]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.debug :refer [debug]]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.yleiset :as y]
            [harja.loki :refer [log logt]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.muutos-tiedot :as muutos-tiedot])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))


(defn- kirjatut-muutokset [e! {:keys [muutokset] :as app}]
  [:span.kirjatut.muutokset
   [grid/grid
    {:otsikko "Kirjatut muutokset"
     :tunniste :id
     :luokat ["kirjatut-muutokset-grid"]
     :voi-lisata? false :voi-kumota? false
     :voi-poistaa? (constantly false) :voi-muokata? false}

    ;; taulukon kentät
    [{:otsikko "Tyyppi" :nimi :tyyppi :tyyppi :string :leveys 15}
     {:otsikko "Muutoksen syy" :nimi :syy :tyyppi :string :leveys 35}
     {:otsikko "Voimassa alkaen" :nimi :voimassa_alkaen :tyyppi :pvm :leveys 15}
     {:otsikko "Tavoitehinnan muutos" :nimi :tavoitehinnan-muutos :tyyppi :numero :leveys 15}
     {:otsikko "" :nimi :toiminnot :tyyppi :komponentti :leveys 15
      :komponentti (fn [rivi]
                     [napit/muokkaa "Muokkaa"
                      #(e! (muutos-tiedot/->MuokkaaMuutosta rivi))])}]
    muutokset]])

(defn muutokset-alempi-valilehti*
  [e! app]
  (let [urakka (:urakka @tila/yleiset)]
    (komp/luo
      (komp/sisaan-ulos
        #(do
           (when urakka
             (e! (muutos-tiedot/->ValitseUrakka urakka))
             (e! (muutos-tiedot/->HaeUrakanMuutostiedot urakka))))
        #(e! (muutos-tiedot/->NakymastaPoistuttiin)))
      (komp/watcher nav/valittu-urakka
        (fn [_ _ urakka]
          (when urakka
            (e! (muutos-tiedot/->ValitseUrakka urakka)))))
      (fn [e! app]
        [:span.muutokset-sivu
         [y/vihje "Muutokset-osio on työn alla ja käytettävissä vain testiympäristössä."]
         [:div.otsikko-ja-hoitokausi
          [:h1 "Muutokset"]
          [valinnat/urakan-hoitokausi-tuck (:valittu-hoitokausi app)
           (:urakan-hoitokaudet app)
           #(e! (muutos-tiedot/->HoitokausiVaihdettu urakka %))]]

         ;; Kirjatut muutokset
         [kirjatut-muutokset e! app]

         [debug app]]))))

(defn muutokset-paatason-valilehti [ur]
  (fn [{:keys [tyyppi] :as ur}]
    [tuck/tuck tila/muutokset muutokset-alempi-valilehti*]))
