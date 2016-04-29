(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus])
  (:use [slingshot.slingshot :only [throw+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this tunniste nimi vuosi])
  (hae-kohteet [this urakka-id])
  (laheta-kohde [this kohde-id]))

(defn kasittele-urakoiden-hakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakoiden haulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakoiden-hakuvastaus/lue-sanoma sisalto)
        virhe (:virhe vastaus)]
    (if virhe
      (throw+
        {:type +virhe-urakoiden-haussa+
         :virheet {:virhe virhe}})
      vastaus)))

(defn hae-urakat-yhasta [integraatioloki db url tunniste sampo-id vuosi]
  (let [url (str url "/urakkahaku")]
    (log/debug (format "Haetaan YHA:sta urakata (tunniste: %s, nimi: %s & vuosi: %s)" tunniste sampo-id vuosi))
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "urakoiden-haku"
      (fn [konteksti]
        (let [parametrit {"tunniste" tunniste
                          "nimi" sampo-id
                          "vuosi" vuosi}
              http-asetukset {:metodi :GET
                              :url url
                              :parametrit parametrit}
              {body :body headers :headers}
              (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-urakoiden-hakuvastaus body headers))))))

(defn hae-urakan-kohteet-yhasta [integraatioloki db url urakka-id]
  ;; todo: toteuta
  )

(defn laheta-kohde-yhan [integraatioloki db url kohde-id]
  ;; todo: toteuta
  )

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YllapidonUrakoidenHallinta

  (hae-urakat [this tunniste nimi vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) tunniste nimi vuosi))
  (hae-kohteet [this urakka-id]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) (:url (:yha asetukset)) urakka-id))
  (laheta-kohde [this kohde-id]
    (laheta-kohde-yhan (:integraatioloki this) (:db this) (:url (:yha asetukset)) kohde-id)))

