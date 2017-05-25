(ns harja.palvelin.integraatiot.vkm.vkm-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.core.async :as async]
            [harja.pvm :as pvm]
            [cheshire.core :as cheshire])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:import (java.net URLEncoder)))
;; curl -X GET "https://testioag.liikennevirasto.fi/vkm/muunnos?in=tieosoite&out=tieosoite&callback=jsonp&tilannepvm=1.1.2017&kohdepvm=1.3.2017&json=%7B%22tieosoitteet%22%3A%0A%5B%7B%22tunniste%22%3A%22666%22%2C%22tie%22%3A4%2C%22osa%22%3A101%2C%22etaisyys%22%3A100%2C%22ajorata%22%3A1%7D%0A%5D%0A%7D"

(defprotocol Tieosoitemuunnos
  (muunna-osoite-verkolta-toiselle [this tieosoite paivan-verkolta paivan-verkolle kun-valmis]))

(defn tierekisteriosoite-vkm-vastauksesta [tieosoitteet vastaus]
  )

(defn pura-tieosoitteet [tieosoitteet]
  (reduce into []
          (map (fn [{:keys [tie aosa aet losa let ajorata tunniste]}]
                 [{:tunniste (str tunniste "-alku") :tie tie :osa aosa :ajorata ajorata :etaisyys aet}
                  {:tunniste (str tunniste "-loppu") :tie tie :osa losa :ajorata ajorata :etaisyys let}])
               tieosoitteet)))

(defn vkm-parametrit [tieosoitteet paivan-verkolta paivan-verkolle]
  {:in "tieosoite"
   :out "tieosoite"
   :callback "jsonp"
   :tilannepvm (pvm/pvm paivan-verkolta)
   :kohdepvm (pvm/pvm paivan-verkolle)
   :json (URLEncoder/encode (cheshire/encode {:tieosoitteet (pura-tieosoitteet tieosoitteet)}))})

(defn muunna-tieosoitteet-verkolta-toiselle [{:keys [db integraatioloki url]} tieosoitteet paivan-verkolta paivan-verkolle kun-valmis]
  (when url
    (log/debug (format "Muunnetaan tieosoitteet: %s päivän: %s verkolta päivän: %s verkolle"
                       tieosoitteet
                       paivan-verkolta
                       paivan-verkolle))
    (try+
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "vkm" "osoitemuunnos" nil
        (fn [konteksti]
          (let [parametrit (vkm-parametrit tieosoitteet paivan-verkolta paivan-verkolle)
                http-asetukset {:metodi :GET
                                :url url
                                :parametrit parametrit}
                {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
            (tierekisteriosoite-vkm-vastauksesta tieosoitteet vastaus))))
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        false))))

(defrecord VKM [asetukset]
  component/Lifecycle
  (start [this]
    (assoc this :url (:url asetukset)))
  (stop [this]
    this)

  Tieosoitemuunnos
  (muunna-osoite-verkolta-toiselle [this tieosoite paivan-verkolta paivan-verkolle kun-valmis]
    (muunna-tieosoitteet-verkolta-toiselle this tieosoite paivan-verkolta paivan-verkolle kun-valmis)))
