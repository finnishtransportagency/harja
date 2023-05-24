(ns harja.palvelin.integraatiot.turi.turi-komponentti
  "TURI:n (TUrvallisuuspoikkeamien ja RIskienhallinnan tietojärjestelmä)
   käyttöön tarkoitetut palvelut"
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.turvallisuuspoikkeamat :as q-turvallisuuspoikkeamat]
            [harja.kyselyt.urakan-tyotunnit :as q-urakan-tyotunnit]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.turi.sanomat.turvallisuuspoikkeama :as turvallisuuspoikkeamasanoma]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.kyselyt.urakan-tyotunnit :as q]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol TuriLahetys
  (laheta-turvallisuuspoikkeama [this id]))

(defn tee-lokittaja [this]
  (integraatioloki/lokittaja (:integraatioloki this) (:db this) "turi" "laheta-turvallisuuspoikkeama"))

(defn kasittele-turin-turvallisuuspoikkeamavastaus [db harja-turpo-id headers body]
  (q-turvallisuuspoikkeamat/lokita-lahetys<! db true harja-turpo-id)
  (try
    (let [turi-id (Integer. (re-find #"\d+" (str/replace body #"[\n]" "")))]
      (if (integer? turi-id)
        (q-turvallisuuspoikkeamat/tallenna-turvallisuuspoikkeaman-turi-id! db turi-id harja-turpo-id)
        (log/error "TURI:in lähettämälle turvallisuuspoikkeamalle ei saatu id:tä.")))
    (catch Exception e
      (log/error e "Poikkeus TURI:n palauttaman id:n parsinnassa"))))

(defn hae-liitteiden-sisallot [liitteiden-hallinta turvallisuuspoikkeama]
  (let [liitteet (:liitteet turvallisuuspoikkeama)]
    (mapv
      (fn [liite]
        (assoc liite
          :data
          (:data
            (liitteet/lataa-liite liitteiden-hallinta (:id liite)))))
      liitteet)))

(defn hae-liitteet [liitteiden-hallinta turvallisuuspoikkeama]
  (let [liitteet (concat (:liitteet turvallisuuspoikkeama)
                         (filter #(not (nil? (:id %))) (mapv :liite (:kommentit turvallisuuspoikkeama))))
        turvallisuuspoikkeama (assoc turvallisuuspoikkeama :liitteet liitteet)]
    (hae-liitteiden-sisallot liitteiden-hallinta turvallisuuspoikkeama)))

(defn hae-turvallisuuspoikkeama [liitteiden-hallinta db id]
  (let [turvallisuuspoikkeama (first (konv/sarakkeet-vektoriin
                                       (into []
                                             q-turvallisuuspoikkeamat/turvallisuuspoikkeama-xf
                                             (q-turvallisuuspoikkeamat/hae-turvallisuuspoikkeama-lahetettavaksi-turiin db id))
                                       {:korjaavatoimenpide :korjaavattoimenpiteet
                                        :liite :liitteet
                                        :kommentti :kommentit}))]
    (if turvallisuuspoikkeama
      (assoc turvallisuuspoikkeama :liitteet (hae-liitteet liitteiden-hallinta turvallisuuspoikkeama))
      (let [virhe (format "Id:llä %s ei löydy turvallisuuspoikkeamaa" id)]
        (log/error virhe)
        (throw+ {:type :tuntematon-turvallisuuspoikkeama
                 :error virhe})))))

(defn laheta-turvallisuuspoikkeama-turiin [{:keys [db integraatioloki liitteiden-hallinta
                                                   turvallisuuspoikkeamat-url kayttajatunnus salasana]} id]
  (when-not (empty? turvallisuuspoikkeamat-url)
    (log/debug (format "Lähetetään turvallisuuspoikkeama (id: %s) TURI:n" id))
    (try
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "turi" "laheta-turvallisuuspoikkeama" nil
        (fn [konteksti]
          (let [sanoma (->> id
                            (hae-turvallisuuspoikkeama liitteiden-hallinta db)
                            turvallisuuspoikkeamasanoma/muodosta)
                {body :body headers :headers}
                (integraatiotapahtuma/laheta
                  konteksti :http {:metodi :POST
                                   :url turvallisuuspoikkeamat-url
                                   :kayttajatunnus kayttajatunnus
                                   :salasana salasana
                                   :otsikot {"Content-Type" "text/xml"}}
                  sanoma)]
            (kasittele-turin-turvallisuuspoikkeamavastaus db id headers body)))
        {:virhekasittelija (fn [_ _]
                             (q-turvallisuuspoikkeamat/lokita-lahetys<! db false id))})
      (catch Throwable t
        (q-turvallisuuspoikkeamat/lokita-lahetys<! db false id)
        (log/error t (format "Turvallisuuspoikkeaman (id: %s) lähetyksessä TURI:n tapahtui poikkeus" id))))))

(defn laheta-turvallisuuspoikkeamat-turiin [this]
  (let [idt (q-turvallisuuspoikkeamat/hae-lahettamattomat-turvallisuuspoikkeamat (:db this))]
    (log/debug (format "Lähetetään %s turvallisuuspoikkeamaa TURI:n" (count idt)))
    (doseq [{id :id} idt]
      (laheta-turvallisuuspoikkeama this id))))

(defn tee-paivittainen-lahetys-tehtava [this paivittainen-lahetysaika]
  (if paivittainen-lahetysaika
    (do
      (log/debug "Ajastetaan turvallisuuspoikkeamien lähettäminen joka päivä kello: " paivittainen-lahetysaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-lahetysaika
        (do
          (log/info "ajasta-paivittain :: turvallisuuspoikkeamien lähettäminen :: Alkaa " (pvm/nyt))
          (fn [_]
            (lukot/yrita-ajaa-lukon-kanssa
              (:db this)
              "turi-paivittainen-lahetys"
              #(do
                 (laheta-turvallisuuspoikkeamat-turiin this)))))))
    (fn [])))

(defrecord Turi [asetukset]
  component/Lifecycle
  (start [this]
    (let [{turvallisuuspoikkeamat-url :turvallisuuspoikkeamat-url
           kayttajatunnus :kayttajatunnus
           salasana :salasana
           paivittainen-lahetysaika :paivittainen-lahetysaika} asetukset
          this (assoc this
                 :turvallisuuspoikkeamat-url turvallisuuspoikkeamat-url
                 :kayttajatunnus kayttajatunnus
                 :salasana salasana)]
      (log/debug (format "Käynnistetään TURI-komponentti (URL: %s)" turvallisuuspoikkeamat-url))
      (assoc
        this
        :paivittainen-lahetys-tehtava (tee-paivittainen-lahetys-tehtava this paivittainen-lahetysaika))))

  (stop [this]
    ((:paivittainen-lahetys-tehtava this))
    this)

  TuriLahetys
  (laheta-turvallisuuspoikkeama [this id]
    (laheta-turvallisuuspoikkeama-turiin this id)))

