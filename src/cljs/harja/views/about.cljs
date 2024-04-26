(ns harja.views.about
  "Tietoja Harjasta, Liikennevirastosta ja mm. avoimen lähdekoodin tai ikonien
  attribuutiot"
  (:require [harja.pvm :as pvm]
            [cljs-time.coerce :as tc]
            [harja.ui.grid :as grid])
  (:require-macros [harja.views.about :refer [lue-gitlog]]
                   [harja.tyokalut.versio :refer [sovelluksen-versio-sha sovelluksen-build-aika-iso8601]]))

(defn harja-info []
  [:p "Tämä on Väylän Harja-palvelu, joka mahdollistaa  mm. teiden hoito- ja ylläpitourakoiden
      kustannusten suunnittelua ja toteutumien seurantaa."])

(defn gitlog []
  (let [gitlog (lue-gitlog)]
    (when (seq gitlog)
      [:div {:style {:overflow "visible"}}
       [grid/grid
        {:otsikko "Viimeisimmät muutokset" :voi-muokata? false :tunniste :hash}

        [{:otsikko "Pvm ja aika" :nimi :date :hae #(tc/from-long (tc/to-long (:date %))) :fmt pvm/pvm-aika :tyyppi :string :leveys "15%"}
         {:otsikko "Tiiviste" :nimi :hash :tyyppi :string :leveys "15%"}
         {:otsikko "Tekijä" :nimi :author :tyyppi :string :leveys "20%"}
         {:otsikko "Otsikko ja viesti" :nimi :title :tyyppi :string :leveys "50%"
          :fmt identity :hae (fn [rivi]
                               [:span
                                [:h6 (:title rivi)]
                                (when-let [body (:body rivi)]
                                  [:pre body])])}]
        gitlog]])))


(defn versiotiedot []
  [:div
   [:h3 "Versiotiedot"]
   [:div [:b "Versio: "] (or (sovelluksen-versio-sha) "-")]
   [:div [:b "Version aikaleima: "] (or (sovelluksen-build-aika-iso8601) "-")]])

(defn about []
  [:div
   [:p
    [:h3 "Yleistä"]
    [harja-info]]
   [:p
    [versiotiedot]]
   [:p
    [gitlog]]
   [:p
    [:label "Harja käyttää avoimen lähdekoodin ohjelmistokomponentteja"]
    [:ul
     [:li [:a {:href "http://getbootstrap.com/"} "Bootstrap"]]
     [:li [:a {:href "http://openlayers.org/"} "OpenLayers"]]
     [:li [:a {:href "https://reagent-project.github.io/"} "Reagent"]]]]])
