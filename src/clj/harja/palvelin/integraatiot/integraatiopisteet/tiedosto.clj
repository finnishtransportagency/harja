(ns harja.palvelin.integraatiot.integraatiopisteet.tiedosto
  (:require [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [clojure.java.io :as io])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn lataa-tiedosto [integraatioloki jarjestelma integraatio lahde kohde]
  (log/debug "Ladataan tiedosto lähteestä: " lahde " kohteeseen: " kohde)
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-tiedoston-haku integraatioloki jarjestelma integraatio lahde)]
    (try
      (with-open [in (io/input-stream lahde)
                  out (io/output-stream kohde)]
        (io/copy in out))
      (log/debug "Tiedosto ladattu onnistuneesti lähteestä: " lahde " kohteeseen: " kohde)
      (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki
                                                     nil
                                                     (str "Tiedosto siirretty onnistuneesti kohteeseen: " kohde)
                                                     tapahtuma-id
                                                     nil)
      (catch Exception e
        (let [viesti (str "Tiedoston latauksessa lähteestä: " lahde " kohteeseen: " kohde " tapahtui poikkeus: " e)]
          (log/error viesti)
          (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki nil viesti tapahtuma-id nil)
          (throw+
            {:type    :tiedoston-lataus-epaonnistui
             :virheet [{:koodi :poikkeus :viesti (str "Poikkeus :" (.toString e))}]}))))))

