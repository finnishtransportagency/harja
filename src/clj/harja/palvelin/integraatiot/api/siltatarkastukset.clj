(ns harja.palvelin.integraatiot.api.siltatarkastukset
  "Siltatarkstuksien API-kutsut"
  (:require [com.stuartsierra.component :as component]
            [compojure.core :refer [POST GET DELETE PUT]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [tee-sisainen-kasittelyvirhevastaus tee-viallinen-kutsu-virhevastaus tee-vastaus]]
            [harja.palvelin.integraatiot.api.tyokalut.json-skeemat :as json-skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu
                                                                             tee-kirjausvastauksen-body]]
            [harja.kyselyt.siltatarkastukset :as silta-q]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.validointi :as validointi]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as liitteet]
            [harja.kyselyt.konversio :as konv])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def api-tulos->kirjain
  {"eiToimenpiteita" #{\A}
   "puhdistettava" #{\B}
   "puhdistettava, urakanKunnostettava" #{\B \C}
   "puhdistettava, urakanKunnostettava, korjausOhjelmoitava" #{\B \C \D}
   "urakanKunnostettava" #{\C}
   "urakanKunnostettava, korjausOhjelmoitava" #{\C \D}
   "korjausOhjelmoitava" #{\D}
   "eiPade" #{\-}
   ;; Tyhjä string tulkitaan arvoksi eiPade (eli -)
   "" "-"})

(def api-kohde->numero
  {;; Alusrakenne
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
   "portaidenSiisteysJaKunto" 24})

(defn luo-siltatarkastus [ulkoinen-id urakka-id tarkastus silta kayttaja db]
  (log/debug "Luodaan uusi siltarkastus")
  (:id (silta-q/luo-siltatarkastus<!
         db
         (:id silta)
         urakka-id
         (aika-string->java-sql-date (:tarkastusaika tarkastus))
         (str (get-in tarkastus [:tarkastaja :etunimi]) " " (get-in tarkastus [:tarkastaja :sukunimi]))
         (:id kayttaja)
         ulkoinen-id
         "harja-api")))

(defn paivita-siltatarkastus [ulkoinen-id urakka-id tarkastus silta kayttaja db]
  (log/debug "Päivitetään vanha siltarkastus")
  (:id (silta-q/paivita-siltatarkastus-ulkoisella-idlla<!
         db
         (:id silta)
         urakka-id
         (aika-string->java-sql-date (:tarkastusaika tarkastus))
         (str (get-in tarkastus [:tarkastaja :etunimi]) " " (get-in tarkastus [:tarkastaja :sukunimi]))
         (:id kayttaja)
         false
         ulkoinen-id)))

(defn luo-tai-paivita-siltatarkastus [ulkoinen-id urakka-id tarkastus silta kayttaja db]
  (let [siltatarkastus-kannassa (first (silta-q/hae-siltatarkastus-ulkoisella-idlla-ja-luojalla db ulkoinen-id (:id kayttaja)))]
    (if siltatarkastus-kannassa
      (paivita-siltatarkastus ulkoinen-id urakka-id tarkastus silta kayttaja db)
      (luo-siltatarkastus ulkoinen-id urakka-id tarkastus silta kayttaja db))))

(defn lisaa-siltatarkastuskohteet [db liitteiden-hallinta kayttaja urakka-id sillantarkastuskohteet siltatarkastus-id]
  (log/debug "Tallennetaan siltatarkastuskohteet")
  (let [tallenna-kohderyhma
        (fn [kohderyhma]
          (doseq [kohde (keys kohderyhma)]
            (let [liitteet (get-in kohderyhma [kohde :liitteet])
                  kohde (silta-q/luo-siltatarkastuksen-kohde<! db
                                                               (konv/seq->array (api-tulos->kirjain (get-in kohderyhma [kohde :ehdotettutoimenpide])))
                                                               (get-in kohderyhma [kohde :lisatietoja])
                                                               siltatarkastus-id
                                                               (api-kohde->numero (name kohde)))]
              (when (and liitteet (not (empty? liitteet)))
                (liitteet/tallenna-liitteet-siltatarkastuskohteelle
                  db
                  liitteiden-hallinta
                  kayttaja
                  urakka-id
                  (:siltatarkastus kohde)
                  (:kohde kohde)
                  liitteet)))))]
    (silta-q/poista-siltatarkastuskohteet! db siltatarkastus-id)
    (tallenna-kohderyhma (:alusrakenne sillantarkastuskohteet))
    (tallenna-kohderyhma (:paallysrakenne sillantarkastuskohteet))
    (tallenna-kohderyhma (:varusteetJaLaitteet sillantarkastuskohteet))
    (tallenna-kohderyhma (:siltapaikanRakenteet sillantarkastuskohteet))))

