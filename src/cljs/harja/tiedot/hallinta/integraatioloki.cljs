(ns harja.tiedot.hallinta.integraatioloki
  "Hallinnoi integraatiolokin tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn hae-jarjestelmien-integraatiot []
  (k/post! :hae-jarjestelmien-integraatiot nil))

(defn hae-integraation-tapahtumat [jarjestelma integraatio aikavali]
  (log "Aikavali: " aikavali)
  (k/post! :hae-integraatiotapahtumat {:jarjestelma (:jarjestelma jarjestelma)
                                       :integraatio integraatio
                                       :alkaen      (first aikavali)
                                       :paattyen    (second aikavali)}))

(def nakymassa? (atom false))

(defonce jarjestelmien-integraatiot (reaction<! [nakymassa?]
                                                (when nakymassa?
                                                  (hae-jarjestelmien-integraatiot))))

(defonce valittu-jarjestelma (reaction (first @jarjestelmien-integraatiot)))
(defonce valittu-integraatio (reaction (first (:integraatiot @valittu-jarjestelma))))
(defonce valittu-aikavali (atom [(harja.pvm/nyt) (harja.pvm/nyt)]))
(defonce valittu-tapahtuma (atom nil))

(defonce haetut-tapahtumat (reaction<! [valittu-jarjestelma @valittu-jarjestelma
                                        valittu-integraatio @valittu-integraatio
                                        valittu-aikavali @valittu-aikavali
                                        nakymassa?]
                                       (when nakymassa?
                                         (hae-integraation-tapahtumat valittu-jarjestelma valittu-integraatio valittu-aikavali))))

#_(defonce haetut-tapahtumat (atom [{:jarjestelma "API"
                                   :integraatio "hae-urakka"
                                   :alkanut     "1.1.2015"
                                   :paattynyt   "1.1.2015"
                                   :onnistunut  true
                                   :ulkoinenid  "asdfasfd"
                                   :lisatietoja "makiaista"
                                   :viestit     [{:suunta "ulos"}
                                                 {:suunta "sisään"}]}
                                  {:jarjestelma "API"
                                   :alkanut     "1.1.2015"
                                   :paattynyt   "1.1.2015"
                                   :onnistunut  true
                                   :ulkoinenid  "asdfasfd"
                                   :lisatietoja "makiaista"
                                   :integraatio "kirjaa-havainto"}]))



