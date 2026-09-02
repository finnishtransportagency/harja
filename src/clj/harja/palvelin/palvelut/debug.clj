(ns harja.palvelin.palvelut.debug
  "Erinäisiä vain JVH:lle tarkoitettuja palveluita, joilla voi selvitellä
  eri tilanteita, esim. TR-osiossa."
  (:require [clojure.string :as string]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.palautevayla :as palautevayla-kyselyt]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [harja.kyselyt.debug :as q]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodit-kyselyt]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.tloik.kasittely.ilmoitus :as ilmoitus-kasittely]
            [harja.palvelin.integraatiot.tloik.sanomat.ilmoitus-sanoma :as ilmoitussanoma]
            [harja.palvelin.integraatiot.tloik.ilmoitukset :as tloik-ilmoitukset]
            [harja.palvelin.integraatiot.api.reittitoteuma :as reittitoteuma]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoitukset]
            [harja.kyselyt.tieliikenneilmoitukset :as tieliikenneilmoitukset-q]
            [harja.palvelin.integraatiot.tloik.sahkoposti :as tloik-sahkoposti]
            [harja.palvelin.integraatiot.tloik.tekstiviesti :as tloik-tekstiviesti]
            [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri-haku]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.sms.sms-komponentti :as sms]
            [harja.kyselyt.tieturvallisuusverkko :as tieturvallisuusverkko-kyselyt]
            [harja.kyselyt.paallysteen-korjausluokat :as korjausluokka-kyselyt]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.tieturvallisuusverkko :as tieturvallisuusverkko-tuonti]
            [harja.geo :as geo]
            [clojure.string :as str]))

(defn hae-toteuman-reitti-ja-pisteet [db toteuma-id]
  (let [tulos (konv/sarakkeet-vektoriin
               (map konv/alaviiva->rakenne
                    (q/hae-toteuman-reitti-ja-pisteet
                     db {:toteuma-id toteuma-id}))
               {:reittipiste :reittipisteet})]
    {:reitti (:reitti (first tulos))
     :reittipisteet (:reittipisteet (first tulos))}))

(defn hae-tyokonehavainto-reitti [db params]
  (let [tulos (q/hae-tyokonehavainto-reitti db {:tyokoneid (:tyokone-id params)})
        reitti (:sijainti (first tulos))]
    reitti))
(defn hae-seuraava-vapaa-ulkoinen-id [db params]
  (let [tulos (q/seuraava-vapaa-ulkoinen-id db)]
    (:ulkoinen_id (first tulos))))

(defn hae-urakan-tierekisteriosoitteita [db params]
  (let [tulos (q/hae-urakan-tierekisteriosoitteita db {:urakka-id (:urakka-id params)})]
    tulos))

(defn paivita-raportit [db params]
  (let [_ (q/paivita-toteuma-tehtavat db)
        _ (q/paivita-toteuma-materiaalit db)
        _ (q/paivita-pohjavesialuekooste db)
        _ (q/paivita-pohjavesialueiden-suolatoteumat db)
        _ (q/paivita-materiaalin-kaytto-urakalle db params)]))

(defn geometrisoi-reittoteuma [db json]
  (let [parsittu  (cheshire/decode json)
        nopeusrajoitus (apply min
                         (map #(toimenpidekoodit-kyselyt/hae-tehtavan-nopeusrajoitus db (get-in % ["tehtava" "id"]))
                           (get-in parsittu ["reittitoteuma" "toteuma" "tehtavat"])))
        reitti (or (get-in parsittu ["reittitoteuma" "reitti"])
                   (get-in parsittu ["reittitoteumat" 0 "reittitoteuma" "reitti"]))
        pisteet (mapv (fn [{{koordinaatit "koordinaatit"
                             aika "aika"} "reittipiste"}]
                        [(get koordinaatit "x") (get koordinaatit "y") aika])
                      reitti)]
    (reittitoteuma/hae-reitti db reittitoteuma/maksimi-linnuntien-etaisyys nopeusrajoitus pisteet)))

