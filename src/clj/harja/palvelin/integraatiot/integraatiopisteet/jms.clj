(ns harja.palvelin.integraatiot.integraatiopisteet.jms
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn laheta-jonoon [integraatioloki sonja jono jarjestelma integraatio viesti]
  (println "-----------> YRITETÄÄN LÄHETTÄÄ!")
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki jarjestelma integraatio nil nil)
        virheviesti (format "Lähetys Sonjan JMS jonoon järjestelmän: %s integraatiolle: %s epäonnistu." jarjestelma integraatio)]
    (try
      (println "-----------> SONJA:" sonja)
      (if-let [viesti-id (sonja/laheta sonja jono viesti)]
        (integraatioloki/kirjaa-jms-viesti integraatioloki tapahtuma-id viesti-id "ulos" viesti)
        (do
          (log/error virheviesti jarjestelma integraatio)
          (integraatioloki/kirjaa-epaonnistunut-integraatio
            integraatioloki
            virheviesti
            nil
            tapahtuma-id
            nil)
          (virheet/heita-sisainen-kasittelyvirhe-poikkeus
            {:koodi :sonja-lahetys-epaonnistui :viesti ""})))
      (catch Exception e
        (log/error e virheviesti)
        (integraatioloki/kirjaa-epaonnistunut-integraatio
          integraatioloki
          virheviesti
          (str "Poikkeus: " (.getMessage e))
          tapahtuma-id
          nil)
        (virheet/heita-sisainen-kasittelyvirhe-poikkeus
          {:koodi :sonja-lahetys-epaonnistui :viesti (format "Poikkeus: %s" e)})))))
