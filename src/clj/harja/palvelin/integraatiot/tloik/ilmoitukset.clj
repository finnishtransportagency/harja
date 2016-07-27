(ns harja.palvelin.integraatiot.tloik.ilmoitukset
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clj-time.core :as time]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitus-sanoma]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma :as kuittaus-sanoma]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot]
            [harja.kyselyt.urakat :as urakat]
            [harja.tyokalut.xml :as xml]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.ilmoitukset :as ilmoitukset-q]
            [harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit :as paivystajaviestit]
            [harja.palvelin.palvelut.urakat :as urakkapalvelu]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [clj-time.core :as t]
            [clj-time.coerce :as c])
  (:use [slingshot.slingshot :only [try+]]))

(def +xsd-polku+ "xsd/tloik/")

(defn laheta-kuittaus [sonja lokittaja kuittausjono kuittaus korrelaatio-id
                       tapahtuma-id onnistunut lisatietoja]
  (lokittaja :lahteva-jms-kuittaus kuittaus tapahtuma-id onnistunut lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus {:correlation-id korrelaatio-id}))

(defn lue-ilmoitus [sonja lokittaja kuittausjono korrelaatio-id tapahtuma-id viesti]
  (let [viesti (.getText viesti)]
    (if (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" viesti)
      (ilmoitus-sanoma/lue-viesti viesti)
      (let [virhe "XML-sanoma ei ole harja-tloik.xsd skeeman mukainen."
            kuittaus (kuittaus-sanoma/muodosta "-" nil (.toString (time/now)) "virhe"
                                               nil nil virhe)]
        (laheta-kuittaus sonja lokittaja kuittausjono kuittaus
                         korrelaatio-id tapahtuma-id false virhe)))))

(defn hae-urakka [db ilmoitus]
  (when-let [urakka-id (first (urakkapalvelu/hae-urakka-idt-sijainnilla db (:urakkatyyppi ilmoitus)
                                                                        (:sijainti ilmoitus)))]
    (first (urakat/hae-urakka db urakka-id))))

(defn- merkitse-automaattisesti-vastaanotetuksi [db ilmoitus ilmoitus-id jms-lahettaja]
  (log/info "Ilmoittaja urakan organisaatiossa, merkitään automaattisesti vastaanotetuksi.")
  (let [ilmoitustoimenpide-id (:id (ilmoitukset-q/luo-ilmoitustoimenpide<!
                                     db {:ilmoitus ilmoitus-id
                                         :ilmoitusid (:ilmoitus-id ilmoitus)
                                         :kuitattu (c/to-date (t/now))
                                         :vapaateksti nil
                                         :kuittaustyyppi "vastaanotto"
                                         :kuittaaja_henkilo_etunimi nil
                                         :kuittaaja_henkilo_sukunimi nil
                                         :kuittaaja_henkilo_matkapuhelin nil
                                         :kuittaaja_henkilo_tyopuhelin nil
                                         :kuittaaja_henkilo_sahkoposti nil
                                         :kuittaaja_organisaatio_nimi nil
                                         :kuittaaja_organisaatio_ytunnus nil
                                         :kasittelija_henkilo_etunimi nil
                                         :kasittelija_henkilo_sukunimi nil
                                         :kasittelija_henkilo_matkapuhelin nil
                                         :kasittelija_henkilo_tyopuhelin nil
                                         :kasittelija_henkilo_sahkoposti nil
                                         :kasittelija_organisaatio_nimi nil
                                         :kasittelija_organisaatio_ytunnus nil}))]
    (ilmoitustoimenpiteet/laheta-ilmoitustoimenpide jms-lahettaja db ilmoitustoimenpide-id)))

(defn- laheta-ilmoitus-paivystajille [db ilmoitus paivystajat urakka-id ilmoitusasetukset]
  (if (empty? paivystajat)
    (log/info "Urakalle " urakka-id " ei löydy yhtään tämänhetkistä päivystäjää!")
    (doseq [paivystaja paivystajat]
      (paivystajaviestit/laheta ilmoitusasetukset db (assoc ilmoitus :urakka-id urakka-id)
                                paivystaja))))

(defn kasittele-ilmoitus
  "Tallentaa ilmoituksen ja tekee tarvittavat huomautus- ja ilmoitustoimenpiteet"
  [sonja ilmoitusasetukset lokittaja db tapahtumat kuittausjono urakka
   ilmoitus viesti-id korrelaatio-id tapahtuma-id jms-lahettaja]
  (let [urakka-id (:id urakka)
        ilmoitus-id (:ilmoitus-id ilmoitus)
        paivystajat (yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat db urakka-id)
        kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (time/now) "valitetty" urakka
                                           paivystajat nil)
        ilmoittaja-urakan-urakoitsijan-organisaatiossa?
        (kayttajat-q/onko-kayttaja-nimella-urakan-organisaatiossa? db urakka-id ilmoitus)
        ilmoitus-kanta-id (ilmoitus/tallenna-ilmoitus db ilmoitus)]

    (notifikaatiot/ilmoita-saapuneesta-ilmoituksesta tapahtumat urakka-id ilmoitus-id)
    (if ilmoittaja-urakan-urakoitsijan-organisaatiossa?
      (merkitse-automaattisesti-vastaanotetuksi db ilmoitus ilmoitus-kanta-id jms-lahettaja)
      (laheta-ilmoitus-paivystajille db ilmoitus paivystajat urakka-id ilmoitusasetukset))

    (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id true nil)))

(defn kasittele-tuntematon-urakka [sonja lokittaja kuittausjono viesti-id ilmoitus-id
                                   korrelaatio-id tapahtuma-id]
  (let [virhe (format "Urakkaa ei voitu päätellä T-LOIK:n ilmoitukselle (id: %s, viesti id: %s)"
                      ilmoitus-id viesti-id)
        kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (.toString (time/now)) "virhe" nil
                                           nil "Tiedoilla ei voitu päätellä urakkaa.")]
    (log/error virhe)
    (laheta-kuittaus sonja lokittaja kuittausjono kuittaus
                     korrelaatio-id tapahtuma-id false virhe)))

(defn vastaanota-ilmoitus [sonja lokittaja ilmoitusasetukset tapahtumat db kuittausjono jms-lahettaja viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [jms-viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        korrelaatio-id (.getJMSCorrelationID viesti)
        tapahtuma-id (lokittaja :saapunut-jms-viesti jms-viesti-id viestin-sisalto)
        {:keys [viesti-id ilmoitus-id] :as ilmoitus}
        (lue-ilmoitus sonja lokittaja kuittausjono korrelaatio-id tapahtuma-id viesti)]
    (try+
      (if-let [urakka (hae-urakka db ilmoitus)]
        (kasittele-ilmoitus sonja ilmoitusasetukset lokittaja db tapahtumat kuittausjono urakka
                            ilmoitus viesti-id korrelaatio-id tapahtuma-id jms-lahettaja)
        (kasittele-tuntematon-urakka sonja lokittaja kuittausjono viesti-id ilmoitus-id
                                     korrelaatio-id tapahtuma-id))
      (catch Exception e
        (log/error e (format "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta"
                             " (id: %s, viesti id: %s)" ilmoitus-id viesti-id))
        (let [virhe (str (format "Poikkeus (id: %s, viesti id: %s) " ilmoitus-id viesti-id) e)
              kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (.toString (time/now))
                                                 "virhe" nil nil "Sisäinen käsittelyvirhe.")]
          (laheta-kuittaus sonja lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id
                           false virhe))))))
