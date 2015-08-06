(ns harja.tiedot.hallinta.integraatioloki
  "Hallinnoi integraatiolokin tietoja"
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [cljs-time.core :as time]
            [harja.atom :refer [paivita!]]
            [cljs-time.core :as t])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defn hae-jarjestelmien-integraatiot []
  (k/post! :hae-jarjestelmien-integraatiot nil))

(defn hae-integraation-tapahtumat [jarjestelma integraatio aikavali]
  (log "Aikavali: " aikavali)
  (k/post! :hae-integraatiotapahtumat
           (merge {:jarjestelma (:jarjestelma jarjestelma)
                   :integraatio integraatio}
                  (when aikavali
                    {:alkaen      (first aikavali)
                     ;; loppupvm halutaan seuraavan päivän 00:00:00 aikaan, jotta valitun loppupäivän tapahtumat näkyvät
                     :paattyen    (t/plus (second aikavali) (t/days 1))}))))

(defn hae-integraatiotapahtuman-viestit [tapahtuma-id]
  (k/post! :hae-integraatiotapahtuman-viestit tapahtuma-id))

(def nakymassa? (atom false))

(defonce jarjestelmien-integraatiot (reaction<! [nakymassa? @nakymassa?]
                                                (when nakymassa?
                                                  (hae-jarjestelmien-integraatiot))))

(defonce valittu-jarjestelma (atom nil))
(defonce valittu-integraatio (atom nil))
(defonce valittu-aikavali (atom nil))
(defonce valittu-tapahtuma (atom nil))

(defonce haetut-tapahtumat
  (reaction<! [valittu-jarjestelma @valittu-jarjestelma
               valittu-integraatio @valittu-integraatio
               valittu-aikavali @valittu-aikavali
               nakymassa? @nakymassa?]
              (when nakymassa?
                ;;(reset! valittu-tapahtuma nil)
                (hae-integraation-tapahtumat valittu-jarjestelma valittu-integraatio valittu-aikavali))))

(defn nayta-tapahtumat-eilisen-jalkeen []
  (reset! valittu-aikavali [(time/yesterday) (harja.pvm/nyt)]))

(defn nayta-uusimmat-tapahtumat []
  (reset! valittu-aikavali nil))

(defn paivita-tapahtumat! []
  (paivita! haetut-tapahtumat))
