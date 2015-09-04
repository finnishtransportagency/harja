(ns harja.palvelin.integraatiot.tierekisteri.tyokalut.kutsukasittely
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kutsu-palvelua [integraatioloki integraatio url kutsudata]
  (log/debug "Kutsutaan Tierekisterin palvelua osoitteessa:" url ", datalla: " kutsudata)

  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "tierekisteri" integraatio nil nil)
        kutsuosoite (str url "/haetietolajit")
        kutsu {:body    kutsudata
               :headers {"Content-Type" "text/xml"}}]
    (try
      (integraatioloki/kirjaa-rest-xml-viesti integraatioloki tapahtuma-id "ulos" url kutsudata (:headers kutsu) nil)
      (let [{:keys [status body error headers]} @(http/post kutsuosoite kutsu)
            lokiviesti (integraatioloki/tee-rest-xml-lokiviesti "sisään" url body headers nil)]
        (log/debug "Tierekisterin palvelu palautti")
        (log/debug "Status: " status)
        (log/debug "Headers: " headers)
        (log/debug "Body: " body)

        (if error
          (do
            (log/error "Kutsu Tierekisterin palveluun: " url " epäonnistui virhe:" error)
            (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti (str "Virhe:" error) tapahtuma-id nil)
            (throw+ {:type :tierekisteri-kutsu-epaonnistui :error error}))
          (do
            (log/debug "Kutsu Tierekisterin palveluun: " url " onnistui.")
            (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti nil tapahtuma-id nil)
            body)))

      (catch Exception e
        (log/error "Tapahtui poikkeus: " e)
        (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki nil (str "Tapahtui poikkeus: " e) tapahtuma-id nil)
        (throw+ {:type :tierekisteri-kutsu-epaonnistui :error e})))))