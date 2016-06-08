(ns harja-laadunseuranta.core
  (:require [taoensso.timbre :as log]
            [gelfino.timbre :as gt]
            [org.httpkit.server :as server]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]            
            [ring.util.response :refer [redirect]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [harja-laadunseuranta.tarkastukset :as tarkastukset]
            [harja-laadunseuranta.schemas :as schemas]
            [harja-laadunseuranta.utils :as utils :refer [respond]]
            [harja-laadunseuranta.config :as c]
            [schema.core :as s]
            [clojure.core.match :refer [match]]
            [clojure.java.jdbc :as jdbc]
            [compojure.route :as route]
            [compojure.api.exception :as ex]
            [clojure.java.io :as io]
            [clojure.data.codec.base64 :as b64])
  (:import (org.postgis PGgeometry))
  (:gen-class))

(def db tietokanta/db)

(defn- tallenna-merkinta! [tx vakiohavainto-idt merkinta]
  (q/tallenna-reittimerkinta! {:id (:id merkinta)
                               :tarkastusajo (:tarkastusajo merkinta)
                               :aikaleima (:aikaleima merkinta)
                               :x (:lon (:sijainti merkinta))
                               :y (:lat (:sijainti merkinta))
                               :lampotila (get-in merkinta [:mittaukset :lampotila])
                               :lumisuus (get-in merkinta [:mittaukset :lumisuus])
                               :tasaisuus (get-in merkinta [:mittaukset :tasaisuus])
                               :kitkamittaus (get-in merkinta [:mittaukset :kitkamittaus])
                               :kiinteys (get-in merkinta [:mittaukset :kiinteys])
                               :polyavyys (get-in merkinta [:mittaukset :polyavyys])
                               :sivukaltevuus (get-in merkinta [:mittaukset :sivukaltevuus])
                               :havainnot (mapv vakiohavainto-idt (:havainnot merkinta))
                               :kuvaus (get-in merkinta [:kuvaus])
                               :laadunalitus (get-in merkinta [:laadunalitus])
                               :kuva (get-in merkinta [:kuva])}
                              {:connection tx}))

(defn- tallenna-kuva! [tx {:keys [data mime-type]} kayttaja-id]
  (let [decoded-data (b64/decode (.getBytes data "UTF-8"))
        oid (tietokanta/tallenna-lob (io/input-stream decoded-data))]
    (:id (q/tallenna-kuva<! {:lahde "harja-ls-mobiili"
                             :tyyppi mime-type
                             :koko (count decoded-data)
                             :pikkukuva (tietokanta/tee-thumbnail decoded-data)
                             :oid oid
                             :luoja kayttaja-id}
                            {:connection tx}))))

(defn- tallenna-multipart-kuva! [tx {:keys [tempfile content-type size]} kayttaja-id]
  (let [oid (tietokanta/tallenna-lob (io/input-stream tempfile))]
    (:id (q/tallenna-kuva<! {:lahde "harja-ls-mobiili"
                             :tyyppi content-type
                             :koko size
                             :pikkukuva (tietokanta/tee-thumbnail tempfile)
                             :oid oid
                             :luoja kayttaja-id}
                            {:connection tx}))))

(defn- tallenna-merkinnat! [kirjaukset kayttaja-id]
  (jdbc/with-db-transaction [tx @db]
    (let [vakiohavainto-idt (q/hae-vakiohavaintoavaimet tx)]
      (doseq [merkinta (:kirjaukset kirjaukset)]
        (tallenna-merkinta! tx vakiohavainto-idt merkinta)))))

(defn merkitse-ajo-paattyneeksi! [tx tarkastusajo-id kayttaja]
  (q/paata-tarkastusajo! {:id tarkastusajo-id
                          :kayttaja (:id kayttaja)}
                         {:connection tx}))

(defn- paata-tarkastusajo! [tarkastusajo kayttaja]
  (jdbc/with-db-transaction [tx @db]
    (let [tarkastusajo-id (-> tarkastusajo :tarkastusajo :id)
          urakka-id (:id (first (q/paattele-urakka {:tarkastusajo tarkastusajo-id}
                                                   {:connection tx})))
          merkinnat (q/hae-reitin-merkinnat {:tarkastusajo tarkastusajo-id
                                             :treshold 100}
                                            {:connection tx})
          merkinnat-tr-osoitteilla (tarkastukset/lisaa-reittimerkinnoille-tieosoite merkinnat)
          tarkastukset (-> (tarkastukset/reittimerkinnat-tarkastuksiksi merkinnat-tr-osoitteilla)
                           (tarkastukset/lisaa-tarkastuksille-urakka-id urakka-id))]
      (tarkastukset/tallenna-tarkastukset! tarkastukset kayttaja)
      (merkitse-ajo-paattyneeksi! tx tarkastusajo-id kayttaja))))

(defn- tarkastustyypiksi [tyyppi]
  (condp = tyyppi
    :kelitarkastus 1
    :soratietarkastus 2
    0))

