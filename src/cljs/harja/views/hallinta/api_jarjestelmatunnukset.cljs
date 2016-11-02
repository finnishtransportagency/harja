(ns harja.views.hallinta.api-jarjestelmatunnukset
  "Harja API:n järjestelmätunnuksien listaus ja muokkaus."
  (:require [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.hallinta.api-jarjestelmatunnukset :as tiedot]
            [harja.ui.yleiset :refer [ajax-loader]]
            [clojure.string :as str]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- api-jarjestelmatunnukset [jarjestelmatunnukset-atom]
  (let [ei-muokattava (constantly false)]
    [grid/grid {:otsikko "API järjestelmätunnukset"
                :tallenna tiedot/tallenna-jarjestelmatunnukset
                :tyhja (if (nil? @jarjestelmatunnukset-atom)
                         [ajax-loader "Haetaan järjestelmätunnuksia..."]
                         "Järjestelmätunnuksia ei löytynyt")}
     [{:otsikko "Käyttäjänimi"
       :nimi :kayttajanimi
       :tyyppi :string
       :leveys 5}
      {:otsikko "Urakoitsija"
       :nimi :organisaatio
       :fmt :nimi
       :tyyppi :valinta
       :valinnat (tiedot/urakoitsijavalinnat)
       :valinta-nayta :nimi
       :leveys 5}
      {:otsikko "Käynnissä olevat urakat"
       :nimi :urakat
       :fmt #(str/join ", " %)
       :muokattava? ei-muokattava
       :leveys 15}
      {:otsikko "Luotu"
       :nimi :luotu
       :tyyppi :pvm
       :fmt pvm/pvm-aika-opt
       :muokattava? ei-muokattava
       :leveys 5}
      {:otsikko "Kuvaus"
       :nimi :kuvaus :tyyppi :string
       :leveys 5}]
     @jarjestelmatunnukset-atom]))

(defn jarjestelmatunnuksen-lisaoikeudet [kayttaja-id]
  (let [tunnuksen-oikeudet (atom nil)]
    (fn []
      [grid/grid
       {:otsikko "Lisäoikeudet urakoihin"
        :tunniste :id
        :tallenna nil}
       [{:otsikko "Urakka"
         :nimi :urakka
         :muokattava (constantly false)
         :tyyppi :string
         :leveys 5}
        {:otsikko "Kuvaus"
         :nimi :kuvaus
         :hae (fn [] "Täydet oikeudet")
         :tyyppi :string
         :muokattava (constantly false)
         :leveys 15}]
       @tunnuksen-oikeudet])))

(defn- jarjestelmatunnuksien-lisaoikeudet [jarjestelmatunnukset-atom]
  [grid/grid
   {:otsikko "API-järjestelmätunnusten lisäoikeudet "
    :tunniste :id
    :tallenna nil
    :vetolaatikot (into {} (map (juxt :id #(-> [jarjestelmatunnuksen-lisaoikeudet (:id %)]))
                                @jarjestelmatunnukset-atom))}
   [{:tyyppi :vetolaatikon-tila :leveys 1}
    {:otsikko "Käyttäjänimi"
     :nimi :kayttajanimi
     :muokattava (constantly false)
     :tyyppi :string
     :leveys 15}
    {:otsikko "Urakoitsija"
     :nimi :organisaatio
     :fmt :nimi
     :tyyppi :string
     :muokattava (constantly false)
     :leveys 30}]
   @jarjestelmatunnukset-atom])

(defn api-jarjestelmatunnukset-paakomponentti []

  (komp/luo
    (komp/lippu tiedot/nakymassa?)
    (fn []
      [:div
       [api-jarjestelmatunnukset tiedot/jarjestelmatunnukset]
       [jarjestelmatunnuksien-lisaoikeudet tiedot/jarjestelmatunnukset]])))
