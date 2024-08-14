(ns harja.palvelin.palvelut.debug
  "Erinäisiä vain JVH:lle tarkoitettuja palveluita, joilla voi selvitellä
  eri tilanteita, esim. TR-osiossa."
  (:require [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :as http]
            [harja.kyselyt.debug :as q]

            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.suolarajoitus-kyselyt :as suolarajoitus-kyselyt]
            [harja.kyselyt.urakat :as urakat-kyselyt]
            [harja.kyselyt.urakan-toimenpiteet :as urakan-toimenpiteet-kyselyt]
            [cheshire.core :as cheshire]
            [harja.palvelin.integraatiot.api.reittitoteuma :as reittitoteuma]
            [harja.palvelin.palvelut.tierekisteri-haku :as tierekisteri-haku]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
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
                         (map #(urakan-toimenpiteet-kyselyt/hae-tehtavan-nopeusrajoitus db (get-in % ["tehtava" "id"]))
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
  "Lähetetään tekstiviesti (integraatioväylän ja) LinkMobilen LinkSMS-palvelun kautta. Toimii vain stg- ja tuotantoympäristöissä IP whitelistauksen vuoksi."
  [sms tekstiviesti]
  (let [vastaus (sms/laheta sms (:puhelinnumero tekstiviesti) (:viesti tekstiviesti)  {"X-Correlation-ID" "Testi"})
        _ (log/info "tekstiviestilähetyksen vastaus: " (pr-str vastaus))]
    ;; Palautetaan onnistunut setti, jos onnistuu, ja jos ei onnistu, niin palautetaan koko setti
    (if (str/includes? (:sisalto vastaus) "OK")
      "Message processed"
      {:status 400
       :error "Virhe"
       :body {:virhe "Virhe"
              :viesti vastaus}})
    vastaus))

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
           sms :labyrintti
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
      :debug-hae-tieturvalliusuus-geometriat
      (vaadi-jvh! (partial #'hae-tieturvalliusuus-geometriat db))
      :debug-hae-yllapitokohteen-geometriat
      (vaadi-jvh! (partial #'hae-yllapitokohteen-geometriat db))
      :debug-hae-pkluokkageometriat
      (vaadi-jvh! (partial #'hae-pkluokkageometriat db)))
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
      :debug-hae-pkluokkageometriat)
    this))
