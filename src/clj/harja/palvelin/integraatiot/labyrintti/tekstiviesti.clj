(ns harja.palvelin.integraatiot.labyrintti.tekstiviesti
  "Tekstiviestin vastaanotto ja lähetys pilviympäristössä.
  Tähän on toistaiseksi integroitu myös vanhan LinkMobility SMS-integraation lähetystoiminnallisuus.
  LinkMobilityn tulee korvaamaan uusi SMS-integraatio, jonka saa asetuksilla aktiiviseksi. Viestit kulkevat Väyläviraston integraatioväylän kautta.
  Tekstiviestien vastaanotto tulee myös poistumaan, kun uusi SMS-integraatio otetaan käyttöön.
  Vastaanotto välittää tekstiviestinä lähetetyn toimenpidekuittauksen eteenpäin tloik-integraatiolle (ja sitä kautta T-LOIKiin ja Palauteväylälle)."
  ;;TODO: Kun #yliheitto, poista vanha LinkSMS toteutus ja tekstiviestien vastaanotto
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util UUID)))


(defprotocol Sms
  (rekisteroi-kuuntelija! [this kasittely-fn])
  (laheta [this numero viesti korrelaatio-id otsikot]))


;; -- Uusi SMS-lähetys --

;; TODO: Parsi uuden SMS-integraation mukainen vastaus
(defn kasittele-vastaus [body headers]
  (log/debug (format "SMS-palvelu vastasi: sisältö: %s, otsikot: %s" body headers))
  (when (and body (.contains (string/lower-case body) "error"))
    (throw+ {:type :sms-lahetys-epaonnistui
             :error body}))
  {:sisalto body :otsikot headers})

(defn laheta-sms [db integraatioloki url apiavain puhelinnumero viesti korrelaatio-id otsikot]
  (if (or (empty? apiavain) (empty? url))
    (log/warn "Tunnistautumistietoja tai URLia SMS-palveluun ei ole annettu. Viestiä ei voida lähettää.")
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "sms" "laheta"
      (fn [konteksti]
        (let [otsikot (merge
                        {"Content-Type" "application/json"
                         "x-api-key" apiavain}
                        otsikot)
              payload {"viesti-id" (str (UUID/randomUUID))
                       "korrelaatio-id" (or korrelaatio-id "")
                       "vastaanottaja" puhelinnumero
                       "sisalto" viesti}
              http-asetukset {:metodi :POST
                              :url url
                              :otsikot otsikot}
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset payload)]
          (kasittele-vastaus body headers))))))


;; -- Vanha LinkMobilityn SMS-lähetys --
(defn kasittele-vastaus-linkmobility [body headers]
  (log/debug (format "SMS-palvelu vastasi: sisältö: %s, otsikot: %s" body headers))
  (when (and body (.contains (string/lower-case body) "error"))
    (throw+ {:type :sms-lahetys-epaonnistui
             :error body}))
  {:sisalto body :otsikot headers})

(defn laheta-sms-linkmobility [db integraatioloki sms-url apiavain numero viesti korrelaatio-id otsikot]
  (if (or (empty? apiavain) (empty? sms-url))
    (log/warn "Tunnistautumistietoja tai URLia LinkMobilityn SMS-palveluun (entinen Labyrintti) ei ole annettu. Viestiä ei voida lähettää.")
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "labyrintti" "laheta"
      (fn [konteksti]
        (let [otsikot (merge
                        {"Content-Type" "application/x-www-form-urlencoded"
                         "x-api-key" apiavain}
                        otsikot)
              parametrit {"dests" numero
                          "text" viesti}
              http-asetukset {:metodi :POST
                              :url sms-url
                              :otsikot (merge
                                         ;; LinkMobilityn SMS-integraatioon on välitetty korrelaatio-id otsikkona
                                         (when korrelaatio-id
                                           {"X-Correlation-ID" korrelaatio-id})
                                         otsikot)
                              ;; Parametrit lähetetään avain-arvo-pareina form-parametreissä
                              :lomakedatana? true}
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset parametrit)]
          (kasittele-vastaus-linkmobility body headers))))))

(defn laheta-sms* [db integraatioloki uusi-sms? asetukset numero viesti korrelaatio-id otsikot]
  (if uusi-sms?
    (laheta-sms db integraatioloki (:url asetukset) (:apiavain asetukset) numero viesti korrelaatio-id otsikot)
    (laheta-sms-linkmobility db integraatioloki (:sms-url asetukset) (:apiavain asetukset) numero viesti korrelaatio-id otsikot)))


;; Vanha LinkMobility SMS-viestien vastaanotto
;; TODO: Tekstiviestin vastaanotto poistuu käytöstä. Poista turhat funktiot siirtymäajan jälkeen. #yliheitto

