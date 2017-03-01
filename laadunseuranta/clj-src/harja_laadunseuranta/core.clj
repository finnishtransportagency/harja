(ns harja-laadunseuranta.core
  (:require [taoensso.timbre :as log]
            [compojure.core :refer [GET]]
            [harja.palvelin.komponentit.http-palvelin :as http-palvelin]
            [ring.util.response :refer [redirect]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [harja-laadunseuranta.tarkastusreittimuunnin.tarkastusreittimuunnin :as reittimuunnin]
            [harja-laadunseuranta.schemas :as schemas]
            [harja-laadunseuranta.utils :as utils]
            [harja.palvelin.palvelut.kayttajatiedot :as kayttajatiedot]
            [schema.core :as s]
            [clojure.core.match :refer [match]]
            [clojure.java.jdbc :as jdbc]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.data.codec.base64 :as b64]
            [com.stuartsierra.component :as component]
            [clojure.walk :as walk]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.konversio :as konv]
            [harja.pvm :as pvm]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.domain.roolit :as roolit])
  (:import (org.postgis PGgeometry))
  (:gen-class))


(defn- kayttajan-tarkastusurakat
  [db kayttaja sijainti]
  (let [urakat (kayttajatiedot/kayttajan-lahimmat-urakat
                 db
                 kayttaja
                 (fn [urakka kayttaja]
                   (oikeudet/voi-kirjata-ls-tyokalulla? kayttaja urakka))
                 sijainti)
        urakat (map
                 #(assoc % :oma-urakka?
                           (boolean ((set
                                       (keys (:urakkaroolit kayttaja)))
                                      (:id %))))
                 urakat)
        ;; Nostetaan lähin hoidon urakka kärkeen, jos sellainen löytyy
        lahdin-hoidon-urakka (first (filter #(= (:tyyppi %) "hoito") urakat))
        urakat (if lahdin-hoidon-urakka
                 (concat [lahdin-hoidon-urakka]
                         (remove #(= % lahdin-hoidon-urakka)
                                 urakat))
                 urakat)
        ;; Siirretään testiurakat hännille
        testiurakat (filter #(nil? (:urakkanro %)) urakat)
        urakat (concat (remove #(nil? (:urakkanro %)) urakat)
                       testiurakat)
        ;; Siirretään mahdolliset omat urakat (ne joissa käyttäjällä urakkarooli) jonon kärkeen
        omat-urakat (filter #(:oma-urakka? %) urakat)
        urakat (concat omat-urakat (remove #(:oma-urakka? %) urakat))]
    (into [] urakat)))

(defn- tallenna-merkinta! [tx vakiohavainto-idt merkinta]
  (q/tallenna-reittimerkinta! tx {:id (:id merkinta)
                                  :tarkastusajo (:tarkastusajo merkinta)
                                  :aikaleima (:aikaleima merkinta)
                                  :x (:lon (:sijainti merkinta))
                                  :y (:lat (:sijainti merkinta))
                                  :sijainti_tarkkuus (:accuracy (:sijainti merkinta))
                                  :lampotila (get-in merkinta [:mittaukset :lampotila])
                                  :lumisuus (get-in merkinta [:mittaukset :lumisuus])
                                  :talvihoito_tasaisuus (get-in merkinta [:mittaukset :talvihoito-tasaisuus])
                                  :soratie_tasaisuus (get-in merkinta [:mittaukset :soratie-tasaisuus])
                                  :kitkamittaus (get-in merkinta [:mittaukset :kitkamittaus])
                                  :kiinteys (get-in merkinta [:mittaukset :kiinteys])
                                  :polyavyys (get-in merkinta [:mittaukset :polyavyys])
                                  :sivukaltevuus (get-in merkinta [:mittaukset :sivukaltevuus])
                                  :havainnot (mapv vakiohavainto-idt (:havainnot merkinta))
                                  :kuvaus (get-in merkinta [:kuvaus])
                                  :laadunalitus (true? (get-in merkinta [:laadunalitus]))
                                  :kuva (get-in merkinta [:kuva])
                                  :liittyy_merkintaan (get-in merkinta [:liittyy-havaintoon])
                                  :tr_numero (get-in merkinta [:kayttajan-syottama-tr-osoite :tie])
                                  :tr_alkuosa (get-in merkinta [:kayttajan-syottama-tr-osoite :aosa])
                                  :tr_alkuetaisyys (get-in merkinta [:kayttajan-syottama-tr-osoite :aet])
                                  :tr_loppuosa (get-in merkinta [:kayttajan-syottama-tr-osoite :losa])
                                  :tr_loppuetaisyys (get-in merkinta [:kayttajan-syottama-tr-osoite :let])}))

(defn- tallenna-multipart-kuva! [db {:keys [tempfile content-type size]} kayttaja-id]
  (let [oid (tietokanta/tallenna-lob db (io/input-stream tempfile))]
    (:id (q/tallenna-kuva<! db {:lahde "harja-ls-mobiili"
                                :tyyppi content-type
                                :koko size
                                :pikkukuva (tietokanta/tee-thumbnail tempfile)
                                :oid oid
                                :luoja kayttaja-id}))))

(defn- tallenna-merkinnat! [db kirjaukset kayttaja-id]
  ;; Ei urakkaa tässä vaiheessa, ei voida tehdä oikeustarkistusta
  ;; Palvelun käyttö vaatii kuitenkin frontilla pääsyn työkaluun
  ;; ja Livi-tunnuksen, mikä on riittävä suoja.
  (log/debug "Vastaanotettu merkintä: " (pr-str kirjaukset))
  (jdbc/with-db-transaction [tx db]
    (let [vakiohavainto-idt (q/hae-vakiohavaintoavaimet tx)]
      (doseq [merkinta (:kirjaukset kirjaukset)]
        (tallenna-merkinta! tx vakiohavainto-idt merkinta)))))

(defn- merkitse-ajo-paattyneeksi! [tx tarkastusajo-id kayttaja]
  (log/debug "Merkitään ajo päättyneeksi")
  (q/paata-tarkastusajo! tx {:id tarkastusajo-id
                             :kayttaja (:id kayttaja)}))


(defn- lisaa-reittimerkinnalle-lopullinen-tieosoite
  "Lisää reittimerkintään ns. 'lopullisen tieosoitteen'.
   Jos käyttäjä on itse syöttänyt merkintään tieosoitteen, katsotaan sen olevan oikea osoite.
   Muussa tapauksessa käytetään sijainnin perusteella tieverkolle projisoitua osoitetta."
  [reittimerkinta]
  (let [tieverkolta-projisoitu-tieosoite? (boolean (:tie reittimerkinta))
        kayttajan-syottama-tieosoite? (boolean (and (:kayttajan-syottama-tie reittimerkinta)
                                                    (:kayttajan-syottama-aosa reittimerkinta)
                                                    (:kayttajan-syottama-aet reittimerkinta)))
        reittimerkinta-lopullisella-osoitteella
        (if (or tieverkolta-projisoitu-tieosoite?
                kayttajan-syottama-tieosoite?)
          (assoc reittimerkinta :tr-osoite (if kayttajan-syottama-tieosoite?
                                             {:tie (:kayttajan-syottama-tie reittimerkinta)
                                              :aosa (:kayttajan-syottama-aosa reittimerkinta)
                                              :aet (:kayttajan-syottama-aet reittimerkinta)
                                              :losa (:kayttajan-syottama-losa reittimerkinta)
                                              :let (:kayttajan-syottama-let reittimerkinta)}
                                             (select-keys reittimerkinta [:tie :aosa :aet])))
          reittimerkinta)]
    (dissoc reittimerkinta-lopullisella-osoitteella
            :tie :aosa :aet
            :kayttajan-syottama-tie
            :kayttajan-syottama-aosa
            :kayttajan-syottama-aet
            :kayttajan-syottama-losa
            :kayttajan-syottama-let)))

(defn lisaa-reittimerkinnoille-lopullinen-tieosoite [reittimerkinnat]
  (mapv lisaa-reittimerkinnalle-lopullinen-tieosoite reittimerkinnat))

(defn lisaa-tarkastuksille-urakka-id [{:keys [reitilliset-tarkastukset pistemaiset-tarkastukset]} urakka-id]
  {:reitilliset-tarkastukset (mapv #(assoc % :urakka urakka-id) reitilliset-tarkastukset)
   :pistemaiset-tarkastukset (mapv #(assoc % :urakka urakka-id) pistemaiset-tarkastukset)})

(defn tallenna-muunnetut-tarkastukset-kantaan [tx tarkastukset kayttaja urakka-id]
  (log/debug "Tallennetaan tarkastukset urakkaan " urakka-id)
  (reittimuunnin/tallenna-tarkastukset! tx tarkastukset kayttaja)
  (log/debug "Reittimerkitöjen muunto tarkastuksiksi suoritettu!"))

(defn muunna-tarkastusajon-reittipisteet-tarkastuksiksi [tx tarkastusajo-id]
  (log/debug "Muutetaan reittipisteet tarkastuksiksi")
  (let [merkinnat-tr-osoitteilla (q/hae-reitin-merkinnat-tieosoitteilla
                                   tx {:tarkastusajo tarkastusajo-id
                                       :laheiset_tiet_threshold 100})
        merkinnat-tr-osoitteilla (lisaa-reittimerkinnoille-lopullinen-tieosoite merkinnat-tr-osoitteilla)
        tarkastukset (reittimuunnin/reittimerkinnat-tarkastuksiksi
                       merkinnat-tr-osoitteilla
                       {:analysoi-rampit? true
                        :analysoi-ymparikaantymiset? true
                        :analysoi-virheelliset-tiet? true})]
    (log/debug "Reittipisteet muunnettu tarkastuksiksi.")
    tarkastukset))

(defn paata-tarkastusajo! [db tarkastusajo kayttaja]
  (jdbc/with-db-transaction [tx db]
    (let [tarkastusajo-id (-> tarkastusajo :tarkastusajo :id)
          urakka-id (:urakka tarkastusajo)
          _ (oikeudet/vaadi-ls-tyokalun-kirjausoikeus kayttaja urakka-id)
          ajo-paatetty (:paatetty (first (q/ajo-paatetty tx {:id tarkastusajo-id})))]
      (if-not ajo-paatetty
        (let [tarkastukset (muunna-tarkastusajon-reittipisteet-tarkastuksiksi
                             tx
                             tarkastusajo-id)
              tarkastukset (lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)]
          (tallenna-muunnetut-tarkastukset-kantaan tx tarkastukset kayttaja urakka-id)
          (merkitse-ajo-paattyneeksi! tx tarkastusajo-id kayttaja))
        (log/warn (format "Yritettiin päättää ajo %s, joka on jo päätetty!" tarkastusajo-id))))))

(defn- luo-uusi-tarkastusajo! [db tiedot kayttaja]
  ;; Ei urakkaa tässä vaiheessa, ei voida tehdä oikeustarkistusta
  ;; Palvelun käyttö vaatii kuitenkin frontilla pääsyn työkaluun
  ;; ja Livi-tunnuksen, mikä on riittävä suoja.
  (q/luo-uusi-tarkastusajo<! db {:ulkoinen_id 0
                                 :kayttaja (:id kayttaja)}))

(defn- hae-tarkastusajon-reitti
  "Debug-funktio, jota käytetään vain salaisesta TR-osiosta, vaatii jvh:n."
  [db tiedot kayttaja]
  (roolit/vaadi-rooli kayttaja roolit/jarjestelmavastaava)
  (let [merkinnat (mapv
                    #(assoc % :sijainti (let [geometria (.getGeometry (:sijainti %))]
                                          [(.x geometria) (.y geometria)]))
                    (q/hae-tarkastusajon-reitti db {:id (:tarkastusajo-id tiedot)}))]
    merkinnat))

(defn- hae-tr-osoite [db lat lon treshold]
  (try
    (first (q/hae-tr-osoite db {:y lat
                                :x lon
                                :treshold treshold}))
    (catch Exception e
      nil)))

(defn- hae-tr-tiedot [db lat lon treshold]
  (let [pos {:y lat
             :x lon
             :treshold treshold}
        talvihoitoluokka (q/hae-pisteen-hoitoluokka db (assoc pos :tietolaji "talvihoito"))
        soratiehoitoluokka (q/hae-pisteen-hoitoluokka db (assoc pos :tietolaji "soratie"))]
    {:talvihoitoluokka (:hoitoluokka_pisteelle (first talvihoitoluokka))
     :soratiehoitoluokka (:hoitoluokka_pisteelle (first soratiehoitoluokka))
     :tr-osoite (hae-tr-osoite db lat lon treshold)}))

(defn- hae-urakkatyypin-urakat [db urakkatyyppi kayttaja]
  (let [urakat (map
                 #(assoc % :urakkaroolissa? (if ((set
                                                   (keys (:urakkaroolit kayttaja)))
                                                  (:id %))
                                              true
                                              false))
                 (q/hae-urakkatyypin-urakat db
                                            {:tyyppi urakkatyyppi}
                                            ))]
    urakat))

(defn- muunna-havainnot [{kirjaukset :kirjaukset :as tiedot}]
  (if kirjaukset
    (assoc tiedot :kirjaukset
                  (mapv (fn [kirjaus]
                          (if-let [havainnot (:havainnot kirjaus)]
                            (assoc kirjaus :havainnot (into #{} (map keyword havainnot)))
                            kirjaus)) kirjaukset))
    tiedot))

(defn kasittele-api-kutsu [skeema-sisaan ok-skeema kasittelija]
  (let [skeema-ulos (schemas/api-vastaus ok-skeema)]
    (fn [user tiedot]
      (oikeudet/vaadi-kirjoitusoikeus oikeudet/laadunseuranta-kirjaus user)
      (let [tiedot (->> tiedot
                        walk/keywordize-keys
                        muunna-havainnot
                        (s/validate skeema-sisaan))
            tulos (try {:ok (kasittelija user tiedot)}
                       (catch Throwable t
                         (log/error "Virhe LS API-kutsussa: " (.getMessage t) ".\nStack: " (.printStackTrace t) ".\nTiedot: " (pr-str tiedot))
                         {:error (.getMessage t)}))]
        (->> tulos
             (s/validate skeema-ulos))))))

(defn- laadunseuranta-api [db http]
  (http-palvelin/julkaise-palvelut
    http

    :ls-reittimerkinta
    (kasittele-api-kutsu
      schemas/Havaintokirjaukset s/Str
      (fn [user kirjaukset]
        (tallenna-merkinnat! db kirjaukset (:id user))
        "Reittimerkinta tallennettu"))

    :ls-paata-tarkastusajo
    (kasittele-api-kutsu
      schemas/TarkastuksenPaattaminen s/Str
      (fn [user tarkastusajo]
        (log/debug "Päätetään tarkastusajo " tarkastusajo)
        (paata-tarkastusajo! db tarkastusajo user)
        "Tarkastusajo päätetty"))

    :ls-uusi-tarkastusajo
    (kasittele-api-kutsu
      s/Any s/Any
      (fn [user tiedot]
        (log/debug "Luodaan uusi tarkastusajo " tiedot)
        (luo-uusi-tarkastusajo! db tiedot user)))

    :ls-simuloitu-reitti
    (kasittele-api-kutsu
      s/Any s/Any
      (fn [user tiedot]
        (log/debug "Palautetaan aiemmin ajettu tarkastusreitti simuloitua ajoa varten " tiedot)
        (hae-tarkastusajon-reitti db tiedot user)))

    :ls-hae-tr-tiedot
    (kasittele-api-kutsu
      s/Any s/Any
      (fn [user koordinaatit]
        (log/debug "Haetaan tierekisteritietoja pisteelle " koordinaatit)
        (let [{:keys [lat lon treshold]} koordinaatit]
          (hae-tr-tiedot db lat lon treshold))))

    :ls-hae-kayttajatiedot
    (kasittele-api-kutsu
      s/Any s/Any
      (fn [kayttaja tiedot]
        (let [kayttajatiedot-kannassa (kayttajatiedot/hae-kayttaja db (:id kayttaja))
              kayttajan-tarkastusurakat (kayttajan-tarkastusurakat db kayttaja (:sijainti tiedot))]
          {:kayttajanimi (:kayttajanimi kayttajatiedot-kannassa)
           :nimi (str (:etunimi kayttajatiedot-kannassa)
                      " "
                      (:sukunimi kayttajatiedot-kannassa))
           :urakat kayttajan-tarkastusurakat
           :organisaatio (:organisaatio kayttajatiedot-kannassa)})))))


(defn- tallenna-liite [db req]
  (let [id (tallenna-multipart-kuva! db (get-in req [:multipart-params "liite"]) (get-in req [:kayttaja :id]))]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (str id)}))

(defn luo-routet [db http]
  ;; Reitti liitteen tallennukseen
  (http-palvelin/julkaise-palvelu
    http :ls-tallenna-liite
    (wrap-multipart-params
      (fn [req]
        (println "SAATIIN UPLOAD: " req)
        (tallenna-liite db req)))
    {:ring-kasittelija? true})

  ;; Laadunseurannan API kutsut
  (laadunseuranta-api db http))

(defrecord Laadunseuranta []
  component/Lifecycle
  (start [{db :db
           http :http-palvelin
           :as this}]
    (tietokanta/aseta-tietokanta! db)
    (log/info "Harja laadunseuranta käynnistyy")
    (luo-routet db http)
    this)

  (stop [{http :http-palvelin :as this}]
    (http-palvelin/poista-palvelut http
                                   :ls-reittimerkinta
                                   :ls-paata-tarkastusajo
                                   :ls-uusi-tarkastusajo
                                   :ls-paata-tarkastusajo
                                   :ls-hae-kayttajatiedot
                                   :ls-tallenna-liite)
    this))
