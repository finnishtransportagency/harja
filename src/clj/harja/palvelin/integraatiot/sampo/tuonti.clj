(ns harja.palvelin.integraatiot.sampo.tuonti
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.jms :as jms]
            [harja.palvelin.integraatiot.sampo.sanomat.sampo-sanoma :as sampo-sanoma]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sampoon-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.kasittely.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet :as toimenpiteet]
            [harja.palvelin.integraatiot.sampo.kasittely.organisaatiot :as organisaatiot]
            [harja.palvelin.integraatiot.sampo.kasittely.yhteyshenkilot :as yhteyshenkilot]
            [harja.palvelin.integraatiot.sampo.kasittely.valitavoitteet :as valitavoitteet]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.tyokalut.lukot :as lukot])
  (:use [slingshot.slingshot :only [try+ throw+]]))


(defn tuo-sampo-viestin-data [db data]
  (let [hankkeet (:hankkeet data)
        urakat (:urakat data)
        sopimukset (:sopimukset data)
        toimenpiteet (:toimenpideinstanssit data)
        organisaatiot (:organisaatiot data)
        yhteyshenkilot (:yhteyshenkilot data)
        kuittaukset (doall
                      (concat
                        (hankkeet/kasittele-hankkeet db hankkeet)
                        (urakat/kasittele-urakat db urakat)
                        (sopimukset/kasittele-sopimukset db sopimukset)
                        (toimenpiteet/kasittele-toimenpiteet db toimenpiteet)
                        (organisaatiot/kasittele-organisaatiot db organisaatiot)
                        (yhteyshenkilot/kasittele-yhteyshenkilot db yhteyshenkilot)))]
    kuittaukset))

(defn- kasittele-sisaanluku [db viestin-sisalto]
  (jdbc/with-db-transaction [db db]
    (let [data (sampo-sanoma/lue-viesti viestin-sisalto)
          kuittaukset (tuo-sampo-viestin-data db data)]
      kuittaukset)))

(defn- kasittele-api-sisaanluku [db viestin-sisalto]
  (jdbc/with-db-transaction [db db]
    (let [data (sampo-sanoma/lue-api-viesti viestin-sisalto)
          kuittaukset (tuo-sampo-viestin-data db data)]
      kuittaukset)))


(defn kasittele-api-viesti [db integraatioloki viesti tapahtuma-id]
  (log/debug "Vastaanotettiin Sampon viesti api:sta:" viesti)
  (let [viestin-sisalto (first (:content (first viesti)))
        viesti-id (get-in viestin-sisalto [:attrs :messageId])
        viestityyppi (:tag viestin-sisalto)]

    (try+
      (let [kuittaukset (kasittele-api-sisaanluku db viesti)]
        (first kuittaukset))
      (catch [:type virheet/+poikkeus-samposisaanluvussa+] {:keys [virheet kuittaus ei-kriittinen?]}
        (do
          (log/error "Sampo sisään luvussa tapahtui poikkeus: " virheet)
          ;; Muodosta virheviesti välitettäväksi Sampoon vastauksena REST-API kutsuun
          (kuittaus-sampoon-sanoma/tee-xml-sanoma
            (kuittaus-sampoon-sanoma/muodosta-viesti viesti-id viestityyppi "CUSTOM" virheet))))

      (catch Exception e
        (log/error e "Sampo sisäänluvussa tapahtui poikkeus." e)
        (integraatioloki/kirjaa-epaonnistunut-integraatio
          integraatioloki
          "Sampo sisäänluvussa tapahtui poikkeus"
          (str "Poikkeus: " e)
          tapahtuma-id
          nil)
        ;; Muodosta virheviesti välitettäväksi Sampoon vastauksena REST-API kutsuun
        (kuittaus-sampoon-sanoma/tee-xml-sanoma
          (kuittaus-sampoon-sanoma/muodosta-viesti viesti-id viestityyppi "CUSTOM" "Vakava virhe. Käsittely ei onnistunut"))))))
