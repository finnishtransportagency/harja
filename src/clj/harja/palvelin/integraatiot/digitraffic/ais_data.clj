(ns harja.palvelin.integraatiot.digitraffic.ais-data
  "https://meri.digitraffic.fi/api/v1/metadata/documentation/swagger-ui.html#!/vessel45location45controller/vesselLocationsByTimestampUsingGET

  Haetaan ajastetusti digitrafficin rajapinnasta alusten nykyiset sijainnit, ja tallennetaan kantaan.
  Kannasta saamme reittihistorian, jota digitraffic ei tarjoa."

  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [specql.core :as specql]
            [jeesql.core :refer [defqueries]]
            [clj-time.coerce :refer [from-long to-sql-date]]
            [clojure.java.jdbc :as jdbc]
            [clojure.core.async :refer [go <! >! go-loop timeout close! chan]]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.tyokalut.lukot :as lukko]
            [harja.geo :as geo]

            [harja.domain.vesivaylat.alus :as alus]
            [harja.domain.muokkaustiedot :as m]))

(defqueries "harja/kyselyt/vesivaylat/ais-data.sql")

(defn- kasittele-vastaus! [{:keys [db konteksti]} halutut viesti]
  (let [tulos (filter (comp halutut :mmsi) (:features viesti))]
    (jdbc/with-db-transaction [db db]
      (doseq [laiva tulos]
        (assert (:mmsi laiva) (str "Laivalta puuttuu :mmsi tieto! " (pr-str laiva)))
        (assert (get-in laiva [:properties :timestampExternal]) (str "Laivalta puuttuu :timestampExternal! " (pr-str laiva)))
        (assert (:geometry laiva) (str "Laivalta puuttuu :geometry! " (pr-str laiva)))
        (lisaa-alukselle-reittipiste<! db
                                       {:mmsi (:mmsi laiva)
                                        :aika (-> laiva
                                                  ;; Location record timestamp in milliseconds from Unix epoch.
                                                  (get-in [:properties :timestampExternal])
                                                  from-long
                                                  to-sql-date)
                                        :sijainti (-> laiva
                                                      :geometry
                                                      (update :type (comp keyword str/lower-case))
                                                      ;; Geometrian koordinaatit ovat wgs84 muodossa, [lon lat] vektorissa
                                                      (update :coordinates (fn [[lon lat]]
                                                                             (let [{:keys [x y]} (geo/wgs84->euref {:x lon :y lat})]
                                                                               [x y])))
                                                      geo/clj->pg
                                                      geo/geometry)})))
    (let [loki (str "Tallennettiin " (count tulos) "/" (count halutut) " laivan sijainnit.")]
      (when konteksti (integraatiotapahtuma/lisaa-tietoja konteksti loki))
      (log/debug loki))))

(defn- tee-haku! [deps url alukset konteksti]
  (let [{body :body} (integraatiotapahtuma/laheta
                       konteksti
                       :http
                       {:metodi :GET
                        :url url
                        ;; Ei haluta tallentaa koko payloadia, koska se on niin iso
                        ;; teoriassa voitaisiin tallentaa vain saatujen alusten sijainnit,
                        ;; mutta sekin voi olla aika paljon dataa, koska haku tehdään niin usein
                        :response->loki #(str "Saatiin " (count (filter (comp alukset :mmsi) (-> % (cheshire/parse-string true) :features)))
                                             "/"
                                             (count alukset)
                                             " laivan sijainnit")})]
    (kasittele-vastaus! (assoc deps :konteksti konteksti) alukset (cheshire/parse-string body true))))

(defn- kasiteltavat-alukset* [alukset]
  (->> alukset
       (keep ::alus/mmsi)
       (into #{})))

(defn kasiteltavat-alukset [db]
  (kasiteltavat-alukset*
    (specql/fetch db
                  ::alus/alus
                  #{::alus/mmsi}
                  {::m/poistettu? false})))

(defn paivita-alusten-ais-data! [{:keys [db integraatioloki] :as deps} url]
  (let [palvelun-nimi "digitraffic"
        haun-nimi "ais-data-paivitys"
        alukset (kasiteltavat-alukset db)]
    (log/debug (str "Haetaan " (count alukset) " laivan sijainti"))
    (lukko/yrita-ajaa-lukon-kanssa
      db
      haun-nimi
      (fn []
        (integraatiotapahtuma/suorita-integraatio
          db
          integraatioloki
          palvelun-nimi
          haun-nimi
          (fn [konteksti]
            (tee-haku! deps url alukset konteksti)))))))

(defrecord Ais-haku [url sekunnit]
  component/Lifecycle
  (start [{:keys [db integraatioloki] :as this}]
    (if (ominaisuus-kaytossa? :ais-data)
      (do
        (log/info "Käynnistetään AIS-datan päivitys " sekunnit "s välein, urlista " url)
        (assoc this
          :lopeta-ais-data-hakeminen-fn!
          (ajastettu-tehtava/ajasta-sekunnin-valein
            sekunnit
            (fn [& _]
              (paivita-alusten-ais-data! this url)))))
      this))

  (stop [{:keys [lopeta-ais-data-hakeminen-fn!] :as this}]
    (when-let [fn lopeta-ais-data-hakeminen-fn!]
      (log/info "Lopetetaan ajastettu AIS-datan päivittäminen")
      (fn))
    this))
