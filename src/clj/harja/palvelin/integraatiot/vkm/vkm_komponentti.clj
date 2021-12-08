(ns harja.palvelin.integraatiot.vkm.vkm-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.pvm :as pvm]
            [cheshire.core :as cheshire]
            [clojure.core :as core])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defprotocol Tieosoitemuunnos
  (muunna-osoitteet-verkolta-toiselle
    [this tieosoiteet paivan-verkolta paivan-verkolle]
    "Muuntaa annetut tieosoitteet päivän verkolta toiselle. Jokaisella tieosoitteella täytyy olla mäpissä :vkm-id avain
    kohdistamista varten."))

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
  (if (and (not virhe?)
           alkuosanosoite
           (or (nil? aosa) tieosoite))
    (core/let [uusi-osoite
               (assoc tieosoite
                 :tie (get alkuosanosoite "tie" tie)
                 :ajorata (get alkuosanosoite "ajorata" ajorata)
                 :aosa (get alkuosanosoite "osa" aosa)
                 :aet (get alkuosanosoite "etaisyys" aet))]
      (if loppuosanosoite
        (assoc uusi-osoite :losa (get loppuosanosoite "osa" losa)
                           :let (get loppuosanosoite "etaisyys" let))
        uusi-osoite))
    tieosoite))

(defn osoitteet-vkm-vastauksesta [tieosoitteet vastaus]
  (if vastaus
    (let [osoitteet-vastauksesta (cheshire/decode vastaus)
          vkm-osoitteet (get osoitteet-vastauksesta "tieosoitteet")]
      (mapv (fn [{:keys [vkm-id] :as tieosoite}]
              (let [alkuosanhakutunnus (alkuosan-vkm-tunniste vkm-id)
                    loppuosanhakutunnus (loppuosan-vkm-tunniste vkm-id)
                    alkuosanosoite (hae-vkm-osoite vkm-osoitteet alkuosanhakutunnus)
                    loppuosanosoite (hae-vkm-osoite vkm-osoitteet loppuosanhakutunnus)
                    virhe? (or (vkm-virhe? alkuosanhakutunnus vkm-osoitteet)
                               (vkm-virhe? loppuosanhakutunnus vkm-osoitteet))]
                (paivita-osoite tieosoite alkuosanosoite loppuosanosoite virhe?)))
            tieosoitteet))
    tieosoitteet))

(defn pura-tieosoitteet [tieosoitteet]
  (reduce into []
          (map (fn [{:keys [tie aosa aet losa let ajr vkm-id]}]
                 (core/let [osoitteet [{:tunniste (alkuosan-vkm-tunniste vkm-id)
                                        :tie tie
                                        :osa aosa
                                        :ajorata ajr
                                        :etaisyys aet}]]
                   (if (and losa let)
                     (conj osoitteet {:tunniste (loppuosan-vkm-tunniste vkm-id)
                                      :tie tie
                                      :osa losa
                                      :ajorata ajr
                                      :etaisyys let})
                     osoitteet)))
               tieosoitteet)))

(defn vkm-parametrit [tieosoitteet paivan-verkolta paivan-verkolle]
  {:in "tieosoite"
   :out "tieosoite"
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
