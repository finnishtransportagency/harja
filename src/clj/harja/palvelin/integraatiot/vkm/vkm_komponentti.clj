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

(defn vkm-virhe? [hakutunnus vkm-kohteet]
  (some #(and (= hakutunnus (get % "tunniste"))
           (some? (get % "virheet")))
        vkm-kohteet))

(defn hae-vkm-osoite [vkm-kohteet hakutunnus]
  (first (filter #(= hakutunnus (get % "tunniste")) vkm-kohteet)))

(defn paivita-osoite [{:keys [tie aosa aet losa let ajr] :as tieosoite} osoite virhe?]
  (if (and (not virhe?)
           osoite)
    (assoc tieosoite
      :tie (get osoite "tie" tie)
      :ajr (get osoite "ajovorata" ajr)
      :aosa (get osoite "osa" aosa)
      :aet (get osoite "etaisyys" aet)
      :losa (get osoite "osa_loppu" losa)
      :let (get osoite "etaisyys_loppu" let))))

(defn osoitteet-vkm-vastauksesta [tieosoitteet vastaus]
  (if vastaus
    (let [osoitteet-vastauksesta (cheshire/decode vastaus)
          vkm-osoitteet (mapv #(get-in osoitteet-vastauksesta ["features" % "properties"]) (range (count tieosoitteet)))]
      (mapv (fn [{:keys [tunniste] :as tieosoite}]
              (let [osoite (hae-vkm-osoite vkm-osoitteet tunniste)
                    virhe? (or (vkm-virhe? osoite vkm-osoitteet))]
                (paivita-osoite tieosoite osoite virhe?)))
            tieosoitteet))
    tieosoitteet))

(defn pura-tieosoitteet [tieosoitteet paivan-verkolta paivan-verkolle]
  (map (fn [{:keys [tie aosa aet losa let ajr tunniste]}]
         {:tunniste tunniste
          :tie tie
          :osa aosa
          :ajorata ajr
          :etaisyys aet
          :osa_loppu losa
          :etaisyys_loppu let
          :tilannepvm (pvm/pvm paivan-verkolta)
          :kohdepvm (pvm/pvm paivan-verkolle)})
    tieosoitteet))

(defn vkm-parametrit [tieosoitteet paivan-verkolta paivan-verkolle]
  {:json (cheshire/encode (pura-tieosoitteet tieosoitteet paivan-verkolta paivan-verkolle))})

(defn muunna-tieosoitteet-verkolta-toiselle [{:keys [db integraatioloki url]} tieosoitteet paivan-verkolta paivan-verkolle]
  (when url
    (log/debug (format "Muunnetaan tieosoitteet: %s päivän: %s verkolta päivän: %s verkolle"
                       tieosoitteet
                       paivan-verkolta
                       paivan-verkolle))
    (let [url (str url "muunna")]
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
          false)))))

(defrecord VKM [url]
  component/Lifecycle
  (start [this]
    (assoc this :url url))
  (stop [this]
    this)

  Tieosoitemuunnos
  (muunna-osoitteet-verkolta-toiselle [this tieosoitteet paivan-verkolta paivan-verkolle]
    (muunna-tieosoitteet-verkolta-toiselle this tieosoitteet paivan-verkolta paivan-verkolle)))
