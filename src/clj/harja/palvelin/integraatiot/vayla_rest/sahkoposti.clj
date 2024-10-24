(ns harja.palvelin.integraatiot.vayla-rest.sahkoposti
  (:require [com.stuartsierra.component :as component]
            [clojure.string :as str]
            [compojure.core :refer [POST]]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut julkaise-palvelu]]
            [harja.tyokalut.xml :as xml]
            [harja.palvelin.integraatiot.api.tyokalut.xml-skeemat :as xml-skeemat]
            [harja.palvelin.integraatiot.sonja.sahkoposti.sanomat :as sahkoposti-sanomat]
            [harja.palvelin.integraatiot.tloik.sahkoposti :as tloik-sahkoposti]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :as kutsukasittely]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.tloik.tloik-komponentti :as tloik-komponentti]
            [harja.palvelin.integraatiot.sahkoposti :refer [Sahkoposti]]
            [harja.domain.tietyoilmoituksen-email :as tietyoilmoituksen-e]
            [harja.kyselyt.tietyoilmoituksen-email :as q-tietyoilmoituksen-e]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log])
  (:use [slingshot.slingshot :only [throw+ try+]])
  (:import (java.util UUID)))

(defn muodosta-lahetys-uri [asetukset liite?]
  (str (get-in asetukset [:api-sahkoposti :palvelin])
    (if liite?
      (get-in asetukset [:api-sahkoposti :sahkoposti-ja-liite-lahetys-url])
      (get-in asetukset [:api-sahkoposti :sahkoposti-lahetys-url]))))

(defn kasittele-sahkoposti-vastaus
  "Kun sähköposti lähetetään sähköpostipalvelu rest-apiin, niin sieltä tulee vastaukseksi kuittaus siitä, onnistuiko kaikki ihan ok.
   Kuittaus on ok, jos status on 200, muutoin logita virhe.
   Kuittaukselle ei kuitenkaan tehdä sähköpostin tilanteessa mitään erikoista. Käsittely on osana integraatio-tapahtumaa.
   Tässä vain extra varmistetaan, että kuittaus on loogisesti oikein ja logitetaan mahdollinen virhe."
  [status body]

  (try
    (if (= 200 status)
      body
      (do
        ;; Virheen sattuessa palauta nil
        (log/error "Virhe sähköpostin lähetyksessä :: saatu virhe: " body)
        nil))
    (catch Exception e
      (log/error "Virhe käsiteltäessä sähköpostin kuittausta :: poikkeus " e)
      ;; Palautetaan virheen sattuessa nil
      nil)))

(defn kasittele-sahkoposti-ja-liite-vastaus
  "Liitteellinen sähköposti on Harjassa aina tietyöilmoitus. Käsitellään niihin tulevat kuittaukset hieman eri tavalla."
  [status body db]
  (try
    (if (= 200 status)
      (let [kuittaus-vastaus (sahkoposti-sanomat/lue-kuittaus body)
            _ (q-tietyoilmoituksen-e/paivita-lahetetyn-emailin-tietoja db
                (merge {::tietyoilmoituksen-e/kuitattu (:aika kuittaus-vastaus)}
                  (when-not (:onnistunut kuittaus-vastaus)
                    {::tietyoilmoituksen-e/lahetysvirhe (:aika kuittaus-vastaus)}))
                {::tietyoilmoituksen-e/lahetysid (:viesti-id kuittaus-vastaus)})]
        kuittaus-vastaus)
      ;; Tapahtui virhe. Logitetaan se ja palautetaan nil
      (do
        (log/error "Virhe liitteellisen sähköpostin lähetyksessä :: saatu virhe: " body)
        nil)
      )
    (catch Exception e
      (log/error "Virhe käsiteltäessä sähköpostin kuittausta: " e)
      ;; Palautetaan virheen sattuessa nil
      nil)))

