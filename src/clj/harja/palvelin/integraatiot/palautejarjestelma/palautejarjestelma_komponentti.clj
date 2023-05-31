(ns harja.palvelin.integraatiot.palautejarjestelma.palautejarjestelma-komponentti
  (:require [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [throw+]]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [harja.domain.palautejarjestelma-domain :as palautejarjestelma]
            [harja.kyselyt.palautejarjestelma :as palautejarjestelma-q]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

;; TODO: Pohdi nimeämistä.
;;       Palautejarjestelma -> palautevayla?
;;       nimi -> selite tms?
;;
;; TODO: Korjaa cypress-testit
;;       Tee testi, jossa ei ole aihetta tai tarkennetta, mutta on selitteitä.
;;       Tee testi, jossa ei ole selitettä ja on aihe/tarkenne.

;; TODO: Pitäisikö ilmoitustaulussa olla relaatio aihe/tarkenne-tauluun?

(defprotocol PalautejarjestelmaHaku
  (hae-aiheet [this])
  (hae-tarkenteet [this])
  (paivita-aiheet-ja-tarkenteet [this]))

(def +xsd-polku+ "xsd/palautejarjestelma/")

(defn- lue-aihe [aihe-xml]
  {::palautejarjestelma/aihe-id (z/xml1-> aihe-xml :id z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/nimi (z/xml1-> aihe-xml :name z/text)
   ::palautejarjestelma/kaytossa? (= "1" (z/xml1-> aihe-xml :active z/text))
   ::palautejarjestelma/jarjestys (z/xml1-> aihe-xml :order z/text xml/parsi-kokonaisluku)})

(defn- lue-aiheet [aiheet-xml]
  (z/xml-> aiheet-xml :subject lue-aihe))

(defn hae-aiheet-palautejarjestelmasta [db integraatioloki {:keys [url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautejarjestelma" "hae-aiheet"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (if (xml/validi-xml? +xsd-polku+ "aiheet.xsd" body)
          (lue-aiheet (xml/lue body))
          (throw+ {:type virheet/+invalidi-xml+
                   :virheet [{:koodi :invalidi-palautejarjestelma-xml
                              :viesti "Palautejärjestelmästä saadut aiheet eivät vastanneet odotettua skeemaa"}]}))))))

(defn- lue-tarkenne [tarkenne-xml]
  {::palautejarjestelma/tarkenne-id (z/xml1-> tarkenne-xml :id z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/nimi (z/xml1-> tarkenne-xml :name z/text)
   ::palautejarjestelma/kaytossa? (= "1" (z/xml1-> tarkenne-xml :active z/text))
   ::palautejarjestelma/jarjestys (z/xml1-> tarkenne-xml :order z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/aihe-id (z/xml1-> tarkenne-xml :subject_id z/text xml/parsi-kokonaisluku)})

(defn- lue-tarkenteet [tarkenteet-xml]
  (z/xml-> tarkenteet-xml :subsubject lue-tarkenne))

(defn hae-tarkenteet-palautejarjestelmasta [db integraatioloki {:keys [url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautejarjestelma" "hae-tarkenteet"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subsubjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (if (xml/validi-xml? +xsd-polku+ "tarkenteet.xsd" body)
          (lue-tarkenteet (xml/lue body))
          (throw+ {:type virheet/+invalidi-xml+
                   :virheet [{:koodi :invalidi-palautejarjestelma-xml
                              :viesti "Palautejärjestelmästä saadut tarkenteet eivät vastanneet odotettua skeemaa"}]}))))))

(defn- paivita-aiheet-ja-tarkenteet-palautejarjestelmasta [db integraatioloki asetukset]
  (let [aiheet (hae-aiheet-palautejarjestelmasta db integraatioloki asetukset)
        tarkenteet (hae-tarkenteet-palautejarjestelmasta db integraatioloki asetukset)]
    (jdbc/with-db-transaction [db db]
      (palautejarjestelma-q/lisaa-tai-paivita-aiheet db aiheet)
      (palautejarjestelma-q/lisaa-tai-paivita-tarkenteet db tarkenteet))

    (palautejarjestelma-q/hae-aiheet-ja-tarkenteet db)))

(defn- aiheet-ja-tarkenteet-paivitystehtava [db integraatioloki {:keys [paivitysaika url kayttajatunnus salasana] :as asetukset}]
  (when (and paivitysaika url kayttajatunnus salasana)
    (ajastettu-tehtava/ajasta-paivittain
      paivitysaika
      (fn [_]
        (paivita-aiheet-ja-tarkenteet-palautejarjestelmasta db integraatioloki asetukset)))))

(defrecord Palautejarjestelma [asetukset]
  component/Lifecycle
  (start [{:keys [db integraatioloki] :as this}]
    (assoc this :paivittainen-haku (aiheet-ja-tarkenteet-paivitystehtava db integraatioloki asetukset)))
  (stop [{:keys [paivittainen-haku] :as this}]
    (when (fn? paivittainen-haku)
      (paivittainen-haku))
    this)

  PalautejarjestelmaHaku
  (hae-aiheet [{:keys [db integraatioloki]}]
    (hae-aiheet-palautejarjestelmasta db integraatioloki asetukset))
  (hae-tarkenteet [{:keys [db integraatioloki]}]
    (hae-tarkenteet-palautejarjestelmasta db integraatioloki asetukset))
  (paivita-aiheet-ja-tarkenteet [{:keys [db integraatioloki]}]
    (paivita-aiheet-ja-tarkenteet-palautejarjestelmasta db integraatioloki asetukset)))