(defn- luo-uusi-tarkastusajo! [tiedot kayttaja]
  (q/luo-uusi-tarkastusajo<! {:ulkoinen_id 0
                              :kayttaja (:id kayttaja)
                              :tyyppi (tarkastustyypiksi (-> tiedot :tyyppi))}
                             {:connection @db}))

(defn- hae-tr-osoite [lat lon treshold]
  (try
    (first (q/hae-tr-osoite {:y lat
                             :x lon
                             :treshold treshold}
                            {:connection @db}))
    (catch Exception e
      nil)))

(defn- hae-tr-tiedot [lat lon treshold]
  (let [pos {:y lat
             :x lon
             :treshold treshold}
        talvihoitoluokka (q/hae-pisteen-hoitoluokka (assoc pos :tietolaji "talvihoito")
                                                    {:connection @db})
        soratiehoitoluokka (q/hae-pisteen-hoitoluokka (assoc pos :tietolaji "soratie")
                                                      {:connection @db})]
    {:talvihoitoluokka (:hoitoluokka_pisteelle (first talvihoitoluokka))
     :soratiehoitoluokka (:hoitoluokka_pisteelle (first soratiehoitoluokka))
     :tr-osoite (hae-tr-osoite lat lon treshold)}))

(defapi laadunseuranta-api
  {:format {:formats [:transit-json]}
   :exceptions {:handlers {::ex/default utils/poikkeuskasittelija}}}
  
  (POST "/reittimerkinta" []
        :body [kirjaukset schemas/Havaintokirjaukset]
        :summary "Tallentaa reittimerkinnat"
        :kayttaja kayttaja
        :return {:ok s/Str}
        (respond (tallenna-merkinnat! kirjaukset (:id kayttaja))
                 "Reittimerkinta tallennettu"))

  (POST "/paata-tarkastusajo" []
        :body [tarkastusajo schemas/TarkastuksenPaattaminen]
        :kayttaja kayttaja
        :summary "Päättää tarkastusajon"
        :return {:ok s/Str}
        (respond (log/debug "Päätetään tarkastusajo " tarkastusajo)
                 (paata-tarkastusajo! tarkastusajo kayttaja)
                 "Tarkastusajo päätetty"))
  
  (POST "/uusi-tarkastusajo" []
        :body [tiedot s/Any]
        :kayttaja kayttaja
        :summary "Luo uuden tarkastusajon"
        :return {:ok s/Any}
        (respond (log/debug "Luodaan uusi tarkastusajo " tiedot)
                 (luo-uusi-tarkastusajo! tiedot kayttaja)))

  (POST "/hae-tr-tiedot" []
        :body [koordinaatit s/Any]
        :summary "Hakee tierekisterin tiedot annetulle pisteelle"
        :return {:ok s/Any}
        (respond (log/debug "Haetaan tierekisteritietoja pisteelle " koordinaatit)
                 (let [{:keys [lat lon treshold]} koordinaatit]
                   (hae-tr-tiedot lat lon treshold))))
  
  (GET "/hae-kayttajatiedot" []
       :summary "Hakee käyttäjän tiedot"
       :kayttaja kayttaja
       :return {:ok s/Any}
       (respond (log/debug "Käyttäjän tietojen haku")
                {:kayttajanimi (:kayttajanimi kayttaja)
                 :nimi (str (:etunimi kayttaja) " " (:sukunimi kayttaja))
                 :vakiohavaintojen-kuvaukset (q/hae-vakiohavaintojen-kuvaukset @db)})))

(defn- lataa-kayttaja [kayttajanimi]
  (first (q/hae-kayttajatiedot {:kayttajanimi kayttajanimi}
                               {:connection @db})))

(defn- tallenna-liite [req]
  (jdbc/with-db-transaction [tx @db]
    (let [id (tallenna-multipart-kuva! tx (get-in req [:multipart-params "liite"]) (get-in req [:kayttaja :id]))]
      {:status 200
       :headers {"Content-Type" "text/plain"}
       :body (str id)})))

(defroutes app
  (GET "/" [] (redirect (utils/polku "/index.html")))
  (middleware [(partial utils/wrap-kayttajatarkistus lataa-kayttaja)]
              (context "/api" [] laadunseuranta-api))
  (middleware [(partial utils/wrap-kayttajatarkistus lataa-kayttaja)
               wrap-multipart-params]
              (POST "/tallenna-liite" req tallenna-liite))
  (route/resources "/")
  (route/not-found "Page not found"))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 2000)
    (reset! server nil)))

(defn- alusta-logitus []
  (when-let [gelf (:gelf @c/config)]
    (log/merge-config! {:appenders {:gelf (assoc gt/gelf-appender :min-level (:taso gelf))}
                        :shared-appender-config {:gelf {:host (:palvelin gelf)}}})))

(defn start-server []
  (alusta-logitus)
  (server/run-server #'app (:http-palvelin @c/config)))

(defn -main []
  (start-server))
