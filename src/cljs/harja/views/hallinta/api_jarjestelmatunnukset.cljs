(ns harja.views.hallinta.api-jarjestelmatunnukset
  "Harja API:n järjestelmätunnuksien listaus ja muokkaus."
  (:require [harja.ui.grid :as grid]
            [harja.pvm :as pvm]
            [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.yleiset :refer [ajax-loader]]
            [clojure.string :as str]
            [harja.loki :refer [log]]
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

(defn api-jarjestelmatunnukset []

  (komp/luo
   (komp/lippu nakymassa?)
   (fn []
     (let [ei-muokattava (constantly false)]
       [grid/grid {:otsikko "API järjestelmätunnukset"
                   :tallenna tallenna
                   :tyhja (if (nil? @jarjestelmatunnukset)
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
        @jarjestelmatunnukset]))))