(defn laheta-sahkoposti-sahkopostipalveluun
  "Harjalla on lähetetty sähköpostit aiemmin laittamalla niistä jonoon ilmoitus, jonka Sähköpostipalvelin on käynyt
  lukemassa ja lähettänyt. Tämä fn lähettää samaiset sähköpostit suoralla api-rest kutsulla."
  [db asetukset integraatioloki sahkoposti-xml http-otsikot liite?]
  (try+
    (let [_ (log/info "Lähetä rest-api sähköposti.")
          vastaus (integraatiotapahtuma/suorita-integraatio
                    db integraatioloki "api" (if liite?
                                               "sahkoposti-ja-liite-lahetys"
                                               "sahkoposti-lahetys") nil
                    (fn [konteksti]
                      (let [http-asetukset {:metodi :POST
                                            :url (muodosta-lahetys-uri asetukset liite?)
                                            :otsikot (merge
                                                       {"Content-Type" "application/xml"}
                                                       http-otsikot)
                                            :kayttajatunnus (get-in asetukset [:api-sahkoposti :kayttajatunnus])
                                            :salasana (get-in asetukset [:api-sahkoposti :salasana])}
                            {body :body headers :headers status :status} (integraatiotapahtuma/laheta konteksti :http http-asetukset sahkoposti-xml)]
                        (if liite?
                          (kasittele-sahkoposti-ja-liite-vastaus status body db)
                          (kasittele-sahkoposti-vastaus status body)))))
          _ (log/info "rest-api sähköpostin lähetys onnistui")]
      vastaus)
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (do
        (log/error "rest-api sähköpostin lähetys epäonnistui! " virheet)
        false))))

(defn muodosta-kuittaus
  "Tee annetulle vastaanotetulle sähköpostiviestille kuittausviesti"
  [{viesti-id :viesti-id} virheet]
  [:sahkoposti:kuittaus {:xmlns:sahkoposti "http://www.liikennevirasto.fi/xsd/harja/sahkoposti"}
   [:viestiId viesti-id]
   [:aika (xml/formatoi-xsd-datetime (pvm/nyt))]
   [:onnistunut (nil? virheet)]
   (when virheet
     (for [virhe virheet]
       [:virheet virhe]))])

(defn muodosta-ilmoitus-virheellisesta-sahkopostista
  "Jos saamme virheellisen sähköpostin, niin vastataan siihen parhaan tietämyksen mukaan ohjeistetulla vastauksella."
  [harja-lahettaja {:keys [viesti-id lahettaja otsikko sisalto] :as kutsun-data} virheet]
  (let [;; Muodostetaan vastaus lähetettyyn sähköpostiin jolloin lahettajasta tulee vastaanottaja
        vastaanottaja lahettaja
        otsikko (str "Re: " otsikko)
        virheet-html (str/join "<br>" virheet)
        viesti-html (str "<html><body>"
                      virheet-html
                      "<br> Saatu viesti: <br>"
                      sisalto
                      "</body></html>")
        sahkoposti (sahkoposti-sanomat/sahkoposti viesti-id harja-lahettaja vastaanottaja otsikko viesti-html)]
    sahkoposti))

