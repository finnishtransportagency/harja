(ns harja.palvelin.integraatiot.palautejarjestelma.palautejarjestelma-komponentti
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z]
            [taoensso.timbre :as log]
            [harja.domain.palautejarjestelma-domain :as palautejarjestelma]
            [harja.kyselyt.palautejarjestelma :as palautejarjestelma-q]
            [clojure.java.jdbc :as jdbc]))

(defn- lue-aihe [aihe-xml]
  {::palautejarjestelma/ulkoinen-id (z/xml1-> aihe-xml :id z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/nimi (z/xml1-> aihe-xml :name z/text)
   ::palautejarjestelma/kaytossa? (= "1" (z/xml1-> aihe-xml :active z/text))
   ::palautejarjestelma/jarjestys (z/xml1-> aihe-xml :order z/text xml/parsi-kokonaisluku)})

(defn- lue-aiheet [aiheet-xml]
  (z/xml-> aiheet-xml :subject lue-aihe))


;; TODO: Lisää virheenkäsittelyä
(defn hae-aiheet-palautejarjestelmasta [{:keys [db integraatioloki url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautejarjestelma" "hae-aiheet" ;; TODO: Tee integraatio tälle
    (fn [konteksti]
      (println "jere testaa::" konteksti)
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (lue-aiheet (xml/lue body))))))

(defn- lue-tarkenne [tarkenne-xml]
  {::palautejarjestelma/ulkoinen-id (z/xml1-> tarkenne-xml :id z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/nimi (z/xml1-> tarkenne-xml :name z/text)
   ::palautejarjestelma/kaytossa? (= "1" (z/xml1-> tarkenne-xml :active z/text))
   ::palautejarjestelma/jarjestys (z/xml1-> tarkenne-xml :order z/text xml/parsi-kokonaisluku)
   ::palautejarjestelma/aihe_id (z/xml1-> tarkenne-xml :subject_id z/text xml/parsi-kokonaisluku)})

(defn- lue-tarkenteet [tarkenteet-xml]
  (z/xml-> tarkenteet-xml :subsubject lue-tarkenne))

;; TODO: Lisää virheenkäsittelyä
(defn hae-tarkenteet-palautejarjestelmasta [{:keys [db integraatioloki url kayttajatunnus salasana]}]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki
    "palautejarjestelma" "hae-tarkenteet" ;; TODO: Tee integraatio tälle
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET
                            :url (str url "/api/x_sgtk_open311/v1/publicws/subsubjects?locale=fi")
                            :kayttajatunnus kayttajatunnus
                            :salasana salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]

        (lue-tarkenteet (xml/lue body))))))

;; TODO: Ajasta päivittäin
(defn- paivita-aiheet-ja-tarkenteet! [{:keys [db] :as asetukset}]
  (let [aiheet (hae-aiheet-palautejarjestelmasta asetukset)
        tarkenteet (hae-tarkenteet-palautejarjestelmasta asetukset)]
    (jdbc/with-db-transaction [db db]
      (palautejarjestelma-q/lisaa-tai-paivita-aiheet db aiheet)
      (palautejarjestelma-q/lisaa-tai-paivita-tarkenteet db tarkenteet))))

;; TODO: Tee rajapinta fronttia varten
(defn hae-aiheet-ja-tarkenteet [{:keys [db]}]
  (palautejarjestelma-q/hae-aiheet-ja-tarkenteet db))

(defrecord Palautejarjestelma [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this))
