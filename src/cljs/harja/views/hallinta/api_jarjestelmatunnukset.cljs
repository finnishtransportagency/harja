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
      {:otsikko "Organisaatio"
       :nimi :organisaatio
       :fmt :nimi
       :tyyppi :valinta
       :valinnat (sort-by :nimi (tiedot/organisaatiovalinnat))
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
    (tiedot/hae-jarjestelmatunnuksen-lisaoikeudet kayttaja-id tunnuksen-oikeudet)
    (fn []
      [grid/grid
       {:otsikko "Lisäoikeudet urakoihin"
        :tunniste :urakka-id
        :tyhja "Ei lisäoikeuksia"
        :tallenna #(tiedot/tallenna-jarjestelmatunnuksen-lisaoikeudet % kayttaja-id tunnuksen-oikeudet)}
       [{:otsikko "Urakka"
         :nimi :urakka-id
         :fmt #(:nimi (first (filter
                               (fn [urakka] (= (:id urakka) %))
                               @tiedot/urakkavalinnat)))
         :tyyppi :valinta
         :valinta-arvo :id
         :valinnat @tiedot/urakkavalinnat
         :valinta-nayta #(or (:nimi %) "- Valitse urakka -")
         :leveys 5}
        {:otsikko "Oikeus"
         :nimi :kuvaus
         :hae (fn [] "Täydet oikeudet")
         :tyyppi :string
         :muokattava? (constantly false)
         :leveys 15}]
       @tunnuksen-oikeudet])))

(defn- jarjestelmatunnuksien-lisaoikeudet [jarjestelmatunnukset-atom]
  [grid/grid
   {:otsikko "API-järjestelmätunnusten lisäoikeudet urakoihin"
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
      (let [nakyma-alustettu? (some? @tiedot/urakkavalinnat)]
        (if nakyma-alustettu?
          [:div
           [api-jarjestelmatunnukset tiedot/jarjestelmatunnukset]
           [jarjestelmatunnuksien-lisaoikeudet tiedot/jarjestelmatunnukset]]
          [ajax-loader "Ladataan..."])))))
