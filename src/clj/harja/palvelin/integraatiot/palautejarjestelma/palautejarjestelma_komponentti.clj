(ns harja.palvelin.integraatiot.palautejarjestelma.palautejarjestelma-komponentti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [harja.domain.palautejarjestelma-domain :as palautejarjestelma]
            [harja.kyselyt.palautejarjestelma :as palautejarjestelma-q]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]))

(defn- lue-aihe [aihe-xml]
  {::palautejarjestelma/aihe-id (z/xml1-> aihe-xml :id z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/nimi (z/xml1-> aihe-xml :name z/text)
   ::palautejarjestelma/kaytossa? (= "1" (z/xml1-> aihe-xml :active z/text))
   ::palautejarjestelma/jarjestys (z/xml1-> aihe-xml :order z/text xml/parsi-kokonaisluku)})

(defn- lue-aiheet [aiheet-xml]
  (z/xml-> aiheet-xml :subject lue-aihe))

;; TODO: Lisää virheenkäsittelyä
(defn hae-aiheet-palautejarjestelmasta [db integraatioloki {:keys [url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautejarjestelma" "hae-aiheet"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (lue-aiheet (xml/lue body))))))

(defn- lue-tarkenne [tarkenne-xml]
  {::palautejarjestelma/tarkenne-id (z/xml1-> tarkenne-xml :id z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/nimi (z/xml1-> tarkenne-xml :name z/text)
   ::palautejarjestelma/kaytossa? (= "1" (z/xml1-> tarkenne-xml :active z/text))
   ::palautejarjestelma/jarjestys (z/xml1-> tarkenne-xml :order z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/aihe-id (z/xml1-> tarkenne-xml :subject_id z/text xml/parsi-kokonaisluku)})

(defn- lue-tarkenteet [tarkenteet-xml]
  (z/xml-> tarkenteet-xml :subsubject lue-tarkenne))

;; TODO: Lisää virheenkäsittelyä
(defn hae-tarkenteet-palautejarjestelmasta [db integraatioloki {:keys [url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautejarjestelma" "hae-tarkenteet"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subsubjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]

        (lue-tarkenteet (xml/lue body))))))

(defn- paivita-aiheet-ja-tarkenteet! [db integraatioloki asetukset]
  (let [aiheet (hae-aiheet-palautejarjestelmasta db integraatioloki asetukset)
        tarkenteet (hae-tarkenteet-palautejarjestelmasta db integraatioloki asetukset)]
    (jdbc/with-db-transaction [db db]
      (palautejarjestelma-q/lisaa-tai-paivita-aiheet db aiheet)
      (palautejarjestelma-q/lisaa-tai-paivita-tarkenteet db tarkenteet))))

(defn- aiheet-ja-tarkenteet-paivitystehtava [db integraatioloki {:keys [paivitysaika url kayttajatunnus salasana] :as asetukset}]
  (when (and paivitysaika url kayttajatunnus salasana)
    (ajastettu-tehtava/ajasta-paivittain
      paivitysaika
      (fn [_]
        (paivita-aiheet-ja-tarkenteet! db integraatioloki asetukset)))))

(defrecord Palautejarjestelma [asetukset]
  component/Lifecycle
  (start [{:keys [db integraatioloki] :as this}]
    (assoc this :paivittainen-haku (aiheet-ja-tarkenteet-paivitystehtava db integraatioloki asetukset)))
  (stop [{:keys [paivittainen-haku] :as this}]
    (when (fn? paivittainen-haku)
      (paivittainen-haku))
    this))
