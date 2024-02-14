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
            [taoensso.timbre :as log]
            [compojure.core :refer [POST]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik-komponentti]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tloik-sms])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kasittele-epaonnistunut-viestin-kasittely [integraatioloki tapahtuma-id poikkeus]
  (log/error (format "Tekstiviestin vastaanotossa tapahtui poikkeus." poikkeus))
  (integraatioloki/kirjaa-epaonnistunut-integraatio
    integraatioloki
    "Tekstiviestin vastaanotossa tapahtui poikkeus"
    (.toString poikkeus)
    tapahtuma-id
    nil))

(defn vastaanota-tekstiviesti [integraatioloki kutsu this asetukset]
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
      (let [jms-lahettaja (jms/jonolahettaja (tloik-komponentti/tee-lokittaja this "toimenpiteen-lahetys") (:itmf this) (get-in asetukset [:tloik :toimenpideviestijono]))
            vastaukset (tloik-sms/vastaanota-tekstiviestikuittaus jms-lahettaja (:db this) numero viesti)
            vastausdata (if (empty? vastaukset) "" (str "text=" vastaukset))
            vastausviesti (integraatioloki/tee-rest-lokiviesti "ulos" url nil vastausdata nil nil)]
        (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki vastausviesti nil tapahtuma-id nil)
        {:status 200
         :body vastausdata
         :headers {"Content-Type" "application/x-www-form-urlencoded"
                   "Content-Length" (count vastausdata)}})
      (catch Exception e
        (kasittele-epaonnistunut-viestin-kasittely integraatioloki tapahtuma-id e)
        {:status 500}))))

(defrecord Tekstiviesti [asetukset]
  component/Lifecycle
  (start [{http :http-palvelin integraatioloki :integraatioloki itmf :itmf :as this}]
    (julkaise-reitti
      http :vastaanota-tekstiviesti
      (POST "/tekstiviesti/toimenpidekuittaus" request (vastaanota-tekstiviesti integraatioloki request this asetukset))
      true))
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http :vastaanota-tekstiviesti)
    this))
