(ns harja-laadunseuranta.core
  (:require [taoensso.timbre :as log]
            [org.httpkit.server :as server]
            [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]            
            [ring.util.response :refer [redirect]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
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
  (:gen-class))

(def db tietokanta/db)

(defn- tallenna-merkinta! [tx merkinta]
  (q/tallenna-reittimerkinta! {:id (:id merkinta)
                               :tarkastusajo (:tarkastusajo merkinta)
                               :aikaleima (:aikaleima merkinta)
                               :x (:lon (:sijainti merkinta))
                               :y (:lat (:sijainti merkinta))
                               :lampotila (get-in merkinta [:havainnot :lampotila])
                               :lumisuus (get-in merkinta [:havainnot :lumisuus])
                               :tasaisuus (get-in merkinta [:havainnot :tasaisuus])
                               :kitkamittaus (get-in merkinta [:havainnot :kitkamittaus])
                               :liukasta (get-in merkinta [:havainnot :liukasta])
                               :kuvaus (get-in merkinta [:kuvaus])
                               :kuva (get-in merkinta [:kuva])}
                              {:connection tx}))

(defn- tallenna-kuva! [tx {:keys [data mime-type]} kayttaja-id]
  (let [decoded-data (b64/decode (.getBytes data "UTF-8"))
        oid (tietokanta/tallenna-lob (io/input-stream decoded-data))]
    (:id (q/tallenna-kuva<! {:lahde "harja-ls-mobiili"
                             :tyyppi mime-type
                             :koko (count decoded-data)
                             :oid oid
                             :luoja kayttaja-id}
                            {:connection tx}))))

(defn- tallenna-merkinnat! [kirjaukset kayttaja-id]
  (jdbc/with-db-transaction [tx @db]
   (doseq [merkinta (:kirjaukset kirjaukset)]
     (if-let [kuva (:kuva merkinta)]
       (tallenna-merkinta! tx (assoc merkinta :kuva (tallenna-kuva! tx kuva kayttaja-id)))
       (tallenna-merkinta! tx merkinta)))))

(defn merkitse-ajo-paattyneeksi [tarkastusajo kayttaja]
  (q/paata-tarkastusajo! {:id (-> tarkastusajo :tarkastusajo :id)
                          :kayttaja (:id kayttaja)}
                         {:connection @db}))

(defn reittimerkinnat-tarkastuksiksi
  "Käy annetut reittimerkinnät läpi ja muodostaa niistä loogiset tarkastukset, jotka näkyvät käyttäjille.
  Uusi tarkastus alkaa jos jokin seuraavista täyttyy:
  1. Tie ja tieosa ovat samat kuin edellisellä pisteellä\n2. Etäisyys kasvaa / laskee & suunta pysyy samana (ei olla käännytty ympäri)\n3. Havaintoa ei ole tai se pysyy samana kuin edellisellä pisteellä."
  [reittimerkinnat]
  (let [merkinnat (q/hae-tarkastusajon-reittimerkinnat (:id tarkastusajo))]

    )
  )

(defn tallenna-tarkastukset [tarkastukset kayttaja]
  ;; TODO
  )

(defn- paata-tarkastusajo! [tarkastusajo kayttaja]
  (merkitse-ajo-paattyneeksi tarkastusajo kayttaja)
  (let [merkinnat (q/hae-tarkastusajon-reittimerkinnat (:id tarkastusajo))
        tarkastukset (reittimerkinnat-tarkastuksiksi merkinnat)]
    (tallenna-tarkastukset tarkastukset kayttaja)))

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
        :body [tarkastusajo s/Any]
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

  (POST "/hae-tr-osoite" []
        :body [koordinaatit s/Any]
        :summary "Hakee tierekisteriosoitteen annetulle pisteelle"
        :return {:ok (s/maybe schemas/TROsoite)}
        (respond (log/debug "Haetaan tierekisteriosoite pisteelle " koordinaatit)
                 (let [{:keys [lat lon treshold]} koordinaatit]
                   (hae-tr-osoite lat lon treshold))))

  (GET "/hae-kayttajatiedot" []
       :summary "Hakee käyttäjän tiedot"
       :kayttaja kayttaja
       :return {:ok s/Any}
       (respond (log/debug "Käyttäjän tietojen haku")
                {:kayttajanimi (:kayttajanimi kayttaja)
                 :nimi (str (:etunimi kayttaja) " " (:sukunimi kayttaja))})))

(defn- lataa-kayttaja [kayttajanimi]
  (first (q/hae-kayttajatiedot {:kayttajanimi kayttajanimi}
                               {:connection @db})))

(defroutes app
  (GET "/" [] (redirect (utils/polku "/index.html")))
  (middleware [(partial utils/wrap-kayttajatarkistus lataa-kayttaja)]
    (context "/api" [] laadunseuranta-api))
  (route/resources "/")
  (route/not-found "Page not found"))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 2000)
    (reset! server nil)))

(defn start-server []
  (server/run-server #'app (:http-palvelin @c/config)))

(defn -main []
  (start-server))