(defn kasittele-epaonnistunut-viestin-kasittely [integraatioloki tapahtuma-id poikkeus]
  (log/error (format "Tekstiviestin vastaanotossa tapahtui poikkeus." poikkeus))
  (integraatioloki/kirjaa-epaonnistunut-integraatio
    integraatioloki
    "Tekstiviestin vastaanotossa tapahtui poikkeus"
    (.toString poikkeus)
    tapahtuma-id
    nil))

(defn vastaanota-tekstiviesti [integraatioloki kutsu kuuntelijat]
  (log/info (format "Vastaanotettiin tekstiviesti LinkMobilityn LinkSMS-palvelusta (entinen Labyrintti) : %s" (assoc-in kutsu [:headers "authorization"] "*****")))
  (let [url (:remote-addr kutsu)
        otsikot (:headers kutsu)
        parametrit (-> kutsu
                     :body
                     .bytes
                     (String.)
                     ring.util.codec/form-decode)
        viesti (integraatioloki/tee-rest-lokiviesti "sisään" url nil nil otsikot (str parametrit))
        tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "labyrintti" "vastaanota" nil viesti)
        numero (get parametrit "source")
        viesti (get parametrit "text")]
    (try
      ;; jos numero tai viesti on nil, tunnistetaan virhe ja heitetään poikkeus
      (when (or (nil? numero) (nil? viesti))
        (throw+ {:type :puhelinnumero-tai-viesti-puuttuu
                 :message (str "numero: " numero ", viesti: " viesti)}))
      (let [vastaukset (mapv #(% numero viesti) @kuuntelijat)
            vastausdata (if (empty? vastaukset) "" (str "text=" (string/join ", " vastaukset)))
            vastausviesti (integraatioloki/tee-rest-lokiviesti "ulos" url nil vastausdata nil nil)]
        (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki vastausviesti nil tapahtuma-id nil)
        {:status 200
         :body vastausdata
         :headers {"Content-Type" "application/x-www-form-urlencoded"
                   "Content-Length" (count vastausdata)}})
      (catch Exception e
        (kasittele-epaonnistunut-viestin-kasittely integraatioloki tapahtuma-id e)
        {:status 500}))))


(defrecord Tekstiviesti [sms-asetukset vanhat-sms-asetukset kuuntelijat]
  component/Lifecycle
  (start [{http :http-palvelin integraatioloki :integraatioloki :as this}]
    ;; TODO: Tekstiviestien vastaanotto poistuu käytöstä. Poista turha toiminnallisuus siirtymäajan jälkeen. #yliheitto
    (julkaise-reitti
      http :vastaanota-tekstiviesti
      (POST "/tekstiviesti/toimenpidekuittaus" request (vastaanota-tekstiviesti integraatioloki request kuuntelijat))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :vastaanota-tekstiviesti)
    (reset! kuuntelijat #{})
    this)

  Sms
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
    (swap! kuuntelijat conj kuuntelija-fn)
    #(swap! kuuntelijat disj kuuntelija-fn))

  (laheta [this numero viesti korrelaatio-id otsikot]
    ;; FIXME: Seuraamme siirtymäajan uuden SMS-integraation käyttöönottoa, jolloin vanha LinkMobilityn integraatio on vielä käytössä.
    ;;       Kun siirtymäaika on ohi, poistamme vanhan LinkMobilityn integraation käytöstä ja käytämme vain uutta SMS-integraatiota. #yliheitto
    (let [uusi-sms-aktiivinen? (:aktiivinen? sms-asetukset)
          asetukset (if uusi-sms-aktiivinen? sms-asetukset vanhat-sms-asetukset)]

      (laheta-sms* (:db this)
        (:integraatioloki this)
        uusi-sms-aktiivinen?
        asetukset
        numero
        viesti
        korrelaatio-id
        otsikot))))


(defn luo-tekstiviesti-komponentti [sms-asetukset vanhat-sms-asetukset]
  ;; TODO: Ota vanhat asetukset pois, kun LinkSMS integraatiosta on päästy kokonaan #yliheitto
  (->Tekstiviesti sms-asetukset vanhat-sms-asetukset (atom #{})))

(defrecord FeikkiTekstiviesti []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  Sms
  (rekisteroi-kuuntelija! [this kasittelija]
    (log/info "Feikki SMS-palvelu EI tue kuuntelijan rekisteröintiä")
    #(log/info "Poistetaan Feikki SMS-palvelun kuuntelija"))
  (laheta [this numero viesti korrelaatio-id otsikot]
    (log/info "Feikki SMS-palvelu lähettää muka viestin numeroon " numero ": " viesti)))

(defn luo-feikki-tekstiviesti-komponentti []
  (->FeikkiTekstiviesti))
