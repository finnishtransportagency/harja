(ns harja.palvelin.integraatiot.tloik.ilmoitukset
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clj-time.core :as time]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitus-sanoma]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus]
            [harja.palvelin.integraatiot.tloik.sanomat.harja-kuittaus-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.api.tyokalut.ilmoitusnotifikaatiot :as notifikaatiot]
            [harja.kyselyt.urakat :as urakat]
            [harja.tyokalut.xml :as xml]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.kayttajat :as kayttajat-q]
            [harja.kyselyt.tieliikenneilmoitukset :as ilmoitukset-q]
            [harja.palvelin.integraatiot.tloik.kasittely.paivystajaviestit :as paivystajaviestit]
            [harja.palvelin.palvelut.urakat :as urakkapalvelu]
            [harja.palvelin.integraatiot.tloik.ilmoitustoimenpiteet :as ilmoitustoimenpiteet]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [try+]])
  (:import (java.util UUID)))

(def +xsd-polku+ "xsd/tloik/")

(defn laheta-kuittaus [itmf lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id onnistunut lisatietoja]
  (lokittaja :lahteva-jms-kuittaus kuittaus tapahtuma-id onnistunut lisatietoja kuittausjono)
  (jms/laheta itmf kuittausjono kuittaus {:correlation-id korrelaatio-id}))

