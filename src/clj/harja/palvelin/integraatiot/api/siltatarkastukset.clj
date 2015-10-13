(ns harja.palvelin.integraatiot.api.siltatarkastukset
  "Siltatarkstuksien API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE PUT]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu]]
            [harja.kyselyt.siltatarkastukset :as silta-q]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [pvm-string->java-sql-date]])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn api-tulos->kirjain [tulos-nimi]
  (case tulos-nimi
    "eiToimenpiteita" "A"
    "puhdistettava" "B"
    "urakanKunnostettava" "C"
    "korjausOhjelmoitava" "D"))

(defn api-kohde->numero [kohde-nimi]
  (case kohde-nimi
    ;; Alusrakenne
    "maatukienSiisteysJaKunto" 1
    "valitukienSiisteysJaKunto" 2
    "laakeritasojenSiisteysJaKunto" 3
    ;; Päällysrakenne
    "kansilaatta" 4
    "paallysteenKunto" 5
    "reunapalkinSiisteysJaKunto" 6
    "reunapalkinLiikuntasauma" 7
    "reunapalkinJaPaallysteenValisenSaumanSiisteysJaKunto" 8
    "sillanpaidenSaumat" 9
    "sillanJaPenkereenRaja" 10
    ;; Varusteet ja laitteet
    "kaiteidenJaSuojaverkkojenVauriot" 11
    "liikuntasaumalaitteidenSiisteysJaKunto" 12
    "laakerit" 13
    "syoksytorvet" 14
    "tippuputket" 15
    "kosketussuojatJaNiidenKiinnitykset" 16
    "valaistuslaitteet" 17
    "johdotJaKaapelit" 18
    "liikennemerkit" 19
    ;; Siltapaikan rakenteet
    "kuivatuslaitteidenSiisteysJaKunto" 20
    "etuluiskienSiisteysJaKunto" 21
    "keilojenSiisteysJaKunto" 22
    "tieluiskienSiisteysJaKunto" 23
    "portaidenSiisteysJaKunto" 24))

(defn luo-siltatarkastus [ulkoinen-id urakka-id tarkastus silta kayttaja db]
  (log/debug "Luodaan uusi siltarkastus")
  (:id (silta-q/luo-siltatarkastus<!
         db
         (:id silta)
         urakka-id
         (pvm-string->java-sql-date (:tarkastusaika tarkastus))
         (str (get-in tarkastus [:tarkastaja :etunimi]) " " (get-in tarkastus [:tarkastaja :sukunimi]))
         (:id kayttaja)
         ulkoinen-id)))

(defn paivita-siltatarkastus [ulkoinen-id urakka-id tarkastus silta kayttaja db]
  (log/debug "Päivitetään vanha siltarkastus")
  (:id (silta-q/paivita-siltatarkastus<!
         db
         (:id silta)
         urakka-id
         (pvm-string->java-sql-date (:tarkastusaika tarkastus))
         (str (get-in tarkastus [:tarkastaja :etunimi]) " " (get-in tarkastus [:tarkastaja :sukunimi]))
         (:id kayttaja)
         false
         ulkoinen-id)))

(defn luo-tai-paivita-siltatarkastus [ulkoinen-id urakka-id tarkastus silta kayttaja db]
  (let [siltatarkastus-kannassa (first (silta-q/hae-siltatarkastus-ulkoisella-idlla-ja-luojalla db ulkoinen-id (:id kayttaja)))]
    (if siltatarkastus-kannassa
      (paivita-siltatarkastus ulkoinen-id urakka-id tarkastus silta kayttaja db)
      (luo-siltatarkastus ulkoinen-id urakka-id tarkastus silta kayttaja db))))

(defn lisaa-siltatarkastuskohteet [sillantarkastuskohteet siltatarkastus-id db]
  (log/debug "Tallennetaan siltatarkastuskohteet")
  (let [tallenna-kohdekohderyhma (fn [kohderyhma]
                                   (doseq [kohde (keys kohderyhma)]
                                     (silta-q/luo-siltatarkastuksen-kohde<! db
                                                                            (api-tulos->kirjain (get-in kohderyhma [kohde :ehdotettutoimenpide]))
                                                                            (get-in kohderyhma [kohde :lisatietoja])
                                                                            siltatarkastus-id
                                                                            (api-kohde->numero (name kohde)))))]
    (silta-q/poista-siltatarkastuskohteet! db siltatarkastus-id)
    (tallenna-kohdekohderyhma (:alusrakenne sillantarkastuskohteet))
    (tallenna-kohdekohderyhma (:paallysrakenne sillantarkastuskohteet))
    (tallenna-kohdekohderyhma (:varusteetJaLaitteet sillantarkastuskohteet))
    (tallenna-kohdekohderyhma (:siltapaikanRakenteet sillantarkastuskohteet))))

(defn lisaa-siltatarkastus [{id :id} data kayttaja db]
  (log/info "Kirjataan siltatarkastus käyttäjältä: " kayttaja)
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (jdbc/with-db-transaction [transaktio db]
                              (let [ulkoinen-id (str (get-in data [:siltatarkastus :tunniste :id]))
                                    siltanumero (Integer. (get-in data [:siltatarkastus :siltanumero]))
                                    silta (first (silta-q/hae-silta-numerolla transaktio siltanumero))]
                                (if silta
                                  (do
                                    (log/debug "Siltanumerolla löydetty silta: " (pr-str silta))
                                    (let [siltatarkastus-id (luo-tai-paivita-siltatarkastus
                                                              ulkoinen-id
                                                              urakka-id
                                                              (:siltatarkastus data)
                                                              silta
                                                              kayttaja
                                                              transaktio)]
                                      (log/debug "Siltatarkastukselle saatu id kannassa: " siltatarkastus-id)
                                      (lisaa-siltatarkastuskohteet (get-in data [:siltatarkastus :sillantarkastuskohteet]) siltatarkastus-id transaktio)))
                                  (throw (RuntimeException. (format (str "Siltaa numerolla " siltanumero " ei löydy.")))))))))

(defrecord Siltatarkastukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :lisaa-siltatarkastus
      (POST "/api/urakat/:id/tarkastus/siltatarkastus" request
        (kasittele-kutsu db integraatioloki :lisaa-siltatarkastus request json-skeemat/+siltatarkastuksen-kirjaus+ nil
                         (fn [parametrit data kayttaja db]
                           (lisaa-siltatarkastus parametrit data kayttaja db)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :lisaa-siltatarkastus)
    this))