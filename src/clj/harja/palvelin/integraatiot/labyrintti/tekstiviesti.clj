(ns harja.palvelin.integraatiot.labyrintti.tekstiviesti
  "Tekstiviestin vastaanotto pilviympäristössä. Nimiavaruudessa *.labyrintti.sms on toteutus tekstiviestien
  lähetystä varten sekä toteutus tekstiviestien vastaanottoa varten vanhassa ympäristössä. Pilviympäristön
  vastaanotto on kopio vanhasta, mutta toteutus on erikseen, koska kutsu autentikoidaan pilvitoteutuksessa jo integraatioväylällä.
  Vanhan toteutuksen kutstuissa on basic auth Harjan päässä ja siksi erilaiset käsittelytä harja-infra-kerroksessa.
  Harja tekstiviestien lähettämiseen ja vastaanottoon käyttää LinkMobilityn LinkSMS-rajapintaa. Viestit kulkevat Väyläviraston integraatioväylän kautta.
  Vastaanotto välittää tekstiviestinä lähetetyn toimenpidekuittauksen eteenpäin tloik-integraatiolle (ja sitä kautta T-LOIKiin ja Palauteväylälle)."
  ;;TODO: Kun #yliheitto, yhdistä tähän lähetystoteutus ja poista vanha sms. Refaktoroi samalla labyrintti-sana historiaan.
  (:require [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [taoensso.timbre :as log]
            [compojure.core :refer [POST]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol Sms
  (rekisteroi-kuuntelija! [this kasittely-fn])
  (laheta [this numero viesti otsikot]))

;; TODO: Parsi uuden SMS-integraation mukainen vastaus
(defn kasittele-vastaus [body headers]
  (log/debug (format "SMS-palvelu vastasi: sisältö: %s, otsikot: %s" body headers))
  (when (and body (.contains (string/lower-case body) "error"))
    (throw+ {:type :sms-lahetys-epaonnistui
             :error body}))
  {:sisalto body :otsikot headers})

;; TODO: Toteuta tuki uudelle SMS-integraatiolle, määrittele sopiva payload
;; Uusi SMS-lähetys
(defn laheta-sms [db integraatioloki sms-url apiavain numero viesti otsikot]
  (if (or (empty? apiavain) (empty? sms-url))
    (log/warn "Tunnistautumistietoja tai URLia SMS-palveluun ei ole annettu. Viestiä ei voida lähettää.")
    ;; TODO: Määrittele uusi järjestelmä 'sms' ja integraatiotapahtumat sille
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "sms" "laheta"
      (fn [konteksti]
        (let [otsikot (merge
                        {"Content-Type" "application/x-www-form-urlencoded"
                         "x-api-key" apiavain}
                        otsikot)
              parametrit {"dests" numero
                          "text" viesti}
              http-asetukset {:metodi :POST
                              :url sms-url
                              :otsikot otsikot
                              :lomakedatana? true}      ;; Parametrit lähetetään avain-arvo-pareina form-parametreissä
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset parametrit)]
          (kasittele-vastaus body headers))))))


;; Vanha SMS-lähetys
(defn laheta-sms-linkmobility [db integraatioloki sms-url apiavain numero viesti otsikot]
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
                              :otsikot otsikot
                              :lomakedatana? true}      ;; Parametrit lähetetään avain-arvo-pareina form-parametreissä
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset parametrit)]
          (kasittele-vastaus body headers))))))

(defn laheta-sms* [db integraatioloki sms-url apiavain numero viesti otsikot]
  ;; TODO: Käsittele vanhan ja uuden integraation lähetykset
  ;; TODO: Hallinnoidaan uuden ja vanhan integraation käyttöönottoa feature-flagilla
  )

(defn kasittele-epaonnistunut-viestin-kasittely [integraatioloki tapahtuma-id poikkeus]
  (log/error (format "Tekstiviestin vastaanotossa tapahtui poikkeus." poikkeus))
  (integraatioloki/kirjaa-epaonnistunut-integraatio
    integraatioloki
    "Tekstiviestin vastaanotossa tapahtui poikkeus"
    (.toString poikkeus)
    tapahtuma-id
    nil))

;; TODO: Tekstiviestin vastaanotto poistuu käytöstä.
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

(defrecord Tekstiviesti [sms-asetukset kuuntelijat]
  component/Lifecycle
  (start [{http :http-palvelin integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :vastaanota-tekstiviesti
      (POST "/tekstiviesti/toimenpidekuittaus" request (vastaanota-tekstiviesti integraatioloki request kuuntelijat))
      true)
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :vastaanota-tekstiviesti)
    (reset! kuuntelijat #{})
    this)

  ;; TODO: Tarvitaanko kuuntelijoita?
  Sms
  (rekisteroi-kuuntelija! [this kuuntelija-fn]
    (swap! kuuntelijat conj kuuntelija-fn)
    #(swap! kuuntelijat disj kuuntelija-fn))

  ;; TODO: Käsittele vanhan ja uuden integraation lähetykset
  (laheta [this numero viesti otsikot]
    (laheta-sms* (:db this)
      (:integraatioloki this)
      (:url sms-asetukset)
      (:apiavain sms-asetukset)
      numero
      viesti
      otsikot)))

;; TODO:
(defn luo-tekstiviesti-komponentti [sms-asetukset]
  (->Tekstiviesti sms-asetukset (atom #{})))

(defrecord FeikkiTekstiviesti []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  Sms
  (rekisteroi-kuuntelija! [this kasittelija]
    (log/info "Feikki SMS-palvelu EI tue kuuntelijan rekisteröintiä")
    #(log/info "Poistetaan Feikki SMS-palvelun kuuntelija"))
  (laheta [this numero viesti otsikot]
    (log/info "Feikki SMS-palvelu lähettää muka viestin numeroon " numero ": " viesti)))

(defn luo-feikki-tekstiviesti-komponentti []
  (->FeikkiTekstiviesti))