(defn hae-urakka [db {:keys [urakkatyyppi sijainti]}]
  (let [ilmoituksen-urakkatyyppi (ilmoitus/urakkatyyppi urakkatyyppi)
        hae-urakka (fn [urakkatyyppi]
                     (when-let [urakka-id
                                (if (#{"teiden-hoito" "hoito"} urakkatyyppi)
                                  ;; Ilmoituksia vastaanottavalla hoidon urakalla pitäisi aina olla rivi alueurakka-taulussa.
                                  ;; Jos näin ei kuitenkaan jostain syystä ole, otetaan fallbackina lähin, viimeisenpä alkanut urakka
                                  (or (:id (first (urakat/hae-lahin-hoidon-alueurakka db (:x sijainti) (:y sijainti) 10000)))
                                    (urakkapalvelu/hae-lahin-urakka-id-sijainnilla db urakkatyyppi sijainti))
                                  (urakkapalvelu/hae-lahin-urakka-id-sijainnilla db urakkatyyppi sijainti))]
                       (first (urakat/hae-urakka db urakka-id))))
        urakka (hae-urakka ilmoituksen-urakkatyyppi)]

    ;; Jos varsinaiselle urakalle ei löydy yhtään päivystäjää, haetaan oletuksena hoidon urakka
    (if (yhteyshenkilot/onko-urakalla-paivystajia? db (:id urakka))
      urakka
      (hae-urakka "hoito"))))

(defn- merkitse-automaattisesti-vastaanotetuksi [db ilmoitus ilmoitus-id jms-lahettaja]
  (log/info "Ilmoittaja urakan organisaatiossa, merkitään automaattisesti vastaanotetuksi.")
  (let [ilmoitustoimenpide-id (:id (ilmoitukset-q/luo-ilmoitustoimenpide<!
                                     db {:ilmoitus ilmoitus-id
                                         :ilmoitusid (:ilmoitus-id ilmoitus)
                                         :kuitattu (c/to-date (t/now))
                                         :vakiofraasi nil
                                         :vapaateksti nil
                                         :kuittaustyyppi "vastaanotto"
                                         :suunta "sisaan"
                                         :kanava "harja"
                                         :tila nil
                                         :kuittaaja_henkilo_etunimi "Harja"
                                         :kuittaaja_henkilo_sukunimi "Automatiikka"
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
      (paivystajaviestit/laheta ilmoitusasetukset db (assoc ilmoitus :urakka-id urakka-id) paivystaja))))

(defn- ilmoituksen-kesto [sekunnit]
  (when sekunnit
    (let [tunnit (-> sekunnit (/ 3600) Math/floor int)
          minuutit (-> sekunnit (- (* tunnit 3600)) (/ 60) Math/floor int)
          sekunnit (-> sekunnit (- (* tunnit 3600) (* minuutit 60)) Math/floor int)]
      (str tunnit "h " minuutit "min " sekunnit "s"))))

(defn- laheta-slackiin-ilmoitus-hitaudesta

  [{:keys [kulunut-aika viesti-id tapahtuma-id kehitysmoodi?]}]
  (let [integraatio-log-params {:tapahtuma-id tapahtuma-id
                                :alkanut (pvm/pvm->iso-8601 (pvm/nyt-suomessa))
                                :valittu-jarjestelma "tloik"
                                :valittu-integraatio "ilmoituksen-kirjaus"}]
    (log/error {:fields [{:title "Linkit"
                          :value (str "<|||ilog" integraatio-log-params "ilog||||Harja integraatioloki> | "
                                      "<|||jira Ilmoitukset ovat hitaita jira||||JIRA> | "
                                      "<|||glogglog||||Graylog>")}]
                :tekstikentta (str "Ilmoitukset ovat hitaita! :snail: :envelope:|||"
                                   "Ilmoituksella, jonka viesti id on " viesti-id "|||"
                                   "Kesti *" (ilmoituksen-kesto kulunut-aika) "* saapua T-LOIK:ista HARJAA:n")})))

(defn- kasittele-ilmoituksessa-kulunut-aika
  [{:keys [valitetty vastaanotettu viesti-id tapahtuma-id kehitysmoodi? uudelleen-lahetys?]}]
  (try (let [kulunut-aika (pvm/aikavali-sekuntteina valitetty vastaanotettu)]
         ;; Jos ilmoituksen saapumisessa HARJA:an on kestänyt yli 5 min, lähetetään siitä viesti slackiin
         (when (and kulunut-aika (> kulunut-aika 300) (not uudelleen-lahetys?))
           (log/debug "SLACKIIN PITÄS LÄHTÄ VIESTIÄ")
           (laheta-slackiin-ilmoitus-hitaudesta
             {:kulunut-aika kulunut-aika :viesti-id viesti-id
              :tapahtuma-id tapahtuma-id :kehitysmoodi? kehitysmoodi?}))
         kulunut-aika)
       (catch Exception e
         (log/error e "Ilmoituksen saapumisen keston laskeminen epäonnistui")
         nil)))

(defn kasittele-ilmoitus
  "Tallentaa ilmoituksen ja tekee tarvittavat huomautus- ja ilmoitustoimenpiteet"
  [itmf ilmoitusasetukset lokittaja db kuittausjono urakka
   ilmoitus viesti-id korrelaatio-id tapahtuma-id jms-lahettaja kehitysmoodi?
   vastaanotettu]
  (let [ilmoitus (assoc ilmoitus
                   :urakkanimi (:nimi urakka)
                   :vastaanotettu vastaanotettu)
        urakka-id (:id urakka)
        ilmoitus-id (:ilmoitus-id ilmoitus)
        paivystajat (yhteyshenkilot/hae-urakan-tamanhetkiset-paivystajat db urakka-id)
        kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (time/now) "valitetty" urakka paivystajat nil)
        ilmoittaja-urakan-urakoitsijan-organisaatiossa? (kayttajat-q/onko-kayttaja-nimella-urakan-organisaatiossa?
                                                          db urakka-id ilmoitus)
        uudelleen-lahetys? (ilmoitukset-q/ilmoitus-loytyy-idlla? db ilmoitus-id)
        ilmoitus-on-lahetetty-urakalle? (ilmoitukset-q/ilmoitus-on-lahetetty-urakalle? db ilmoitus-id urakka-id)
        ilmoituksen-tyyppi-kannassa (:ilmoitustyyppi (first (ilmoitukset-q/hae-ilmoitus-ilmoitus-idlla db {:ilmoitusid ilmoitus-id})))
        ;; Jos ilmoitus muuttuu päivityksellä toimenpidepyynnöksi, lähetetään viestit uudelleen, että SMS:t saadaan lähtemään (HARJA-631)
        ilmoitus-muuttui-toimenpidepyynnoksi? (and ilmoituksen-tyyppi-kannassa
                                                (not= "toimenpidepyynto" ilmoituksen-tyyppi-kannassa)
                                                (= "toimenpidepyynto" (:ilmoitustyyppi ilmoitus)))
        {:keys [lisatietoja ilmoitus]}
        (jdbc/with-db-transaction [db db]
          (let [;; Jos kyseessä on harvinainen tilanne, että ilmoitus on lähetetty väärälle urakalle ensin, niin korjataan
                ;; ilmoitustauluun urakka-id oikeaksi tällä toisella kerralla
                _ (when (and uudelleen-lahetys? (not ilmoitus-on-lahetetty-urakalle?))
                    (ilmoitukset-q/paivita-ilmoituksen-urakka db ilmoitus-id urakka-id))
                ilmoitus-kanta-id (ilmoitus/tallenna-ilmoitus db urakka-id ilmoitus)
                ;; Kuluneen ajan laskennassa verrataan ajankohtaa, jolloin T-LOIK on lähettänyt ilmoituksen siihen milloin se on Harjassa vastaanotettu.
                kulunut-aika (kasittele-ilmoituksessa-kulunut-aika {:valitetty (:valitetty ilmoitus) :vastaanotettu vastaanotettu
                                                                    :viesti-id (:viesti-id ilmoitus) :tapahtuma-id tapahtuma-id
                                                                    :kehitysmoodi? kehitysmoodi? :uudelleen-lahetys? uudelleen-lahetys?})
                ilmoituksen-alkuperainen-kesto (when uudelleen-lahetys?
                                                 (->> ilmoitus-kanta-id (ilmoitukset-q/ilmoituksen-alkuperainen-kesto db) first :kesto))
                lisatietoja (if uudelleen-lahetys?
                              (str "Ilmoituksen päivityksen saapuminen kesti " (ilmoituksen-kesto kulunut-aika)
                                " - alkuperäisellä kestänyt: "
                                (if (< ilmoituksen-alkuperainen-kesto 1)
                                  "alle 1s"
                                  (ilmoituksen-kesto (Math/floor ilmoituksen-alkuperainen-kesto))))
                              (str "Illmoituksella kesti " (ilmoituksen-kesto kulunut-aika) " saapua HARJA:an"))
                ilmoitus (assoc ilmoitus :id ilmoitus-kanta-id)
                tieosoite (ilmoitus/hae-ilmoituksen-tieosoite db ilmoitus-kanta-id)]

            ;; Ilmoita kaikki ilmoitustapahtumat, ja vielä erikseen urakkakohtaisesti
            (notifikaatiot/ilmoita-saapuneesta-ilmoituksesta ilmoitus-id)
            (notifikaatiot/ilmoita-saapuneesta-ilmoituksesta urakka-id ilmoitus-id)
            (if ilmoittaja-urakan-urakoitsijan-organisaatiossa?
              (merkitse-automaattisesti-vastaanotetuksi db ilmoitus ilmoitus-kanta-id jms-lahettaja)
              ;; Tee tähän joku logiikka, että lähetetään uusiksi, mikäli päivystäjä on eri, kuin edellisellä kerralla
              ;; Voisko olla niin, että päivystäjille lähetetään, jos päivystäjä on eri kuin edellisellä kerralla
              ;; HARJA-631: Jos päivitetty ilmoitus on tyyppiä TPP, lähetetään aina viestit uudelleen
              (if (or (not uudelleen-lahetys?) (and uudelleen-lahetys? (not ilmoitus-on-lahetetty-urakalle?))
                    ilmoitus-muuttui-toimenpidepyynnoksi?)
                (laheta-ilmoitus-paivystajille db
                  (assoc ilmoitus :sijainti (merge (:sijainti ilmoitus) tieosoite))
                  paivystajat urakka-id ilmoitusasetukset)
                (log/warn "Päivitetty ilmoitus saapui. Ei lähetetä päivystäjille, koska samat päivystäjät ovat jo saaneet viestin.")))
            {:lisatietoja lisatietoja
             :ilmoitus ilmoitus}))]
    ;; Tallennus-transaktion jälkeen lähetetään Ack-viesti T-Loikiin. Se onnistuu lähes aina. Vaikka se epäonnistuisi,
    ;; tallennetaan ilmoitus Harjaan, ja päivystäjille on lähetetty viestit. Tällöin T-Loik lähettää aikanaan ilmoituksen
    ;; uudelleen, mikä Harjassa havaitaan päivitykseksi ja samasta ilmoituksesta ei enää lähde turhaan duplikaattina viestintää.
    ;; Ilmoituksen tila päivitetään normaaliin "kuittaamaton" aloitustilaan, jos ja kun T-Loikiin menevä Ack-viesti onnistuu
    ;; Muutoin ilmoitus jää harvinaiseen välitilaan "ei-valitetty", mikä tarkoittaa ettei T-Loikille ole lähetetty "valitetty" kuittausta (eli Ack) onnistuneesti
    (try
      (let [valitysviestin-id (laheta-kuittaus itmf lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id true lisatietoja)]
        (when valitysviestin-id
          (ilmoitukset-q/paivita-ilmoitus-valitetty! db {:id (:id ilmoitus)})))
      (catch Exception e
        (log/error (format "Ilmoituksen välityskuittausta (Ack) ei saatu välitettyä T-Loikiin. Tarkastele kokonaistilannetta,
      ja ole tarvittaessa yhteydessä T-Loikiin, heidän järjestelmässä voi olla ongelmatilanne päällä. Virhe: %s" (pr-str e)))))))

(defn kasittele-tuntematon-urakka [itmf lokittaja kuittausjono viesti-id ilmoitus-id
                                   korrelaatio-id tapahtuma-id]
  (let [virhe (format "Urakkaa ei voitu päätellä T-LOIK:n ilmoitukselle (id: %s, viesti id: %s)"
                      ilmoitus-id viesti-id)
        kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (.toString (time/now)) "virhe" nil
                                           nil "Tiedoilla ei voitu päätellä urakkaa.")]
    (log/error virhe)
    (laheta-kuittaus itmf lokittaja kuittausjono kuittaus
                     korrelaatio-id tapahtuma-id false virhe)))

(defn vastaanota-ilmoitus [itmf lokittaja ilmoitusasetukset db
                           ilmoitusviestijono kuittausjono jms-lahettaja kehitysmoodi? viesti]
  (log/debug "Vastaanotettiin T-LOIK:n ilmoitusjonosta viesti: " viesti)
  (let [vastaanotettu (pvm/nyt)
        jms-viesti-id (.getJMSMessageID viesti)
        viestin-sisalto (.getText viesti)
        korrelaatio-id (.getJMSCorrelationID viesti)
        tapahtuma-id (lokittaja :saapunut-jms-viesti jms-viesti-id viestin-sisalto ilmoitusviestijono)]

    (if (xml/validi-xml? +xsd-polku+ "harja-tloik.xsd" viestin-sisalto)
      (let [{:keys [viesti-id ilmoitus-id] :as ilmoitus} (ilmoitus-sanoma/lue-viesti viestin-sisalto)]
        (try+
          (if-let [urakka (hae-urakka db ilmoitus)]
            (kasittele-ilmoitus itmf ilmoitusasetukset lokittaja db kuittausjono urakka
                                ilmoitus viesti-id korrelaatio-id tapahtuma-id jms-lahettaja kehitysmoodi?
                                vastaanotettu)
            (kasittele-tuntematon-urakka itmf lokittaja kuittausjono viesti-id ilmoitus-id
                                         korrelaatio-id tapahtuma-id))
          (catch Exception e
            (log/error e (format "Tapahtui poikkeus luettaessa sisään ilmoitusta T-LOIK:sta"
                                 " (id: %s, viesti id: %s)" ilmoitus-id viesti-id))
            (let [virhe (str (format "Poikkeus (id: %s, viesti id: %s) " ilmoitus-id viesti-id) e)
                  kuittaus (kuittaus-sanoma/muodosta viesti-id ilmoitus-id (.toString (time/now))
                                                     "virhe" nil nil "Sisäinen käsittelyvirhe.")]
              (laheta-kuittaus itmf lokittaja kuittausjono kuittaus korrelaatio-id tapahtuma-id
                               false virhe)))))
      (let [virhe "XML-sanoma ei ole harja-tloik.xsd skeeman mukainen."
            viesti-id (str (UUID/randomUUID))
            kuittaus (kuittaus-sanoma/muodosta viesti-id nil (.toString (time/now)) "virhe" nil nil virhe)]
        (laheta-kuittaus itmf lokittaja kuittausjono kuittaus
                         korrelaatio-id tapahtuma-id false virhe)))))
