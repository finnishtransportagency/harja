(ns harja.palvelin.integraatiot.palautevayla.palautevayla-komponentti
  (:require [com.stuartsierra.component :as component]
            [slingshot.slingshot :refer [throw+]]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [harja.domain.palautevayla-domain :as palautevayla]
            [harja.kyselyt.palautevayla :as palautevayla-q]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

;; TODO: Korjaa cypress-testit
;;       Tee testi, jossa ei ole aihetta tai tarkennetta, mutta on selitteitä.
;;       Tee testi, jossa ei ole selitettä ja on aihe/tarkenne.

(defprotocol PalautevaylaHaku
  (hae-aiheet [this])
  (hae-tarkenteet [this])
  (paivita-aiheet-ja-tarkenteet [this]))

(def +xsd-polku+ "xsd/palautevayla/")

(defn- lue-aihe [aihe-xml]
  {::palautevayla/aihe-id (z/xml1-> aihe-xml :id z/text xml/parsi-kokonaisluku)
   ::palautevayla/nimi (z/xml1-> aihe-xml :name z/text)
   ::palautevayla/kaytossa? (= "1" (z/xml1-> aihe-xml :active z/text))
   ::palautevayla/jarjestys (z/xml1-> aihe-xml :order z/text xml/parsi-kokonaisluku)})

(defn- lue-aiheet [aiheet-xml]
  (z/xml-> aiheet-xml :subject lue-aihe))

(defn hae-aiheet-palautevaylasta [db integraatioloki {:keys [url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautevayla" "hae-aiheet"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (if (xml/validi-xml? +xsd-polku+ "aiheet.xsd" body)
          (lue-aiheet (xml/lue body))
          (throw+ {:type virheet/+invalidi-xml+
                   :virheet [{:koodi :invalidi-palautevayla-xml
                              :viesti "Palautejärjestelmästä saadut aiheet eivät vastanneet odotettua skeemaa"}]}))))))

(defn- lue-tarkenne [tarkenne-xml]
  {::palautevayla/tarkenne-id (z/xml1-> tarkenne-xml :id z/text xml/parsi-kokonaisluku)
   ::palautevayla/nimi (z/xml1-> tarkenne-xml :name z/text)
   ::palautevayla/kaytossa? (= "1" (z/xml1-> tarkenne-xml :active z/text))
   ::palautevayla/jarjestys (z/xml1-> tarkenne-xml :order z/text xml/parsi-kokonaisluku)
   ::palautevayla/aihe-id (z/xml1-> tarkenne-xml :subject_id z/text xml/parsi-kokonaisluku)})

(defn- lue-tarkenteet [tarkenteet-xml]
  (z/xml-> tarkenteet-xml :subsubject lue-tarkenne))

(defn hae-tarkenteet-palautevaylasta [db integraatioloki {:keys [url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautevayla" "hae-tarkenteet"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subsubjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (if (xml/validi-xml? +xsd-polku+ "tarkenteet.xsd" body)
          (lue-tarkenteet (xml/lue body))
          (throw+ {:type virheet/+invalidi-xml+
                   :virheet [{:koodi :invalidi-palautevayla-xml
                              :viesti "Palautejärjestelmästä saadut tarkenteet eivät vastanneet odotettua skeemaa"}]}))))))

(defn- paivita-aiheet-ja-tarkenteet-palautevaylasta [db integraatioloki asetukset]
  (let [aiheet (hae-aiheet-palautevaylasta db integraatioloki asetukset)
        tarkenteet (hae-tarkenteet-palautevaylasta db integraatioloki asetukset)]
    (jdbc/with-db-transaction [db db]
      (palautevayla-q/lisaa-tai-paivita-aiheet db aiheet)
      (palautevayla-q/lisaa-tai-paivita-tarkenteet db tarkenteet))

    (palautevayla-q/hae-aiheet-ja-tarkenteet db)))

(defn- aiheet-ja-tarkenteet-paivitystehtava [db integraatioloki {:keys [paivitysaika url kayttajatunnus salasana] :as asetukset}]
  (when (and paivitysaika url kayttajatunnus salasana)
    (ajastettu-tehtava/ajasta-paivittain
      paivitysaika
      (fn [_]
        (paivita-aiheet-ja-tarkenteet-palautevaylasta db integraatioloki asetukset)))))

(defrecord Palautevayla [asetukset]
  component/Lifecycle
  (start [{:keys [db integraatioloki] :as this}]
    (assoc this :paivittainen-haku (aiheet-ja-tarkenteet-paivitystehtava db integraatioloki asetukset)))
  (stop [{:keys [paivittainen-haku] :as this}]
    (when (fn? paivittainen-haku)
      (paivittainen-haku))
    this)

  PalautevaylaHaku
  (hae-aiheet [{:keys [db integraatioloki]}]
    (hae-aiheet-palautevaylasta db integraatioloki asetukset))
  (hae-tarkenteet [{:keys [db integraatioloki]}]
    (hae-tarkenteet-palautevaylasta db integraatioloki asetukset))
  (paivita-aiheet-ja-tarkenteet [{:keys [db integraatioloki]}]
    (paivita-aiheet-ja-tarkenteet-palautevaylasta db integraatioloki asetukset)))