(defn vastaanota-sahkoposti [kutsun-parametrit kutsun-data kayttaja db this itmf asetukset integraatioloki]
  (log/info "RestApi - Sähköposti :: vastaanota-sahkoposti")
  (let [jms-lahettaja (jms/jonolahettaja (tloik-komponentti/tee-lokittaja this "toimenpiteen-lahetys")
                        itmf (get-in asetukset [:tloik :toimenpideviestijono]))
        viesti-id (:viesti-id kutsun-data)
        kasitelty-vastaus (tloik-sahkoposti/vastaanota-sahkopostikuittaus jms-lahettaja db kutsun-data)
        otsikko (:otsikko kutsun-data)
        [_ _ ilmoitus-id] (when otsikko (re-matches tloik-sahkoposti/otsikko-pattern otsikko))
        ;; Lisää mahdolliset virheet kuittausviestiin
        virheet (if (#{"Virheellinen kuittausviesti" "Kuittausta ei voitu käsitellä"} (:otsikko kasitelty-vastaus))
                  [(:sisalto kasitelty-vastaus)]
                  nil)
        vastaus-virheelliseen-viestiin (when-not (nil? virheet)
                                         (-> (muodosta-ilmoitus-virheellisesta-sahkopostista
                                               (get-in asetukset [:api-sahkoposti :vastausosoite])
                                               kutsun-data virheet)
                                           (xml/tee-xml-sanoma)))
        ;; Rakenna kuittaus xml välitettäväksi rajapinnan kutsujalle
        kuittaus-xml (muodosta-kuittaus {:viesti-id viesti-id} virheet)]

    ;; Lähetetään kuittaus saatuun sähköpostiin, mikäli siinä on virheitä. Onnistuneesta vastaanotosta ei kuittausta lähetetä.
    (when-not (nil? virheet)
      (laheta-sahkoposti-sahkopostipalveluun (:db this) asetukset (:integraatioloki this)
        vastaus-virheelliseen-viestiin {"X-Correlation-ID" ilmoitus-id} false)
      kuittaus-xml)
    kuittaus-xml))

(def ^:const +xsd-polku+ "xsd/sahkoposti/")
(def ^:const +sahkoposti-xsd+ "sahkoposti.xsd")
(def ^:const +sahkoposti-liite-xsd+ "sahkoposti-liite.xsd")
(defn validoi-sahkoposti [xml-viesti]
  (when-not (xml/validi-xml? +xsd-polku+ +sahkoposti-xsd+ xml-viesti)
    (log/error "Lähetettävä sähköposti XML-tiedosto ei ole sahkoposti.xsd skeeman mukainen.")
    {:type virheet/+invalidi-xml+}))

(defn validoi-sahkoposti-liite [xml-viesti]
  (when-not (xml/validi-xml? +xsd-polku+ +sahkoposti-liite-xsd+ xml-viesti)
    (log/error "Lähetettävä sähköposti XML-tiedosto ei ole sahkoposti-liite.xsd skeeman mukainen.")
    {:type virheet/+invalidi-xml+}))

(defrecord ApiSahkoposti [asetukset]
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki itmf :itmf :as this}]
    (julkaise-reitti
      http :sahkoposti-vastaanotto
      (POST "/sahkoposti/toimenpidekuittaus" request
        (kutsukasittely/kasittele-sahkoposti-kutsu db integraatioloki :sahkoposti-vastaanotto
          request xml-skeemat/+sahkoposti-kutsu+ xml-skeemat/+sahkoposti-vastaus+
          (fn [kutsun-parametrit kutsun-data kayttaja db] (vastaanota-sahkoposti kutsun-parametrit kutsun-data kayttaja db this itmf asetukset integraatioloki)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :sahkoposti-vastaanotto)
    this)

  Sahkoposti
  (laheta-viesti! [this lahettaja vastaanottaja otsikko sisalto headers]
    (let [viesti-id (str (UUID/randomUUID))
          sahkoposti (sahkoposti-sanomat/sahkoposti viesti-id lahettaja vastaanottaja otsikko sisalto)
          viesti (xml/tee-xml-sanoma sahkoposti)
          ;; Validoidaan viesti
          virhe (validoi-sahkoposti viesti)]
      (if (nil? virhe)
        (laheta-sahkoposti-sahkopostipalveluun (:db this) asetukset (:integraatioloki this) viesti headers false)
        (throw+ virhe))))
  (laheta-ulkoisella-jarjestelmalla-viesti! [this lahettaja vastaanottaja otsikko sisalto headers username password port]
    ;; Ei tee tarkoituksellisesti mitään, mutta toteuttaa Protokollan
    (log/debug "ApiSahkoposti palvelu ei tue laheta-ulkoisella-jarjestelmalla-viesti! toiminnallisuutta."))
  (vastausosoite [this]
    (get-in asetukset [:api-sahkoposti :vastausosoite]))
  (laheta-viesti-ja-liite! [this lahettaja vastaanottajat otsikko sisalto headers tiedosto-nimi]
    (let [viesti-id (str (UUID/randomUUID))
          sahkoposti (sahkoposti-sanomat/sahkoposti-ja-liite viesti-id vastaanottajat lahettaja otsikko sisalto tiedosto-nimi (pvm/nyt))
          viesti (xml/tee-xml-sanoma sahkoposti)
          ;; Validoidaan viesti
          ;;TODO: Missään ei koskaan ole validoitu liitteellistä sähköpostia. Laita toimimaan
          virhe nil #_(validoi-sahkoposti-liite viesti)]
      (if (nil? virhe)
        (laheta-sahkoposti-sahkopostipalveluun (:db this) asetukset (:integraatioloki this) viesti headers true)
        (throw+ virhe))))
  (rekisteroi-kuuntelija!
    [this kuuntelija-fn]
    ;; Ei tee tarkoituksellisesti mitään, mutta toteuttaa Protokollan
    (log/debug "ApiSahkoposti palvelu ei tue rekisteroi-kuuntelija! toiminnallisuutta.")
    nil))
