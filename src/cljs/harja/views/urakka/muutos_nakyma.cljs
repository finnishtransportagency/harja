(ns harja.views.urakka.muutos-nakyma
  "MHU-urakoiden muutosten välilehti. Hallinnoi ja näyttää tarjouksen pohjatietoihin ja tavoitehintaan tehtäviä muutoksia."
  (:require [cljs.core.async :refer [<!]]
            [harja.fmt :as fmt]
            [harja.ui.ikonit :as ui-ikonit]
            [harja.ui.napit :as napit]
            [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [harja.domain.muutos-domain :as muutos-domain]
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

(defn muutoslomake [e! app]
  [:span.muutoslomake
   (if (get-in app [:muokattava-muutos :id])
     "Muokkaa muutosta"
     "Lisää uusi muutos")

   ;; todo: eri tyyppisten muutosten lomakkeiden toteutus tähän
   ;; oletettavasti kannattaa toteuttaa lomakkeen harja.ui.lomake avulla
   ;; siten että niiden sisälle sijoitetaan tarvittaessa taulukkoja :muokkaus-grid, ks. esim views/urakka/toteumat/muut_materiaalit.cljs#L127
   [:div (pr-str (:muokattava-muutos app))]
   [napit/peruuta
    #(e! (muutos-tiedot/->MuokkaaMuutosta nil))]])

(defn- kehystetty-avattava-grid
  "Piirtää yhtenäisesti Muutoksien taulukot collapsoitaviksi"
  [e! app {:keys [taulukon-avain taulukon-nakyvyys-event
                  otsikko summa toiminnot taulukko]}]
  (let [sisalto-nakyvissa? (get-in app [:taulukko-nakyvissa? taulukon-avain] app)]
    [:div.collapsoitava-osio
     [:div.otsikkorivi.klikattava {:on-click taulukon-nakyvyys-event}
      [:span
       [ui-ikonit/navigation-ympyrassa (if sisalto-nakyvissa?
                                         :down
                                         :right)]
       [:h2 otsikko]]
      [:div.summa (fmt/euro-opt summa)]]
     (when sisalto-nakyvissa?
       [:div.toiminnot [toiminnot e! app]
        [:div.taulukko [taulukko e! app]]])]))

(defn- kirjatut-muutokset [e! {:keys [muutokset] :as app}]
  [kehystetty-avattava-grid e! app
   {:taulukon-avain :kirjatut-muutokset
    :taulukon-nakyvyys-event #(e! (muutos-tiedot/->ToggleTaulukonNakyvyys :kirjatut-muutokset))
    :otsikko "Kirjatut muutokset"
    :summa (reduce + 0 (map :tavoitehinnan-muutos muutokset))
    :toiminnot (fn [e! app]
                 [napit/uusi "Lisää uusi" #(e! (muutos-tiedot/->MuokkaaMuutosta {}))])
    :taulukko
    (fn [e! app]
      [grid/grid
       {:tunniste :id
        :luokat ["kirjatut-muutokset-grid"]
        :tyhja "Ei kirjattuja muutoksia."
        :voi-lisata? false :voi-kumota? false
        :voi-poistaa? (constantly false) :voi-muokata? false}

       ;; taulukon kentät
       [{:otsikko "Tyyppi" :nimi :tyyppi :tyyppi :string :leveys 15
         :fmt muutos-domain/tyyppi-fmt}
        {:otsikko "Muutoksen syy" :nimi :syy :tyyppi :string :leveys 35}
        {:otsikko "Voimassa alkaen" :nimi :voimassa_alkaen :tyyppi :pvm :leveys 15}
        {:otsikko "Tavoitehinnan muutos" :nimi :tavoitehinnan-muutos :tyyppi :numero
         :fmt fmt/euro-opt :tasaa :oikea :leveys 15}
        {:otsikko "" :nimi :toiminnot :tyyppi :komponentti :leveys 10 :tasaa :oikea
         :komponentti (fn [rivi]
                        [napit/muokkaa "Muokkaa"
                         #(e! (muutos-tiedot/->MuokkaaMuutosta rivi))])}]
       muutokset])}])

(defn muutoslistaus [e! app]
  [:span.muutoslistaus
   ;; Kirjatut muutokset
   [kirjatut-muutokset e! app]])

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

         (if (:muokattava-muutos app)
           [muutoslomake e! app]
           [muutoslistaus e! app])

         [debug app]]))))

(defn muutokset-paatason-valilehti [ur]
  (fn [ur]
    [tuck/tuck tila/muutokset muutokset-alempi-valilehti*]))