(defn geometrisoi-tarkastus [db json]
  (let [tarkastukset (get-in (cheshire/decode json) ["tarkastukset"])
        geometriat (mapv (fn [{tarkastus "tarkastus"}]
                        (let [alkusijainti (clojure.walk/keywordize-keys (get-in tarkastus ["alkusijainti"]))
                              loppusijainti (clojure.walk/keywordize-keys (get-in tarkastus ["loppusijainti"]))
                              tr-osoite (sijainnit/hae-tierekisteriosoite db alkusijainti loppusijainti)
                              pisteet-alku (tierekisteri-haku/hae-tr-pisteella db alkusijainti)
                              pisteet-loppu (tierekisteri-haku/hae-tr-pisteella db loppusijainti)
                              geometria (if tr-osoite
                                          (:geometria tr-osoite)
                                          (sijainnit/tee-geometria alkusijainti loppusijainti))]
                          {:reitit (geo/pg->clj geometria)
                           :alkupisteet pisteet-alku
                           :loppupisteet pisteet-loppu}))
                         tarkastukset)
        reitit (mapv :reitit geometriat)
        alkupisteet (mapv :alkupisteet geometriat)
        loppupisteet (mapv :loppupisteet geometriat)

        yhtena-geometriana (reittitoteuma/yhdista-viivat reitit)]
    yhtena-geometriana
    {:reitti yhtena-geometriana
     :alkupisteet alkupisteet
     :loppupisteet loppupisteet}))

(defn geometrisoi-reittipisteet [db pisteet]
  (reittitoteuma/hae-reitti db pisteet))

(defn- urakan-rajoitusalueet [db urakka-id]
  (let [rajoitusalueet (suolarajoitus-kyselyt/hae-urakan-rajoitusaluegeometriat db {:urakka-id urakka-id})
        rajoitusalueet (map (fn [r]
                              (-> r
                                (update :tierekisteriosoite konv/lue-tr-osoite)
                                (assoc :sijainti (geo/pg->clj (:sijainti r)))))
                         rajoitusalueet)]
    rajoitusalueet))

(defn- hae-suolatoteumat
  "Älä hae tällä liian laajalta aikaväliltä"
  [db tiedot]
  (let [suolat (suolarajoitus-kyselyt/hae-suolatoteumageometriat db tiedot)
        suolat (map (fn [s]
                              (-> s
                                (assoc :sijainti (geo/pg->clj (:sijainti s)))))
                         suolat)]
    suolat))

(defn hae-urakan-geometriat
  "Osaa hakea vain hoido/mhu urakoiden ja valaistusurakoiden geometriat tällä hetkellä."
  [db tiedot]
  (let [urakka-id (Integer/parseInt (:urakka-id tiedot))
        ;; hoito ja teiden-hoito tyyppisten urakoiden geometriat ovat alueurakka -taulussa
        ;; valaistusurakoiden geometriatiedot ovat valaistusurakka -taulussa
        urakan-tyyppi (:tyyppi (first (urakat-kyselyt/hae-urakan-tyyppi db {:urakka urakka-id})))
        geometriat (cond
                     (or (= "hoito" urakan-tyyppi) (= "teiden-hoito" urakan-tyyppi))
                     (map
                       #(-> %
                          (assoc :alue (or (:alueurakka_alue %) (:urakka_alue %)))
                          (dissoc :alueurakka_alue :urakka_alue))
                       (urakat-kyselyt/hae-urakan-geometria db {:id urakka-id}))
                     (= "valaistus" urakan-tyyppi)
                     (urakat-kyselyt/hae-valaistusurakan-geometria db {:id urakka-id}))
        geometriat (map (fn [s]
                    (-> s
                      (assoc :alue (geo/pg->clj (:alue s)))))
               geometriat)]
    geometriat))

(defn- laheta-email
  "Lähetetään sähköpostia itse konfiguroidun järjestelmän kautta. Esim Gmailin."
  [ulkoinen-sahkoposti email]
  (let [vastaus (sahkoposti/laheta-ulkoisella-jarjestelmalla-viesti!
          ulkoinen-sahkoposti (:lahettaja email) (:vastaanottaja email)
          (:otsikko email) (:viesti email) nil
          (:tunnus email) (:salasana email) (:portti email))]
    ;; Palautetaan onnistunut setti, jos onnistuu, ja jos ei onnistu, niin palautetaan koko setti
    (if (= :SUCCESS (:error vastaus))
      "Viesti lähetetty"
      vastaus)))

