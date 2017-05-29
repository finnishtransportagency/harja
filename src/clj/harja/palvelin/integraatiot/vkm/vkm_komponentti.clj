(ns harja.palvelin.integraatiot.vkm.vkm-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.pvm :as pvm]
            [cheshire.core :as cheshire])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:import (java.net URLEncoder)))
;; curl -X GET "https://testioag.liikennevirasto.fi/vkm/muunnos?in=tieosoite&out=tieosoite&callback=jsonp&tilannepvm=1.1.2017&kohdepvm=1.3.2017&json=%7B%22tieosoitteet%22%3A%0A%5B%7B%22tunniste%22%3A%22666%22%2C%22tie%22%3A4%2C%22osa%22%3A101%2C%22etaisyys%22%3A100%2C%22ajorata%22%3A1%7D%0A%5D%0A%7D"
;; curl -X GET "https://harja-test.solitaservices.fi/harja/integraatiotesti/vkm/muunnos?in=tieosoite&out=tieosoite&callback=jsonp&tilannepvm=1.1.2017&kohdepvm=1.3.2017&json=%7B%22tieosoitteet%22%3A%0A%5B%7B%22tunniste%22%3A%22666%22%2C%22tie%22%3A4%2C%22osa%22%3A101%2C%22etaisyys%22%3A100%2C%22ajorata%22%3A1%7D%0A%5D%0A%7D"
;; curl -X GET "https://harja-test.solitaservices.fi/harja/integraatiotesti/vkm/muunnos?in=tieosoite&out=tieosoite&callback=jsonp&tilannepvm=1.1.2017&kohdepvm=1.3.2017&json=%7B%22tieosoitteet%22%3A%5B%7B%22tunniste%22%3A%22666-alku%22%2C%22tie%22%3A4%2C%22osa%22%3A1%2C%22ajorata%22%3A1%2C%22etaisyys%22%3A0%7D%2C%7B%22tunniste%22%3A%22666-loppu%22%2C%22tie%22%3A4%2C%22osa%22%3A3%2C%22ajorata%22%3A1%2C%22etaisyys%22%3A1000%7D%5D%7D"

(defprotocol Tieosoitemuunnos
  (muunna-osoitteet-verkolta-toiselle [this tieosoiteet paivan-verkolta paivan-verkolle]))

(defn alkuosan-vkm-tunniste [tunniste]
  (str tunniste "-alku"))

(defn loppuosan-vkm-tunniste [tunniste]
  (str tunniste "-loppu"))

(defn vkm-virhe? [hakutunnus vkm-kohteet]
  (some #(and (= hakutunnus (get % "tunniste"))
              (not (= 1 (get % "palautusarvo"))))
        vkm-kohteet))

(defn hae-vkm-osoite [vkm-kohteet hakutunnus]
  (first (filter #(= hakutunnus (get % "tunniste")) vkm-kohteet)))

(defn paivita-osoite [{:keys [tie aosa aet losa let ajorata] :as tieosoite} alkuosanosoite loppuosanosoite virhe?]
  (if (or virhe? (not alkuosanosoite) (not loppuosanosoite))
    tieosoite
    (-> tieosoite
        (assoc :tie (get alkuosanosoite "tie" tie))
        (assoc :ajorata (get alkuosanosoite "ajorata" ajorata))
        (assoc :aosa (get alkuosanosoite "osa" aosa))
        (assoc :aet (get alkuosanosoite "etaisyys" aet))
        (assoc :losa (get loppuosanosoite "osa" losa))
        (assoc :let (get loppuosanosoite "etaisyys" let)))))

(defn osoitteet-vkm-vastauksesta [tieosoitteet vastaus]
  (if (and vastaus (.contains vastaus "json("))
    (let [osoitteet-vastauksesta (cheshire/decode (apply str (drop-last (.replace vastaus "json(" ""))))
         vkm-osoitteet (get osoitteet-vastauksesta "tieosoitteet")]
     (mapv (fn [{:keys [id] :as tieosoite}]
             (let [alkuosanhakutunnus (alkuosan-vkm-tunniste id)
                   loppuosanhakutunnus (loppuosan-vkm-tunniste id)
                   alkuosanosoite (hae-vkm-osoite vkm-osoitteet alkuosanhakutunnus)
                   loppuosanosoite (hae-vkm-osoite vkm-osoitteet loppuosanhakutunnus)
                   virhe? (or (vkm-virhe? alkuosanhakutunnus vkm-osoitteet)
                              (vkm-virhe? loppuosanhakutunnus vkm-osoitteet))]
               (paivita-osoite tieosoite alkuosanosoite loppuosanosoite virhe?)))
           tieosoitteet))
    tieosoitteet))

(defn pura-tieosoitteet [tieosoitteet]
  (reduce into []
          (map (fn [{:keys [tie aosa aet losa let ajr id]}]
                 [{:tunniste (alkuosan-vkm-tunniste id) :tie tie :osa aosa :ajorata ajr :etaisyys aet}
                  {:tunniste (loppuosan-vkm-tunniste id) :tie tie :osa losa :ajorata ajr :etaisyys let}])
               tieosoitteet)))

(defn vkm-parametrit [tieosoitteet paivan-verkolta paivan-verkolle]
  {:in "tieosoite"
   :out "tieosoite"
   :callback "json"
   :tilannepvm (pvm/pvm paivan-verkolta)
   :kohdepvm (pvm/pvm paivan-verkolle)
   :json (cheshire/encode {:tieosoitteet (pura-tieosoitteet tieosoitteet)})})

(defn muunna-tieosoitteet-verkolta-toiselle [{:keys [db integraatioloki url]} tieosoitteet paivan-verkolta paivan-verkolle]
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
                http-asetukset {:metodi :POST
                                :url url
                                :parametrit parametrit}
                {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
            (osoitteet-vkm-vastauksesta tieosoitteet vastaus))))
      (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
        false))))

(defrecord VKM [url]
  component/Lifecycle
  (start [this]
    (assoc this :url url))
  (stop [this]
    this)

  Tieosoitemuunnos
  (muunna-osoitteet-verkolta-toiselle [this tieosoitteet paivan-verkolta paivan-verkolle]
    (muunna-tieosoitteet-verkolta-toiselle this tieosoitteet paivan-verkolta paivan-verkolle)))
