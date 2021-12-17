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

(defn vkm-virhe [vkm-kohteet tunniste]
  (some #(and (= tunniste (get % "tunniste"))
           (get % "virheet"))
        vkm-kohteet))

(defn hae-vkm-osoite [vkm-kohteet hakutunnus]
  (first (filter #(= hakutunnus (get % "tunniste")) vkm-kohteet)))

(defn paivita-osoite [{:keys [tie aosa aet losa let ajorata] :as tieosoite} osoite virhe]
  (println "jere testaa:: paivita-osoite \n"
    tieosoite
    "\n -> \n"
    osoite
    "\n virhe on"
    virhe
    )
  (if osoite
    (merge
      (assoc tieosoite
        :tie (get osoite "tie" tie)
        :ajorata (get osoite "ajorata" ajorata)
        :aosa (get osoite "osa" aosa)
        :aet (get osoite "etaisyys" aet)
        :losa (get osoite "osa_loppu" losa)
        :let (get osoite "etaisyys_loppu" let))
      (when virhe
        {:virhe virhe}))
    tieosoite))

(defn- yhdista-vkm-osoitteet
  "VKM-vastauksesta saattaa tulla useampi ajorata per kohde, yhdistetään ne."
  [vkm-osoitteet]
  (mapv (fn [[tunniste kohteet]]
          (if (= (count kohteet) 1)
            (first kohteet)
            (let [ensimmainen-kohde (first kohteet)]
              {"tunniste" tunniste
               "tie" (get ensimmainen-kohde "tie")
               "osa" (apply min (mapv #(get % "osa") kohteet))
               "etaisyys" (apply min (mapv #(get % "etaisyys") kohteet))})))
    (group-by #(get % "tunniste") vkm-osoitteet)))

;; TODO: VKM saattaa palauttaa useamman rivin per haettu osoite, yhdistä ne.
(defn osoitteet-vkm-vastauksesta [tieosoitteet vastaus]
  (if vastaus
    (let [osoitteet-vastauksesta (cheshire/decode vastaus)
          vkm-osoitteet (mapv #(get % "properties") (get osoitteet-vastauksesta "features"))
          #_ (println "jere testaa::\n" vkm-osoitteet)
          #_ (println "jere testaa:: numeroita \n"
              "count tieosoitteet " (count tieosoitteet)
              "\ncount vkm-osoitteet " (count vkm-osoitteet)
              "\ncount count vastaukset" (count (get osoitteet-vastauksesta "features"))
              )
          yhdistetyt-vkm-osoitteet (yhdista-vkm-osoitteet vkm-osoitteet)
          ]
      (mapv (fn [{:keys [tunniste] :as tieosoite}]
              (let [osoite (hae-vkm-osoite yhdistetyt-vkm-osoitteet tunniste)
                    _ (when (nil? osoite)
                        tunniste
                        )
                    virhe (vkm-virhe yhdistetyt-vkm-osoitteet tunniste)]
                (paivita-osoite tieosoite osoite virhe)))
            tieosoitteet))
    tieosoitteet))

(defn kohteen-tunnus [kohde teksti]
  (str "kohde-" (:yha-id kohde) (when teksti "-") teksti))

(defn alikohteen-tunnus [kohde alikohde teksti]
  (str "alikohde-" (:yha-id kohde) "-" (:yha-id alikohde) (when teksti "-") teksti))

(defn yllapitokohde->vkm-parametrit [kohteet tilannepvm kohdepvm]
  "Hakee tieosoitteet kohteista ja niiden alikohteista."
  (into []
    (mapcat (fn [kohde]
              (let [tr (:tierekisteriosoitevali kohde)]
                (concat
                  [{:tunniste (kohteen-tunnus kohde "alku")
                    :tie (:tienumero tr)
                    :osa (:aosa tr)
                    :etaisyys (:aet tr)
                    :ajorata (:ajorata tr)
                    :tilannepvm (pvm/pvm tilannepvm)
                    :kohdepvm (pvm/pvm kohdepvm)
                    :palautusarvot "2"}
                   {:tunniste (kohteen-tunnus kohde "loppu")
                    :tie (:tienumero tr)
                    :osa (:losa tr)
                    :etaisyys (:let tr)
                    :ajorata (:ajorata tr)
                    :tilannepvm (pvm/pvm tilannepvm)
                    :kohdepvm (pvm/pvm kohdepvm)
                    :palautusarvot "2"}]
                  (mapcat (fn [alikohde]
                            (let [tr (:tierekisteriosoitevali alikohde)]
                              [{:tunniste (alikohteen-tunnus kohde alikohde "alku")
                                :tie (:tienumero tr)
                                :osa (:aosa tr)
                                :etaisyys (:aet tr)
                                :ajorata (:ajorata tr)
                                :tilannepvm (pvm/pvm tilannepvm)
                                :kohdepvm (pvm/pvm kohdepvm)
                                :palautusarvot "2"}
                               {:tunniste (alikohteen-tunnus kohde alikohde "loppu")
                                :tie (:tienumero tr)
                                :osa (:losa tr)
                                :etaisyys (:let tr)
                                :ajorata (:ajorata tr)
                                :tilannepvm (pvm/pvm tilannepvm)
                                :kohdepvm (pvm/pvm kohdepvm)
                                :palautusarvot "2"}]))
                    (:alikohteet kohde)))))
      kohteet)))


(defn vkm-parametrit [tieosoitteet]
  {:json (cheshire/encode tieosoitteet)})

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
            (let [parametrit {:json (cheshire/encode tieosoitteet)}
                  http-asetukset {:metodi :POST
                                  :url url
                                  :lomakedatana? true}
                  {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset parametrit)]
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