(defn- laheta-emailapi
  "Lähetetään sähköpostia API-rajapinnan kautta. Toimii vain stg- ja tuotantoympäristöissä IP whitelistauksen vuoksi."
  [api-sahkoposti email]
  (let [vastaus (sahkoposti/laheta-viesti!
                  api-sahkoposti (:lahettaja email) (:vastaanottaja email)
                  (:otsikko email) (:viesti email) nil)
        _ (log/info "emailapin lähetyksen vastaus: " (pr-str vastaus))]
    ;; Palautetaan onnistunut setti, jos onnistuu, ja jos ei onnistu, niin palautetaan koko setti
    (if (= "Message processed" vastaus)
      "Message processed"
      {:status 400
       :error "Virhe"
       :body {:virhe "Virhe"
              :viesti vastaus}})
    vastaus))

(defn- laheta-sms
  "Lähetetään tekstiviesti (integraatioväylän ja) SMS-integraation kautta. Toimii vain stg- ja tuotantoympäristöissä IP whitelistauksen vuoksi."
  [sms tekstiviesti]
  (let [vastaus (sms/laheta sms (:puhelinnumero tekstiviesti) (:viesti tekstiviesti) "Testi" {})
        _ (log/info "tekstiviestilähetyksen vastaus: " (pr-str vastaus))]
    ;; Palautetaan onnistunut setti, jos onnistuu, ja jos ei onnistu, niin palautetaan koko setti
    (if (str/includes? (:sisalto vastaus) "OK")
      "Message processed"
      {:status 400
       :error "Virhe"
       :body {:virhe "Virhe"
              :viesti vastaus}})
    vastaus))


;; --- Päivystäjän ilmoituksen testaus -- Alkaa ---

(defn hae-ilmoitus [db ilmoitusid]
  (let [id (:id (first (tieliikenneilmoitukset-q/hae-id-ilmoitus-idlla db ilmoitusid)))
        _ (when-not id
            (log/error (format "[Testi] Ilmoitusta %s ei löytynyt tietokannasta." ilmoitusid))
            (throw (Exception. "Ilmoitusta ei löytynyt ilmoitus-id:llä")))
        ilmoitus (first
                   (konversio/sarakkeet-vektoriin
                     (into [] ilmoitukset/ilmoitus-xf
                       (tieliikenneilmoitukset-q/hae-ilmoitus db {:id id}))
                     {:kuittaus :kuittaukset}))
        ;; Normaalisti käsitellään ns. raaka" T-Loikista tullut ilmoitus, joka on hiukan eri muodossa kuin kantaan tallennettu
        ;; Tässä muokataan kannasta haetun ilmoituksen tietoja niin, että ne ovat samassa muodossa kuin T-Loikista tuleva ilmoitus
        ilmoitus (assoc ilmoitus
                   :sijainti {:x (get-in ilmoitus [:sijainti :coordinates 0])
                              :y (get-in ilmoitus [:sijainti :coordinates 1])}
                   :luokittelu {:aihe (:aihe ilmoitus) :tarkenne (:tarkenne ilmoitus)})]
    ilmoitus))

(defn- laheta-paivystaja-ilmoitus-sahkopostilla [db api-sahkoposti vastaanottajan-email {id :ilmoitusid :as ilmoitus}]
  (log/info (format "[Testi] Lähetetään ilmoitus (id: %s) sähköpostilla" id))

  (let [lahettaja "harja-ala-vastaa@vayla.fi" #_(sahkoposti/vastausosoite api-sahkoposti)
        [otsikko viesti] (tloik-sahkoposti/otsikko-ja-viesti db lahettaja ilmoitus)
        vastaus (sahkoposti/laheta-viesti! api-sahkoposti lahettaja vastaanottajan-email (str "TESTI: " otsikko) viesti {"X-Correlation-ID" id})]
    (when (not= "Message processed" vastaus)
      (log/error (format "[Testi] Ilmoituksen %s lähettämisessä sähköpostilla tapahtui virhe, vastaus integraatiolta %s" id vastaus))
      (throw (Exception. "Sähköpostin lähetys epäonnistui")))))

