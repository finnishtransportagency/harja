(ns harja.palvelin.integraatiot.integraatiopisteet.jms
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-epaonnistunut-lahetys [lokittaja tapahtuma-id virheviesti]
  (log/error virheviesti)
  (lokittaja :epaonnistunut virheviesti nil tapahtuma-id nil)
  (virheet/heita-sisainen-kasittelyvirhe-poikkeus
    {:koodi :sonja-lahetys-epaonnistui :viesti virheviesti}))

(defn kasittele-poikkeus [lokittaja tapahtuma-id poikkeus virheviesti]
  (log/error poikkeus virheviesti)
  (lokittaja :epaonnistunut virheviesti (str "Poikkeus: " (.getMessage poikkeus)) tapahtuma-id nil)
  (virheet/heita-sisainen-kasittelyvirhe-poikkeus
    {:koodi :sonja-lahetys-epaonnistui :viesti (format "Poikkeus: %s" poikkeus)}))

(defn laheta-jonoon
  ([lokittaja sonja jono viesti] (laheta-jonoon lokittaja sonja jono viesti nil))
  ([lokittaja sonja jono viesti viesti-id]
   (let [tapahtuma-id (lokittaja :alkanut-integraatio integraatioloki jarjestelma integraatio nil nil)
         virheviesti (format "Lähetys Sonjan JMS jonoon järjestelmän: %s integraatiolle: %s epäonnistu." jarjestelma integraatio)]
     (try
       (if-let [jms-viesti-id (sonja/laheta sonja jono viesti)]
         (lokittaja :jms-viesti tapahtuma-id (or viesti-id jms-viesti-id) "ulos" viesti)
         (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id virheviesti))
       (catch Exception poikkeus
         (kasittele-poikkeus lokittaja tapahtuma-id poikkeus virheviesti))))))

(defn jonolahettaja [lokittaja sonja jono]
  (fn [viesti viesti-id]
    (laheta-jonoon lokittaja sonja jono viesti viesti-id)))

(defn jonokuuntelija [lokittaja sonja jono  viestiparseri viesti->id kasittelija]
  )

