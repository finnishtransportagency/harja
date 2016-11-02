(ns harja.views.hallinta.api-jarjestelmatunnukset
  "Harja API:n järjestelmätunnuksien listaus ja muokkaus."
  (:require [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.yleiset :refer [ajax-loader]]
            [clojure.string :as str]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakoitsijat :refer [urakoitsijat]]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))

(defonce jarjestelmatunnukset
  (reaction<! [nakymassa? @nakymassa?]
              (when nakymassa?
                (k/post! :hae-jarjestelmatunnukset nil))))

(defn- urakoitsijavalinnat []
  (distinct (map #(select-keys % [:id :nimi]) @urakoitsijat)))

(defn- tallenna [muuttuneet-tunnukset]
  (go (let [uudet-tunnukset (<! (k/post! :tallenna-jarjestelmatunnukset
                                         muuttuneet-tunnukset))]
        (log "SAIN: " (pr-str uudet-tunnukset))
        (reset! jarjestelmatunnukset uudet-tunnukset))))

(defn- api-jarjestelmatunnukset [jarjestelmatunnukset-atom]
  (let [ei-muokattava (constantly false)]
    [grid/grid {:otsikko "API järjestelmätunnukset"
                :tallenna tallenna
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
       :valinnat (urakoitsijavalinnat)
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
    (komp/lippu nakymassa?)
    (fn []
      [:div
       [api-jarjestelmatunnukset jarjestelmatunnukset]
       [jarjestelmatunnuksien-lisaoikeudet jarjestelmatunnukset]])))