(defn- laheta-paivystaja-ilmoitus-sms [db sms {id :ilmoitusid :as ilmoitus} puhelinnumero]
  (log/info (format "[Testi] Lähetetään ilmoitus (id: %s) tekstiviestillä" id))

  (let [viestinumero (rand-int 100000) ; Satunnainen viestinumero
        aiheet-ja-tarkenteet (when (get-in ilmoitus [:luokittelu :aihe])
                               (palautevayla-kyselyt/hae-aiheet-ja-tarkenteet db))
        viesti (tloik-tekstiviesti/ilmoitus-tekstiviesti ilmoitus viestinumero aiheet-ja-tarkenteet)
        vastaus (sms/laheta sms puhelinnumero viesti (:ilmoitusid ilmoitus) {})]

    (when (or (not vastaus) (not (str/includes? (:sisalto vastaus) "OK")))
      (log/error (format "[Testi] Ilmoituksen %s lähettämisessä tekstiviestillä tapahtui virhe, vastaus integraatiolta: %s" id vastaus))
      (throw (Exception. "Tekstiviestin lähetys epäonnistui")))))

(defn- laheta-paivystajan-ilmoitus
  ""
  [db api-sahkoposti sms {:keys [ilmoitus-id sahkoposti puhelinnumero] :as ilmoitus-tiedot}]
  (try
    (when (and (string/blank? sahkoposti) (string/blank? puhelinnumero))
      (do
        (log/error (format "[Testi] Päivystajan ilmoitusta %s ei voida lähettää ilman sähköpostiosoitetta tai puhelinnumeroa." ilmoitus-id))
        (throw (Exception. "Päivystajan ilmoitusta ei voida lähettää ilman sähköpostiosoitetta tai puhelinnumeroa."))))

    (let [email-sensuroitu (when (string? sahkoposti)
                             (str/replace sahkoposti #"(?<=^.)[^@]*|(?<=@.).*(?=\.[^.]+$)" "***"))
          puh-sensuroitu (when (string? puhelinnumero)
                           (str/replace puhelinnumero #"\d(?=\d{4})" "*"))
          _ (log/info "[Testi] Lähetetään päivystajan ilmoitus, ilmoitus-id: " ilmoitus-id
              " sähköposti: " email-sensuroitu
              " puhelinnumero: " puh-sensuroitu)

          ilmoitus (hae-ilmoitus db ilmoitus-id)]

      (when-not (string/blank? sahkoposti)
        (laheta-paivystaja-ilmoitus-sahkopostilla db api-sahkoposti sahkoposti ilmoitus))

      (when-not (string/blank? puhelinnumero)
        (laheta-paivystaja-ilmoitus-sms db sms ilmoitus puhelinnumero))

      {:status 200
       :body {:viesti "Päivystäjän ilmoitus lähetetty"
              :ilmoitus-id ilmoitus-id}})

    (catch Exception e
      (log/error (format "[Testi] Päivystäjän ilmoituksen lähettämisessä tapahtui poikkeus: %s" e))
      {:status 500
       :error "Virhe"
       :body {:virhe "Päivystäjän ilmoituksen lähettäminen epäonnistui"
              :viesti (.getMessage e)
              :ilmoitus-id ilmoitus-id}})))

;; --- Päivystäjän ilmoituksen testaus -- Päättyy ---

(defn hae-tieturvalliusuus-geometriat
  "Kokeillaan hakea kaikki tieturvallisuusgeometriat. Jos haluat lokaalisti ajaa geometriat kantaan, päivitä polku, josta niitä
  tallennetaan. Lokaalisti tieturvallisuusgeometrioita ei välttämättä ole ajettu kantaan."
  [db tiedot]
  (let [_ (log/debug "hae-tieturvalliusuus-geometriat")
        tiedostopolku-kunnossa? false
        geometriat (tieturvallisuusverkko-kyselyt/hae-tieturvallisuusgeometriat db)
        geometriat (if (and (empty? geometriat) tiedostopolku-kunnossa?)
                     (do
                       (tieturvallisuusverkko-tuonti/vie-tieturvallisuusverkko-kantaan
                           db
                           "file:///Users/<username>/Downloads/tieturvallisuustarkastustiesto/tieturvallisuustarkastustiestö.shp")
                       ;; HAetaan generoidut geometriat
                       (tieturvallisuusverkko-kyselyt/hae-tieturvallisuusgeometriat db))
                     geometriat)

        geometriat (map (fn [s]
                           (-> s
                             (assoc :geometria (geo/pg->clj (:geometria s)))))
                      geometriat)]
    geometriat))

(defn hae-yllapitokohteen-geometriat
  "Haetaan annetun ylläpitokohde-id:n perustella ylläpitokohteen kaikkien alikohteiden geometriat"
  [db tiedot]
  (let [_ (println "hae-yllapitokohteen-geometriat :: tiedot: " (pr-str tiedot))
        geometriat (korjausluokka-kyselyt/hae-yllapitokohteen-geometriat db {:id (Integer/parseInt (:id tiedot))})
        geometriat (map (fn [s]
                         (-> s
                           (assoc :geometria (geo/pg->clj (:geometria s)))))
                       geometriat)]
    geometriat))

(defn hae-pkluokkageometriat
  "Haetaan kaikki päällysteen korjausluokkien geometriat"
  [db tiedot]
  (let [_ (println "hae-pkluokkageometriat :: tiedot: " (pr-str tiedot) "numero: " (:elynumero tiedot))
        elynumero (if (string? (:elynumero tiedot))
                    (Integer/parseInt (:elynumero tiedot))
                    (:elynumero tiedot))
        geometriat_pk1 (korjausluokka-kyselyt/hae-paallysteen-korjausluokkageometriat db {:elynumero elynumero
                                                                                          :korjausluokka "PK1"})
        geometriat_pk2 (korjausluokka-kyselyt/hae-paallysteen-korjausluokkageometriat db {:elynumero elynumero
                                                                                          :korjausluokka "PK2"})
        geometriat_pk3 (korjausluokka-kyselyt/hae-paallysteen-korjausluokkageometriat db {:elynumero elynumero
                                                                                          :korjausluokka "PK3"})
        geometriat_pk1 (map (fn [s] (-> s (assoc :geometria (geo/pg->clj (:geometria s))))) geometriat_pk1)
        geometriat_pk2 (map (fn [s] (-> s (assoc :geometria (geo/pg->clj (:geometria s))))) geometriat_pk2)
        geometriat_pk3 (map (fn [s] (-> s (assoc :geometria (geo/pg->clj (:geometria s))))) geometriat_pk3)]
    {:pk1 geometriat_pk1
     :pk2 geometriat_pk2
     :pk3 geometriat_pk3}))

(defn ilmoitus-xml
  "Tallentaa ilmoituksen annetun XML:n perusteella. Tällä simuloidaan tloikista tullutta ilmoitusta.
  Tätä voi käyttää vain lokaalisti ongelmien debuggaamisessa. Ota siis vaikka tuotannosta ilmoitus xml ja aja se tähän
  ja simuloi, että mitä se tekee."
  [db tiedot]
  (try
    (log/info "Käsitellään ilmoitus xml")
    (when-not (:xml tiedot)
      (throw (ex-info "XML-sisältö puuttuu" {:tiedot tiedot})))

    (let [ilmoitus (ilmoitussanoma/lue-viesti (:xml tiedot))
          _ (log/debug "Parsittu ilmoitus:" (pr-str ilmoitus))
          urakka (tloik-ilmoitukset/hae-urakka db ilmoitus)]

      (when-not urakka
        (throw (ex-info "Urakkaa ei löytynyt ilmoitukselle"
                 {:ilmoitus-id (:ilmoitus-id ilmoitus)
                  :sijainti (:sijainti ilmoitus)})))

      (let [ilmoitus-id (ilmoitus-kasittely/tallenna-ilmoitus db (:id urakka) ilmoitus)]
        (log/info (format "Ilmoitus tallennettu onnistuneesti. Ilmoitus-id: %s, Urakka-id: %s"
                    ilmoitus-id (:id urakka)))
        {:status "OK"
         :ilmoitus-id ilmoitus-id
         :urakka-id (:id urakka)
         :urakka-nimi (:nimi urakka)}))

    (catch Exception e
      (log/error e "Virhe käsiteltäessä debug-ilmoitusta")
      {:status "VIRHE"
       :viesti (.getMessage e)
       :virhe (pr-str e)})))

(defn hae-tierekisteriosoite-koordinaateista
  "Palauttaa tierekisteriosoitteen annetuista alkusijainti/loppusijainti-koordinaateista.
  Käyttää samaa päättelylogiikkaa kuin integraatioiden laatupoikkeama-API."
  [db {:keys [alkusijainti loppusijainti]}]
  (let [tr-osoite (sijainnit/hae-tierekisteriosoite db alkusijainti loppusijainti)]
    {:tr-osoite tr-osoite
     :alkusijainti alkusijainti
     :loppusijainti loppusijainti}))

(defn vaadi-jvh! [palvelu-fn]
  (fn [user payload]
    (if-not (roolit/jvh? user)
      (log/error "DEBUG näkymän palvelua yritti käyttää ei-jvh: " user)
      (do
        (oikeudet/merkitse-oikeustarkistus-tehdyksi!)
        (palvelu-fn payload)))))


(defrecord Debug []
  component/Lifecycle
  (start [{db :db
           ulkoinen-sahkoposti :ulkoinen-sahkoposti
           api-sahkoposti :api-sahkoposti
           sms :sms
           http :http-palvelin :as this}]
    (http/julkaise-palvelut
      http
      :debug-hae-toteuman-reitti-ja-pisteet
      (vaadi-jvh! (partial #'hae-toteuman-reitti-ja-pisteet db))
      :debug-geometrisoi-reittitoteuma
      (vaadi-jvh! (partial #'geometrisoi-reittoteuma db))
      :debug-geometrisoi-tarkastus
      (vaadi-jvh! (partial #'geometrisoi-tarkastus db))
      :debug-geometrisoi-reittipisteet
      (vaadi-jvh! (partial #'geometrisoi-reittipisteet db))
      :debug-hae-tyokonehavainto-reittipisteet
      (vaadi-jvh! (partial #'hae-tyokonehavainto-reitti db))
      :debug-hae-seuraava-vapaa-ulkoinen-id
      (vaadi-jvh! (partial #'hae-seuraava-vapaa-ulkoinen-id db))
      :debug-hae-urakan-tierekisteriosoitteita
      (vaadi-jvh! (partial #'hae-urakan-tierekisteriosoitteita db))
      :debug-paivita-raportit
      (vaadi-jvh! (partial #'paivita-raportit db))
      :debug-hae-rajoitusalueet
      (vaadi-jvh! (partial #'urakan-rajoitusalueet db))
      :debug-hae-paivan-suolatoteumat
      (vaadi-jvh! (partial #'hae-suolatoteumat db))
      :debug-hae-urakan-geometriat
      (vaadi-jvh! (partial #'hae-urakan-geometriat db))
      :debug-laheta-email
      (vaadi-jvh! (partial #'laheta-email ulkoinen-sahkoposti))
      :debug-laheta-emailapi
      (vaadi-jvh! (partial #'laheta-emailapi api-sahkoposti))
      :debug-laheta-tekstiviesti
      (vaadi-jvh! (partial #'laheta-sms sms))
      :debug-laheta-paivystajan-ilmoitus
      (vaadi-jvh! (partial #'laheta-paivystajan-ilmoitus db api-sahkoposti sms))
      :debug-hae-tieturvalliusuus-geometriat
      (vaadi-jvh! (partial #'hae-tieturvalliusuus-geometriat db))
      :debug-hae-yllapitokohteen-geometriat
      (vaadi-jvh! (partial #'hae-yllapitokohteen-geometriat db))
      :debug-hae-pkluokkageometriat
      (vaadi-jvh! (partial #'hae-pkluokkageometriat db))
      :debug-ilmoitus-xml
      (vaadi-jvh! (partial #'ilmoitus-xml db))
      :debug-hae-tierekisteriosoite-koordinaateista
      (vaadi-jvh! (partial #'hae-tierekisteriosoite-koordinaateista db)))
    this)

  (stop [{http :http-palvelin :as this}]
    (http/poista-palvelut
      http
      :debug-hae-toteuman-reitti-ja-pisteet
      :debug-geometrisoi-reittitoteuma
      :debug-geometrisoi-tarkastus
      :debug-geometrisoi-reittipisteet
      :debug-hae-tyokonehavainto-reittipisteet
      :debug-hae-seuraava-vapaa-ulkoinen-id
      :debug-hae-urakan-tierekisteriosoitteita
      :debug-paivita-raportit
      :debug-hae-rajoitusalueet
      :debug-hae-paivan-suolatoteumat
      :debug-hae-urakan-geometriat
      :debug-laheta-email
      :debug-laheta-emailapi
      :debug-laheta-tekstiviesti
      :debug-hae-tieturvalliusuus-geometriat
      :debug-hae-yllapitokohteen-geometriat
      :debug-hae-pkluokkageometriat
      :debug-ilmoitus-xml
      :debug-hae-tierekisteriosoite-koordinaateista)
    this))