(defn tarkista-siltatarkastus [db siltatunnus silta-id ulkoinen-tarkastus-id tarkastusaika]
  (when (silta-q/onko-olemassa? db silta-id ulkoinen-tarkastus-id tarkastusaika)
    (throw+ {:type virheet/+viallinen-kutsu+
             :virheet [{:koodi virheet/+duplikaatti-siltatarkastus+
                        :viesti (format "Sillalle (tunnus: %s) ei voi kirjata uutta tarkastusta, sillä samalla aikaleimalla (%s) on jo kirjattu tarkastus. Tarkastusajalle saa olla vain yksi tarkastus."
                                        siltatunnus tarkastusaika)}]})))

(defn lisaa-siltatarkastus [{id :id} data kayttaja db liitteiden-hallinta]
  (log/info "Kirjataan siltatarkastus käyttäjältä: " kayttaja)
  (let [urakka-id (Integer/parseInt id)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (jdbc/with-db-transaction [db db]
      (let [ulkoinen-id (str (get-in data [:siltatarkastus :tunniste :id]))
            tarkastus (:siltatarkastus data)
            siltatunnus (get-in data [:siltatarkastus :siltatunnus])
            silta (first (silta-q/hae-silta-tunnuksella db siltatunnus))]
        (if silta
          (do
            (log/debug "Siltatunnuksella löydetty silta: " (pr-str silta))
            (tarkista-siltatarkastus db siltatunnus (:id silta) ulkoinen-id (aika-string->java-sql-date (:tarkastusaika tarkastus)))
            (let [siltatarkastus-id (luo-tai-paivita-siltatarkastus
                                      ulkoinen-id
                                      urakka-id
                                      (:siltatarkastus data)
                                      silta
                                      kayttaja
                                      db)]
              (log/debug "Siltatarkastukselle saatu id kannassa: " siltatarkastus-id)
              (lisaa-siltatarkastuskohteet
                db
                liitteiden-hallinta
                kayttaja
                urakka-id
                (get-in data [:siltatarkastus :sillantarkastuskohteet])
                siltatarkastus-id)
              (tee-kirjausvastauksen-body {:ilmoitukset "Siltatarkistus kirjattu onnistuneesti"})))
          (throw+ {:type virheet/+viallinen-kutsu+
                   :virheet [{:koodi virheet/+tuntematon-silta+
                              :viesti (str "Siltaa ei löydy tunnuksella: " siltatunnus)}]}))))))

(defn poista-siltatarkastus [{id :id} data kayttaja db]
  (log/info "Kirjataan siltatarkastus käyttäjältä: " kayttaja)
  (let [urakka-id (Integer/parseInt id)
        ulkoiset-idt (-> data :tarkastusten-tunnisteet)
        kayttaja-id (:id kayttaja)]
    (validointi/tarkista-urakka-ja-kayttaja db urakka-id kayttaja)
    (let [poistettujen-maara (silta-q/poista-siltatarkastukset-ulkoisilla-idlla-ja-luojalla! db kayttaja-id ulkoiset-idt urakka-id)]
      (tee-kirjausvastauksen-body {:ilmoitukset (if (pos? poistettujen-maara)
                                                  (str poistettujen-maara " tarkastusta poistettu onnistuneesti")
                                                  "Vastaavia tarkastuksia ei loytynyt")}))))

(defrecord Siltatarkastukset []
  component/Lifecycle
  (start [{http :http-palvelin db :db liitteiden-hallinta :liitteiden-hallinta integraatioloki :integraatioloki :as this}]
    (julkaise-reitti
      http :api-lisaa-siltatarkastus
      (POST "/api/urakat/:id/tarkastus/siltatarkastus" request
        (kasittele-kutsu db integraatioloki :lisaa-siltatarkastus request
                         json-skeemat/siltatarkastuksen-kirjaus json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (lisaa-siltatarkastus parametrit data kayttaja db liitteiden-hallinta)))))
    (julkaise-reitti
      http :api-poista-siltatarkastus
      (DELETE "/api/urakat/:id/tarkastus/siltatarkastus" request
        (kasittele-kutsu db integraatioloki :poista-siltatarkastus request
                         json-skeemat/siltatarkastuksen-poisto json-skeemat/kirjausvastaus
                         (fn [parametrit data kayttaja db]
                           (poista-siltatarkastus parametrit data kayttaja db)))))
    this)

  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :api-lisaa-siltatarkastus
                     :api-poista-siltatarkastus)
    this))
