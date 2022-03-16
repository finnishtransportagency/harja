(ns harja.palvelin.integraatiot.vayla-rest.sahkoposti
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.integraatiot :as q]
            [compojure.core :refer [PUT GET POST]]
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
   Kuittauksen sisältö on suunnilleen xml muotoisena: {:viesti-id <viesti-id>
                                                      :aika <datetime>
                                                      :onnistunut <boolean true/false>}
   Kuittaukselle ei kuitenkaan tehdä sähköpostin tilanteessa mitään erikoista. Käsittely on osana integraatio-tapahtumaa.
   Tässä vain extra varmistetaan, että kuittaus on loogisesti oikein ja logitetaan mahdollinen virhe."
  [body]

  (try
    (sahkoposti-sanomat/lue-kuittaus body)
    (catch Exception e
      (log/error "Virhe käsiteltäessä sähköpostin kuittausta: " e)
      ;; Palautetaan virheen sattuessa nil
      nil)))

(defn kasittele-sahkoposti-ja-liite-vastaus
  "Liitteellinen sähköposti on Harjassa aina tietyöilmoitus. Käsitellään niihin tulevat kuittaukset hieman eri tavalla."
  [body db]
  (try
    (let [kuittaus-vastaus (sahkoposti-sanomat/lue-kuittaus body)
          _ (q-tietyoilmoituksen-e/paivita-lahetetyn-emailin-tietoja db
              (merge {::tietyoilmoituksen-e/kuitattu (:aika kuittaus-vastaus)}
                (when-not (:onnistunut kuittaus-vastaus)
                  {::tietyoilmoituksen-e/lahetysvirhe (:aika kuittaus-vastaus)}))
              {::tietyoilmoituksen-e/lahetysid (:viesti-id kuittaus-vastaus)})]
      kuittaus-vastaus)
    (catch Exception e
      (log/error "Virhe käsiteltäessä sähköpostin kuittausta: " e)
      ;; Palautetaan virheen sattuessa nil
      nil)))

(defn laheta-sahkoposti-sahkopostipalveluun
  "Harjalla on lähetetty sähköpostit aiemmin laittamalla niistä jonoon ilmoitus, jonka Sähköpostipalvelin on käynyt
  lukemassa ja lähettänyt. Tämä fn lähettää samaiset sähköpostit suoralla api-rest kutsulla."
  [db asetukset integraatioloki sahkoposti-xml liite?]
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "api" (if liite?
                                 "sahkoposti-ja-liite-lahetys"
                                 "sahkoposti-lahetys") nil
      (fn [konteksti]
        (let [http-asetukset {:metodi :POST
                              :url (muodosta-lahetys-uri asetukset liite?)
                              :otsikot {"Content-Type" "application/xml"}
                              :kayttajatunnus (get-in asetukset [:api-sahkoposti :kayttajatunnus])
                              :salasana (get-in asetukset [:api-sahkoposti :salasana])}
              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset sahkoposti-xml)]
          (if liite?
            (kasittele-sahkoposti-ja-liite-vastaus body db)
            (kasittele-sahkoposti-vastaus body)))))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      false)))

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

(defn vastaanota-sahkoposti [kutsun-parametrit kutsun-data kayttaja db this itmf asetukset integraatioloki]
  (let [jms-lahettaja (jms/jonolahettaja (tloik-komponentti/tee-lokittaja this "toimenpiteen-lahetys")
                        itmf (get-in asetukset [:tloik :toimenpidekuittausjono]))
        viesti-id (:viesti-id kutsun-data)
        kasitelty-vastaus (tloik-sahkoposti/vastaanota-sahkopostikuittaus jms-lahettaja db kutsun-data)
        ;; Lisää mahdolliset virheet kuittausviestiin
        virheet (if (= "Virheellinen kuittausviesti" (:otsikko kasitelty-vastaus))
                  [(:sisalto kasitelty-vastaus)]
                  nil)
        ;; Rakenna kuittaus xml välitettäväksi rajapinnan kutsujalle
        kuittaus-xml (muodosta-kuittaus {:viesti-id viesti-id} virheet)]
    ;; Palautetaan käsitelty vastaus
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
        (kutsukasittely/kasittele-kutsu db integraatioloki :sahkoposti-vastaanotto
          request xml-skeemat/+sahkoposti-kutsu+ xml-skeemat/+sahkoposti-vastaus+
          (fn [kutsun-parametrit kutsun-data kayttaja db] (vastaanota-sahkoposti kutsun-parametrit kutsun-data kayttaja db this itmf asetukset integraatioloki)))))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
      :sahkoposti-vastaanotto)
    this)

  Sahkoposti
  (laheta-viesti! [this lahettaja vastaanottaja otsikko sisalto]
    (let [viesti-id (str (UUID/randomUUID))
          sahkoposti (sahkoposti-sanomat/sahkoposti viesti-id lahettaja vastaanottaja otsikko sisalto)
          viesti (xml/tee-xml-sanoma sahkoposti)
          ;; Validoidaan viesti
          virhe (validoi-sahkoposti viesti)]
      (if (nil? virhe)
        (laheta-sahkoposti-sahkopostipalveluun (:db this) asetukset (:integraatioloki this) viesti false)
        (throw+ virhe))))
  (vastausosoite [this]
    (get-in asetukset [:api-sahkoposti :vastausosoite]))
  (laheta-viesti-ja-liite! [this lahettaja vastaanottajat otsikko sisalto tiedosto-nimi]
    (let [viesti-id (str (UUID/randomUUID))
          sahkoposti (sahkoposti-sanomat/sahkoposti-ja-liite viesti-id vastaanottajat lahettaja otsikko sisalto tiedosto-nimi (pvm/nyt))
          viesti (xml/tee-xml-sanoma sahkoposti)
          ;; Validoidaan viesti
          ;;TODO: Missään ei koskaan ole validoitu liitteellistä sähköpostia. Laita toimimaan
          virhe nil #_(validoi-sahkoposti-liite viesti)]
      (if (nil? virhe)
        (laheta-sahkoposti-sahkopostipalveluun (:db this) asetukset (:integraatioloki this) viesti true)
        (throw+ virhe))))
  (rekisteroi-kuuntelija!
    [this kuuntelija-fn]
    ;; Ei tee tarkoituksellisesti mitään, mutta toteuttaa Protokollan
    (log/error "ApiSahkoposti palvelu ei tue rekisteroi-kuuntelija! toiminnallisuutta!")
    nil))
