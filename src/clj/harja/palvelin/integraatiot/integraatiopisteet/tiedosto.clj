(ns harja.palvelin.integraatiot.integraatiopisteet.tiedosto
  (:require [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn lataa-tiedosto
  ([integraatioloki db jarjestelma integraatio url kohde]
   (lataa-tiedosto integraatioloki db jarjestelma integraatio url kohde nil nil))
  ([integraatioloki db jarjestelma integraatio url kohde kayttajatunnus salasana]
   (integraatiotapahtuma/suorita-integraatio
     db integraatioloki jarjestelma integraatio nil
     (fn [konteksti]
       (let [http-asetukset (cond-> {:metodi :GET
                                     :url url}
                                    (not (nil? kayttajatunnus)) (assoc :kayttajatunnus kayttajatunnus)
                                    (not (nil? salasana)) (assoc :salasana salasana))
             {body :body} (integraatiotapahtuma/laheta
                            konteksti
                            :http
                            http-asetukset)]
         (with-open [in (io/input-stream body)
                     out (io/output-stream kohde)]
           (io/copy in out))))
     {:virhekasittelija (fn [_ _]
                          (log/error (format "Tiedoston lataaminen osoitteesta: %s epäonnistui." url)))})))
