(ns harja.palvelin.integraatiot.vkm.vkm-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.pvm :as pvm]
            [cheshire.core :as cheshire]
            [clojure.core :as core]
            [clojure.string :as string])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defprotocol Tieosoitemuunnos
  (muunna-osoitteet-verkolta-toiselle
    [this tieosoiteet]
    "Muuntaa annetut tieosoitteet päivän verkolta toiselle. Jokaisella tieosoitteella täytyy olla mäpissä :vkm-id avain
    kohdistamista varten."))

(defn vkm-virhe [vkm-kohteet tunniste]
  (some #(and (= tunniste (get % "tunniste"))
           (get % "virheet"))
        vkm-kohteet))

(defn virheelliset-tieosoitteet [tieosoitteet-vkm-hausta]
  (filter #(some? (:virheet %))) tieosoitteet-vkm-hausta)

(defn hae-vkm-osoite [vkm-kohteet hakutunnus]
  (first (filter #(= hakutunnus (get % "tunniste")) vkm-kohteet)))

(defn paivita-osoite [{:keys [tie aosa aet losa let] :as tieosoite} osoite virhe]
  (if osoite
    (merge
      (assoc tieosoite
        :tienumero (get osoite :tienumero tie)
        :aosa (get osoite :aosa aosa)
        :aet (get osoite :etaisyys aet)
        :losa (get osoite :losa losa)
        :let (get osoite :let let))
      (when virhe
        {:virhe virhe}))
    tieosoite))

(defn- yhdista-vkm-ajoradat
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

(defn- alku-ja-loppuosa-tasmaa? [alkuosa loppuosa]
  (let [alkuosan-tunniste (get alkuosa "tunniste")
        loppuosan-tunniste (get loppuosa "tunniste" (:tunniste loppuosa))]
    (and (=
           (string/replace loppuosan-tunniste #"loppu" "alku")
           alkuosan-tunniste)
      (not= loppuosan-tunniste alkuosan-tunniste))))

(defn- vkm-palautusarvo->tieosoitteet [vkm-osoitteet tieosoitteet]
  (map (fn [alkuosa]
         (let [loppuosat (filter (partial alku-ja-loppuosa-tasmaa? alkuosa) vkm-osoitteet)
               _ (when (< 1 (count loppuosat))
                   (log/error "VKM Palautusarvoista löytyi useampi loppuosa alkukohteella!"))
               loppuosa (first loppuosat)
               alku-virheet (get alkuosa "virheet")
               loppu-virheet (get loppuosa "virheet")
               ;; Muunnettavat tieosoitteet, palautetaan jos VKM:stä tulee virhe.
               alku-tieosoite (first (filter #(= (:tunniste %) (get "tunniste" alkuosa)) tieosoitteet))
               loppu-tieosoite (first (filter (partial alku-ja-loppuosa-tasmaa? alkuosa) tieosoitteet))]
           (merge {:tie (get alkuosa "tie" (:tie alku-tieosoite))
                   :aosa (get alkuosa "osa" (:etaisyys alku-tieosoite))
                   :losa (get loppuosa "osa" (:osa loppu-tieosoite))
                   :aet (get alkuosa "etaisyys" (:etaisyys alku-tieosoite))
                   :let (get loppuosa "etaisyys" (:etaisyys loppu-tieosoite))}
             (when (or alku-virheet loppu-virheet)
               {:virheet
                {:alku alku-virheet
                 :loppu loppu-virheet}}))))
    (filter #(string/includes? (get % "tunniste") "alku") vkm-osoitteet)))

(defn osoitteet-vkm-vastauksesta [tieosoitteet vastaus]
  (if vastaus
    (let [osoitteet-vastauksesta (cheshire/decode vastaus)
          vkm-osoitteet (mapv #(get % "properties") (get osoitteet-vastauksesta "features"))
          yhdistetyt-vkm-osoitteet (-> vkm-osoitteet
                                     (yhdista-vkm-ajoradat)
                                     (vkm-palautusarvo->tieosoitteet tieosoitteet))]
      yhdistetyt-vkm-osoitteet)
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

(defn muunna-tieosoitteet-verkolta-toiselle [{:keys [db integraatioloki url]} tieosoitteet]
  (when url
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
  (muunna-osoitteet-verkolta-toiselle [this tieosoitteet]
    (muunna-tieosoitteet-verkolta-toiselle this tieosoitteet)))
