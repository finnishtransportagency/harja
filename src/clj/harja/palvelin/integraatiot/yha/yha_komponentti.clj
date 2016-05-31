(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma :as urakan-kohdehakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [org.httpkit.fake :refer [with-fake-http]]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)
(def +virhe-urakan-kohdehaussa+ ::yha-virhe-urakan-kohdehaussa)
(def +virhe-kohteen-lahetyksessa+ ::yha-virhe-kohteen-lahetyksessa)

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this yhatunniste sampotunniste vuosi])
  (hae-kohteet [this urakka-id kayttajatunnus])
  (laheta-kohteet [this urakka-id kohde-idt]))

(defn kasittele-urakoiden-hakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakan kohdehaulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakoiden-hakuvastaus/lue-sanoma sisalto)
        urakat (:urakat vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (do
        (log/error (format "Urakoiden haussa YHA:sta tapahtui virhe: %s" virhe))
        (throw+
          {:type +virhe-urakoiden-haussa+
           :virheet {:virhe virhe}}))
      urakat)))

(defn kasittele-urakan-kohdehakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakoiden haulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakan-kohdehakuvastaus/lue-sanoma sisalto)
        kohteet (:kohteet vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (do
        (log/error (format "Urakan kohteiden haussa YHA:sta tapahtui virhe: %s" virhe))
        (throw+
          {:type +virhe-urakan-kohdehaussa+
           :virheet {:virhe virhe}}))
      kohteet)))

(defn kasittele-urakan-kohdelahetysvastaus [body headers]
  ;; todo: jos onnistunut, merkitse kohde lähetetyksi
  )

(defn lisaa-http-parametri [parametrit avain arvo]
  (if arvo
    (assoc parametrit avain arvo)
    parametrit))

(defn hae-urakat-yhasta [integraatioloki db url yhatunniste sampotunniste vuosi]
  (let [url (str url "urakkahaku")]
    (log/debug (format "Haetaan YHA:sta urakata (tunniste: %s, sampotunnus: %s & vuosi: %s). URL: "
                       yhatunniste sampotunniste vuosi url))
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "urakoiden-haku"
      (fn [konteksti]
        (let [parametrit {"tunniste" yhatunniste
                          "sampotunnus" sampotunniste
                          "vuosi" vuosi}
              http-asetukset {:metodi :GET
                              :url url
                              :parametrit parametrit}
              {body :body headers :headers}
              (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-urakoiden-hakuvastaus body headers))))))

(defn hae-urakan-kohteet-yhasta [integraatioloki db url urakka-id kayttajatunnus]
  (if-let [yha-id (q-yha-tiedot/hae-urakan-yha-id db {:urakkaid urakka-id})]
    (let [url (str url (format "haeUrakanKohteet" yha-id))
          vuosi (pvm/vuosi (pvm/nyt))
          ;; todo: poista pultattu urakan yha-id, kun CGI saa korjattua rajapinnan
          yha-id 251604402]
      (log/debug (format "Haetaan urakan (id: %s, YHA-id: %s) kohteet YHA:sta. URL: %s" urakka-id yha-id url))

      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "yha" "kohteiden-haku"
        (fn [konteksti]
          (let [parametrit (-> {}
                               (lisaa-http-parametri "yha-id" yha-id)
                               (lisaa-http-parametri "vuosi" vuosi)
                               (lisaa-http-parametri "kayttaja" kayttajatunnus))
                http-asetukset {:metodi :GET :url url :parametrit parametrit}
                {body :body headers :headers}
                (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
            (kasittele-urakan-kohdehakuvastaus body headers)))))
    (do
      (let [virhe (format "Urakan (id: %s) YHA-id:tä ei löydy tietokannasta. Kohteita ei voida hakea." urakka-id)]
        (log/error virhe)
        (throw+
          {:type +virhe-urakan-kohdehaussa+
           :virheet {:virhe virhe}})))))

(defn hae-kohteen-tiedot [db kohde-id]
  (if-let [kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))]
    (let [alikohteet (q-yllapitokohteet/hae-yllapitokohteen-kohdeosat db {:yllapitokohde kohde-id})
          paallystys-ilmoitus (first (q-paallystys/hae-urakan-paallystysilmoitus-paallystyskohteella db kohde-id))]
      {:kohde kohde
       :alikohteet alikohteet
       :paallystys-ilmoitus paallystys-ilmoitus})
    (let [virhe (format "Tuntematon kohde (id: %s)." kohde-id)]
      (log/error virhe)
      (throw+
        {:type +virhe-kohteen-lahetyksessa+
         :virheet {:virhe virhe}}))))

(defn laheta-kohteet-yhan [integraatioloki db url urakka-id kohde-idt]
  (log/debug (format "Lähetetään urakan (id: %s) kohteet: %s YHA:n URL:lla: %s." urakka-id kohde-idt url))
  (if-let [urakka (q-yha-tiedot/hae-urakan-yhatiedot db urakka-id)]
    (let [urakka (assoc urakka :harjaid urakka-id :sampoid (q-urakat/hae-urakan-sampo-id db urakka-id))
          kohteet (mapv #(hae-kohteen-tiedot db %) kohde-idt)
          url (str url "toteumatiedot")
          kutsudata (kohteen-lahetyssanoma/muodosta urakka kohteet)]
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "yha" "kohteiden-lahetys"
        (fn [konteksti]
          (let [http-asetukset {:metodi :POST :url url}
                {body :body headers :headers}
                (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
            (kasittele-urakan-kohdelahetysvastaus body headers)))))
    (let [virhe (format "Urakan (id: %s) YHA-tietoja ei löydy." urakka-id)]
      (log/error virhe)
      (throw+
        {:type +virhe-kohteen-lahetyksessa+
         :virheet {:virhe virhe}}))))

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YllapidonUrakoidenHallinta

  (hae-urakat [this yhatunniste sampotunniste vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) (:url asetukset) yhatunniste sampotunniste vuosi))
  (hae-kohteet [this urakka-id kayttajatunnus]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) (:url asetukset) urakka-id kayttajatunnus))
  (laheta-kohteet [this urakka-id kohde-idt]
    (laheta-kohteet-yhan (:integraatioloki this) (:db this) (:url asetukset) urakka-id kohde-idt)))