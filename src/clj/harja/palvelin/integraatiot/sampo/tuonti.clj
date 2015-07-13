(ns harja.palvelin.integraatiot.sampo.tuonti
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sanomat.sampo-sanoma :as sampo-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet :as toimenpiteet]
            [harja.palvelin.integraatiot.sampo.kasittely.organisaatiot :as organisaatiot]
            [harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.tyokalut.xml :as xml]
            [clojure.data.zip.xml :as z])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn onko-kuittaus-positiivinen? [kuittaus]
  (= "NA"
     (first (z/xml-> (xml/lue kuittaus)
                     (fn [kuittaus] (z/xml1-> (z/xml1-> kuittaus) :Ack (z/attr :ErrorCode)))))))

(defn tee-lokiviesti [suunta sisalto otsikko]
  {:suunta        suunta
   :sisaltotyyppi "application/xml"
   :siirtotyyppi  "JMS"
   :sisalto       sisalto
   :otsikko       (str otsikko)
   :parametrit    nil})

(defn lokita-viesti [integraatioloki viesti]
  ;; todo: mieti mitä headeritason tietoja tarvii tallentaa. muista lisätä niille toteutus feikki-sonjaan.
  (let [viesti-id (.getJMSMessageID viesti)
        otsikko {:message-id viesti-id}
        lokiviesti (tee-lokiviesti "sisään" (.getText viesti) otsikko)]
    (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "sampo" "sisaanluku" nil lokiviesti)))

(defn lokita-kuittaus [integraatioloki kuittaus tapahtuma-id lisatietoja]
  (let [onnistunut (onko-kuittaus-positiivinen? kuittaus)
        lokiviesti (tee-lokiviesti "ulos" kuittaus nil)]
    (if onnistunut
      (integraatioloki/kirjaa-onnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil)
      (integraatioloki/kirjaa-epaonnistunut-integraatio integraatioloki lokiviesti lisatietoja tapahtuma-id nil))))

(defn laheta-kuittaus [sonja integraatioloki kuittausjono kuittaus tapahtuma-id lisatietoja]
  (lokita-kuittaus integraatioloki kuittaus tapahtuma-id lisatietoja)
  (sonja/laheta sonja kuittausjono kuittaus))

(defn kasittele-viesti [sonja integraatioloki db kuittausjono viesti]
  (log/debug "Vastaanotettiin Sampon viestijonosta viesti: " viesti)
  (let [tapahtuma-id (lokita-viesti integraatioloki viesti)]
    (try+
      (jdbc/with-db-transaction [transaktio db]
        (let [data (sampo-sanoma/lue-viesti (.getText viesti))
              hankkeet (:hankkeet data)
              urakat (:urakat data)
              sopimukset (:sopimukset data)
              toimenpiteet (:toimenpideinstanssit data)
              organisaatiot (:organisaatiot data)
              yhteyshenkilot (:yhteyshenkilot data)
              kuittaukset (concat (hankkeet/kasittele-hankkeet transaktio hankkeet)
                                  (urakat/kasittele-urakat transaktio urakat)
                                  (sopimukset/kasittele-sopimukset transaktio sopimukset)
                                  (toimenpiteet/kasittele-toimenpiteet transaktio toimenpiteet)
                                  (organisaatiot/kasittele-organisaatiot transaktio organisaatiot)
                                  (yhteyshenkilot/kasittele-yhteyshenkilot transaktio yhteyshenkilot))]
          (doseq [kuittaus kuittaukset]
            (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id nil))))
      (catch [:type virheet/+poikkeus-samposisaanluvussa+] {:keys [virheet kuittaus]}
        (log/debug "NAPATTIIMPAS POIKKEUS!!!!" virheet kuittaus)
        (laheta-kuittaus sonja integraatioloki kuittausjono kuittaus tapahtuma-id (str virheet)))
      (catch Exception e
        (log/error e "Tapahtui poikkeus luettaessa sisään viestiä Samposta.")))))