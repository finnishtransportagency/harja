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
            [harja.palvelin.integraatiot.turi.sanomat.tyotunnit :as tyotunnit-sanoma]
            [harja.palvelin.komponentit.liitteet :as liitteet]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str]
            [harja.domain.urakan-tyotunnit :as urakan-tyotunnit]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [harja.kyselyt.urakan-tyotunnit :as q])
  (:use [slingshot.slingshot :only [throw+]]))

(defprotocol TuriLahetys
  (laheta-turvallisuuspoikkeama [this id])
  (laheta-urakan-vuosikolmanneksen-tyotunnit [this urakka-id vuosi vuosikolmannes]))

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

(defn laheta-urakan-vuosikolmanneksen-tyotunnit-turiin [{:keys [db integraatioloki urakan-tyotunnit-url
                                                                kayttajatunnus salasana]}
                                                        urakka-id vuosi vuosikolmannes]
  (when-not (empty? urakan-tyotunnit-url)
    (let [lokita-lahetys (fn [onnistunut?]
                           (q-urakan-tyotunnit/lokita-lahetys db
                                                              urakka-id
                                                              vuosi
                                                              vuosikolmannes
                                                              onnistunut?))]
      (log/debug (format "Lähetetään urakan (urakka-id: %s) vuoden %s kolmanneksen %s työtunnit TURI:n"
                         urakka-id
                         vuosi
                         vuosikolmannes))
      (try

        (integraatiotapahtuma/suorita-integraatio
          db integraatioloki "turi" "urakan-tyotunnit" nil
          (fn [konteksti]
            (let [urakka (first (q-urakan-tyotunnit/hae-urakan-tiedot-lahettavaksi-tyotuntien-kanssa db {:urakkaid urakka-id}))
                  tyotunnit (q-urakan-tyotunnit/hae-urakan-vuosikolmanneksen-tyotunnit
                              db
                              urakka-id
                              vuosi
                              vuosikolmannes)
                  sanoma (tyotunnit-sanoma/muodosta urakka vuosi vuosikolmannes tyotunnit)
                  {body :body} (integraatiotapahtuma/laheta
                                 konteksti
                                 :http
                                 {:metodi :POST
                                  :url urakan-tyotunnit-url
                                  :kayttajatunnus kayttajatunnus
                                  :salasana salasana
                                  :otsikot {"Content-Type" "text/xml"}}
                                 sanoma)]
              (log/debug (format "Urakan (urakka-id: %s) vuoden %s kolmanneksen %s työtunnit lähetetty onnistuneesti TURI:n. TURI:n vastaus: %s."
                                 urakka-id
                                 vuosi
                                 vuosikolmannes
                                 body))
              (lokita-lahetys true)))
          {:virhekasittelija (fn [_ _]
                               (log/error (format "Urakan (urakka-id: %s) vuoden %s kolmanneksen %s työtuntien lähetys TURI:n epäonnistui"
                                                  urakka-id
                                                  vuosi
                                                  vuosikolmannes))
                               (lokita-lahetys false))})
        (catch Throwable t
          (lokita-lahetys false)
          (log/error (format "Urakan (urakka-id: %s) vuoden %s kolmanneksen %s työtuntien lähetys TURI:n epäonnistui"
                             urakka-id
                             vuosi
                             vuosikolmannes)))))))

(defn laheta-tyotunnit-turin [this]
  (let [lahettamattomat (q-urakan-tyotunnit/hae-lahettamattomat-tai-epaonnistuneet-tyotunnit (:db this))]
    (log/debug (format "Lähetetään %s vuosikolmanneksen työtunteja TURI:n" (count lahettamattomat)))
    (doseq [{urakka ::urakan-tyotunnit/urakka
             vuosi ::urakan-tyotunnit/vuosi
             vuosikolmannes ::urakan-tyotunnit/vuosikolmannes}
            lahettamattomat]
      (laheta-urakan-vuosikolmanneksen-tyotunnit-turiin this urakka vuosi vuosikolmannes))))

(defn tee-paivittainen-lahetys-tehtava [this paivittainen-lahetysaika]
  (if paivittainen-lahetysaika
    (do
      (log/debug "Ajastetaan turvallisuuspoikkeamien ja työtuntien lähettäminen joka päivä kello: " paivittainen-lahetysaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-lahetysaika
        (fn [_]
          (lukot/yrita-ajaa-lukon-kanssa
            (:db this)
            "turi-paivittainen-lahetys"
            #(do
               (laheta-turvallisuuspoikkeamat-turiin this)
               (laheta-tyotunnit-turin this))))))
    (fn [])))

(defrecord Turi [asetukset]
  component/Lifecycle
  (start [this]
    (let [{turvallisuuspoikkeamat-url :turvallisuuspoikkeamat-url
           urakan-tyotunnit-url :urakan-tyotunnit-url
           kayttajatunnus :kayttajatunnus
           salasana :salasana
           paivittainen-lahetysaika :paivittainen-lahetysaika} asetukset
          this (assoc this
                 :turvallisuuspoikkeamat-url turvallisuuspoikkeamat-url
                 :urakan-tyotunnit-url urakan-tyotunnit-url
                 :kayttajatunnus kayttajatunnus
                 :salasana salasana)]
      (log/debug (format "Käynnistetään TURI-komponentti (URL: %s)" turvallisuuspoikkeamat-url))
      (assoc
        this
        :paivittainen-lahetys-tehtava (tee-paivittainen-lahetys-tehtava this paivittainen-lahetysaika))))

  (stop [this]
    (:paivittainen-lahetys-tehtava this)
    this)

  TuriLahetys
  (laheta-turvallisuuspoikkeama [this id]
    (laheta-turvallisuuspoikkeama-turiin this id))
  (laheta-urakan-vuosikolmanneksen-tyotunnit [this urakka-id vuosi vuosikolmannes]
    (laheta-urakan-vuosikolmanneksen-tyotunnit-turiin this urakka-id vuosi vuosikolmannes)))

