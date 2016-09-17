(ns harja-laadunseuranta.utils
  (:require [harja-laadunseuranta.config :as c]
            [ring.util.http-response :refer :all]
            [compojure.api.meta :as meta]
            [taoensso.timbre :as log]
            [clojure.string :as str]

            [harja.palvelin.komponentit.todennus :as todennus]
            [harja.domain.oikeudet :as oikeudet]))

(defn- kayttajaheaderi [req]
  (get-in req [:headers "oam_remote_user"]))

(defn ryhmat [req]
  (let [groups (get-in req [:headers "oam_groups"])]
    (if (empty? groups)
      #{}
      (set (map str/lower-case (str/split groups #","))))))

(def vaaditut-ryhmat #{"tilaajan_laadunvalvoja"
                       "tilaajan_urakanvalvoja"
                       "jarjestelmavastaava"
                       "paakayttaja"
                       "laadunvalvoja"
                       "ely_kayttaja"
                       "ely_paakayttaja"
                       "ely_urakanvalvoja"
                       "ely_laadunvalvoja"})

(defn on-ryhma? [ryhmat vaadittu-ryhma]
  (some #(str/ends-with? % vaadittu-ryhma) ryhmat))

(defn feikattu-kayttaja? [kayttajanimi]
  (contains? (set (:feikatut-kayttajat @c/config)) kayttajanimi))

(defn wrap-kayttajatarkistus [todennus handler]
  (fn [req]
    (let [{kayttaja :kayttaja :as req} (todennus/todenna-pyynto todennus req)]
      (if (oikeudet/voi-kirjoittaa? oikeudet/laadunseuranta-kirjaus nil kayttaja)
        (handler req)
        {:status 403
         :headers {"Content-Type" "text/plain; charset=UTF-8"}
         :body "Ei käyttöoikeutta. Ota yhteyttä organisaatiosi Sähke-käyttövaltuusvastaavaan."}))))

(defn polku [s]
  (str (:url-prefix @c/config) s))

(defmacro respond [& body]
  `(let [result# (do ~@body)]
     (ok {:ok result#})))

(defmethod meta/restructure-param :kayttaja [_ user acc]
  (update-in acc [:lets] into [user `(:kayttaja ~'+compojure-api-request+)]))

(defn poikkeuskasittelija [^Exception e data req]
  (log/error e "Virhe " (.getMessage e))
  (when-let [next-ex (.getNextException e)]
    (log/error next-ex "-- Sisempi virhe " (.getMessage next-ex)))
  (internal-server-error {:error (.getMessage e)}))

(defn select-non-nil-keys [c keys]
  (into {} (filterv #(not (nil? (second %))) (into [] (select-keys c keys)))))
