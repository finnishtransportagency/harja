(ns harja-laadunseuranta.utils
  (:require [harja-laadunseuranta.config :as c]
            [ring.util.http-response :refer :all]
            [compojure.api.meta :as meta]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

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

(defn wrap-kayttajatarkistus [lataa-kayttaja handler]
  (fn [req]
    (if-let [kayttajanimi (kayttajaheaderi req)]
      (if-let [kayttaja (lataa-kayttaja kayttajanimi)]
        (if (or (feikattu-kayttaja? kayttajanimi)
                (some #(on-ryhma? (ryhmat req) %) vaaditut-ryhmat))
          (handler (assoc req :kayttaja kayttaja))
          (unauthorized "VIRHE: Ei käyttöoikeutta"))
        (unauthorized "VIRHE: Käyttäjää ei löydy"))
      (unauthorized "Access denied!"))))

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
