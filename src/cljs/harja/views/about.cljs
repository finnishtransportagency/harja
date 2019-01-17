(ns harja.views.about
  "Tietoja Harjasta, Liikennevirastosta ja mm. avoimen lähdekoodin tai ikonien
  attribuutiot"
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.pvm :as pvm]
            [cljs-time.coerce :as tc]
            [harja.ui.grid :as grid]
            [harja.ui.kartta.varit.puhtaat :as puhtaat])
  (:require-macros [harja.views.about :refer [lue-gitlog]]))

(defn harja-info []
  [:p "Tämä on Väylän Harja-palvelu, joka mahdollistaa alkuvaiheessa mm. teiden hoito- ja ylläpitourakoiden
      kustannusten suunnittelua ja toteutumien seurantaa."])

(defn gitlog []
  [:span {:style {:overflow "visible"}}
   [grid/grid
   {:otsikko "Viimeisimmät muutokset" :voi-muokata? false :tunniste :hash}

   [{:otsikko "Pvm ja aika" :nimi :date :hae #(tc/from-long (tc/to-long (:date %))) :fmt pvm/pvm-aika :tyyppi :string :leveys "15%"}
    {:otsikko "Tiiviste" :nimi :hash  :tyyppi :string :leveys "15%"}
    {:otsikko "Tekijä" :nimi :author :tyyppi :string :leveys "20%"}
    {:otsikko "Otsikko ja viesti" :nimi :title :tyyppi :string :leveys "50%"
     :fmt identity :hae (fn [rivi]
                          [:span
                           [:h6 (:title rivi)]
                           (when-let [body (:body rivi)]
                             [:pre body])])
     }
    ]
 
   (lue-gitlog)]])

(defn ikonit
  "Näyttää Harjan ikonit. Tässä oleva versio ei välttämättä ole up-to-date."
  []
  [:div.harjan-ikonit
   [:div.livicon-arrow-beginning " livicon-arrow-beginning"]
   [:div.livicon-arrow-bottom " livicon-arrow-bottom"]
   [:div.livicon-arrow-down " livicon-arrow-down"]
   [:div.livicon-arrow-end " livicon-arrow-end"]
   [:div.livicon-arrow-left-down " livicon-arrow-left-down"]
   [:div.livicon-arrow-left " livicon-arrow-left"]
   [:div.livicon-arrow-right " livicon-arrow-right"]
   [:div.livicon-arrow-start " livicon-arrow-start"]
   [:div.livicon-arrow-top " livicon-arrow-top"]
   [:div.livicon-arrow-up " livicon-arrow-up"]
   [:div.livicon-arrows-height " livicon-arrows-height"]
   [:div.livicon-arrows-width " livicon-arrows-width"]
   [:div.livicon-ban " livicon-ban"]
   [:div.livicon-calendar " livicon-calendar"]
   [:div.livicon-check " livicon-check"]
   [:div.livicon-chevron-beginning " livicon-chevron-beginning"]
   [:div.livicon-chevron-bottom " livicon-chevron-bottom"]
   [:div.livicon-chevron-down " livicon-chevron-down"]
   [:div.livicon-chevron-end " livicon-chevron-end"]
   [:div.livicon-chevron-left " livicon-chevron-left"]
   [:div.livicon-chevron-right " livicon-chevron-right"]
   [:div.livicon-chevron-top " livicon-chevron-top"]
   [:div.livicon-chevron-up " livicon-chevron-up"]
   [:div.livicon-circle-chosen " livicon-circle-chosen"]
   [:div.livicon-circle " livicon-circle"]
   [:div.livicon-clock " livicon-clock"]
   [:div.livicon-comment " livicon-comment"]
   [:div.livicon-compress " livicon-compress"]
   [:div.livicon-crosshairs " livicon-crosshairs"]
   [:div.livicon-database " livicon-database"]
   [:div.livicon-delete " livicon-delete"]
   [:div.livicon-document-empty " livicon-document-empty"]
   [:div.livicon-document-full " livicon-document-full"]
   [:div.livicon-download " livicon-download"]
   [:div.livicon-duplicate " livicon-duplicate"]
   [:div.livicon-exclamation-triangle " livicon-exclamation-triangle"]
   [:div.livicon-exclamation " livicon-exclamation"]
   [:div.livicon-expand " livicon-expand"]
   [:div.livicon-external " livicon-external"]
   [:div.livicon-eye " livicon-eye"]
   [:div.livicon-folder " livicon-folder"]
   [:div.livicon-gear " livicon-gear"]
   [:div.livicon-home " livicon-home"]
   [:div.livicon-info-circle " livicon-info-circle"]
   [:div.livicon-info " livicon-info"]
   [:div.livicon-location " livicon-location"]
   [:div.livicon-log-in " livicon-log-in"]
   [:div.livicon-log-out " livicon-log-out"]
   [:div.livicon-maintenance " livicon-maintenance"]
   [:div.livicon-menu " livicon-menu"]
   [:div.livicon-minus " livicon-minus"]
   [:div.livicon-pen " livicon-pen"]
   [:div.livicon-plus " livicon-plus"]
   [:div.livicon-print " livicon-print"]
   [:div.livicon-question-circle " livicon-question-circle"]
   [:div.livicon-question " livicon-question"]
   [:div.livicon-refresh " livicon-refresh"]
   [:div.livicon-rotate-left " livicon-rotate-left"]
   [:div.livicon-rotate-right " livicon-rotate-right"]
   [:div.livicon-save " livicon-save"]
   [:div.livicon-search " livicon-search"]
   [:div.livicon-signal " livicon-signal"]
   [:div.livicon-sound-minus " livicon-sound-minus"]
   [:div.livicon-sound-on " livicon-sound-on"]
   [:div.livicon-sound-plus " livicon-sound-plus"]
   [:div.livicon-souns-off " livicon-souns-off"]
   [:div.livicon-square-chosen " livicon-square-chosen"]
   [:div.livicon-square " livicon-square"]
   [:div.livicon-star " livicon-star"]
   [:div.livicon-target " livicon-target"]
   [:div.livicon-track-side " livicon-track-side"]
   [:div.livicon-track " livicon-track"]
   [:div.livicon-upload " livicon-upload"]
   [:div.livicon-user " livicon-user"]
   [:div.livicon-wifi " livicon-wifi"]
   [:div.livicon-wrench " livicon-wrench"]])

(defn varit [varit]
  [:div
   (for [v varit]
     ^{:key (str "varilaatikko_" v)}
     [:div.kartan-ikoni-vari {:style {:background-color v
                                      :width            "20px"
                                      :height           "20px"
                                      :display "inline-block"}}])])

(defn varilista [& varit-ja-nimet]
  [:div
   (for [[nimi vari] (partition 2 varit-ja-nimet)]
     ^{:key (str "varilista_" nimi)}
     [:div.kartan-ikoni-vari {:style {:background-color vari
                                      :width            "20px"
                                      :height           "20px"
                                      :margin-left 0}}
      [:span
       {:style {:left "20px"
                :position "relative"}}
       nimi]])])

(defn about []
  [:span
   [:div.section [:label "Yleistä"]
      [harja-info]]
   [:div.section
    [:label "Harja käyttää avoimen lähdekoodin ohjelmistokomponentteja"]
    [:ul
     [:li [:a {:href "http://getbootstrap.com/"} "Bootstrap"]]
     [:li [:a {:href "http://openlayers.org/"} "OpenLayers"]]
     [:li [:a {:href "https://reagent-project.github.io/"} "Reagent"]]]]
   [:div.section [:label "Harjan ikonit"]
    [ikonit]]
   [:div.section [:label "Harjan värit (puhtaat)"]
    [varit puhtaat/kaikki]
    [varilista "punainen" puhtaat/punainen "oranssi" puhtaat/oranssi "keltainen" puhtaat/keltainen
     "lime" puhtaat/lime "vihrea" puhtaat/vihrea "turkoosi" puhtaat/turkoosi
     "syaani" puhtaat/syaani "sininen" puhtaat/sininen "tummansininen" puhtaat/tummansininen
     "violetti" puhtaat/violetti "magenta" puhtaat/magenta "pinkki" puhtaat/pinkki
     "musta" puhtaat/musta "vaaleanharmaa" puhtaat/vaaleanharmaa "harmaa" puhtaat/harmaa
     "tummanharmaa" puhtaat/tummanharmaa]]
   [gitlog]])
