(ns harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-palvelu
  "Talvihoitoreittien UI:n endpointit."
  (:require [cheshire.core :as cheshire]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [harja.domain.hoitoluokat :as hoitoluokat]
            [harja.domain.tierekisteri :as tr]
            [harja.domain.tierekisteri.validointi :as tr-validointi]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.kyselyt.talvihoitoreitit :as talvihoitoreitit-q]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [slingshot.slingshot :refer [throw+ try+]]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-excel :as t-excel]))

(defn hae-urakan-talvihoitoreitit [db user {:keys [urakka-id]}]
  ;; TODO: HOX muuta roolit excel
  #_(oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-talvihoitoreitit user urakka-id)
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-tarkastukset user urakka-id)
  (let [urakan-talvihoitoreitit (talvihoitoreitit-q/hae-urakan-talvihoitoreitit db {:urakka_id urakka-id})
        _ (log/debug "hae-urakan-talvihoitoreitit :: urakan-talvihoitoreitit" urakan-talvihoitoreitit)
        talvihoitoreitit (mapv (fn [rivi]
                                 (let [;; Hae reitit erikseen
                                       reitit (talvihoitoreitit-q/hae-sijainti-talvihoitoreitille db {:talvihoitoreitti_id (:id rivi)})
                                       ;; Kalustot mäpätään eri taulusta array_aggregateksi. Se pitää purkaa psql vector objectista clojuren ymmärtämään muotoon
                                       reitit (mapv (fn [rivi]
                                                      (-> rivi
                                                        (update :kalustot
                                                          (fn [rivi]
                                                            (mapv
                                                              #(konv/pgobject->map % :kalustotyyppi :string :kalustomaara :long)
                                                              (konv/pgarray->vector rivi))))))
                                                reitit)

                                       ;; Formatoi käyttöliittymälle valmiiksi
                                       reitit (map (fn [r]
                                                     (-> r
                                                       (assoc :sijainti (:reitti r))
                                                       (assoc :formatoitu-tr (tr/tr-osoite-moderni-fmt
                                                                               (:tie r) (:alkuosa r) (:alkuetaisyys r)
                                                                               (:loppuosa r) (:loppuetaisyys r)))
                                                       (dissoc :reitti))) reitit)

                                       ;; Jaotellaan reitti hoitoluokittan UI:ta varten
                                       hoitoluokkat (vec (vals (group-by :hoitoluokka (map (fn [r]
                                                                                             (dissoc r :sijainti :tie :alkuosa
                                                                                               :alkuetaisyys :loppuosa :loppuetaisyys
                                                                                               :id :formatoitu-tr)) reitit))))

                                       ;; Hae kaikki kalustot
                                       reitin-kalustot (conj (flatten (map (fn [r] (:kalustot r)) reitit)))
                                       ;; Groupataan reitin kalustot kalustotyypin mukaan
                                       groupatut-kalustot (group-by :kalustotyyppi reitin-kalustot)

                                       kalustot (mapv (fn [avain]
                                                        (let [kalustotyyppi avain
                                                              kalustomaara (apply + (map #(:kalustomaara %) (get groupatut-kalustot avain)))]
                                                          {:kalustotyyppi kalustotyyppi
                                                           :kalustomaara kalustomaara}))
                                                  (keys groupatut-kalustot))
                                       ;; Lasketaan jokaiselle hoitoluokalle pituus
                                       hoitoluokkat (mapv (fn [hoitoluokka-vec]
                                                            {:hoitoluokka (:hoitoluokka (first hoitoluokka-vec))
                                                             :pituus (reduce + (map :laskettu_pituus hoitoluokka-vec))})
                                                      hoitoluokkat)
                                       rivi (-> rivi
                                              (assoc :reitit reitit)
                                              (assoc :hoitoluokat hoitoluokkat)
                                              (assoc :kalustot kalustot))]
                                   rivi))
                           urakan-talvihoitoreitit)]
    talvihoitoreitit))

(defn- kasittele-excel [db urakka-id kayttaja req]
  (let [;; Excelistä löytyneille talvihoitoreitteille koostetaan atomeihin statuksia. Päivittyneet omaansa, uudet lisäykset omaansa
        ;; ja virheet omaansa
        lisatyt-atom (atom [])
        paivitetyt-atom (atom [])
        virheet-atom (atom [])
        ;; Lue excelistä kaikki tiedot talteen
        workbook (xls/load-workbook-from-file (:path (bean (get-in req [:params "file" :tempfile]))))
        talvihoitoreitit (try+
                           (t-excel/lue-talvihoitoreitit-excelista workbook)
                           (catch [:type :validaatiovirhe] {:keys [virheet]}
                             (swap! virheet-atom conj virheet)
                             nil))
        _ (log/debug "kasittele-excel :: talvihoitoreitit:" talvihoitoreitit)

        ;; Käsittele jokainen talvihoitoreitti itsenäisessä loopissa
        _ (doseq [t talvihoitoreitit
                  :let [;; Varmista, että talvihoitoreittiä ei ole jo olemassa
                        talvihoitoreitti-db (first (talvihoitoreitit-q/hae-talvihoitoreitti-ulkoisella-idlla db {:urakka_id urakka-id
                                                                                                                 :ulkoinen_id (:tunniste t)}))

                        ;; Talvihoitoreitillä voi olla virheellisiä tieosoitteita
                        tieosoite-virheet (reduce (fn [virheet r]
                                                    (let [validointivastaus (tr-validointi/validoi-tieosoite virheet (:tie r) (:aosa r) (:losa r) (:aet r) (:let r))
                                                          v (if-not (empty? validointivastaus)
                                                              {:virheet (str "Reitin " (:reittinimi t) ", virheet: " (str/join (vec (mapcat identity validointivastaus))))}
                                                              nil)
                                                          _ (when v (swap! virheet-atom conj v))]
                                                      v))
                                            [] (:sijainnit t))


                        ;; Etsitään leikkaavat geometriat vain, jos ei ole virheitä ja ei olla päivittämässä olemassa olevaa
                        leikkaavat-geometriat (when (and (empty? tieosoite-virheet) (nil? talvihoitoreitti-db))
                                                (reduce (fn [leikkaavat-geometriat r]
                                                          (let [leikkaavat (talvihoitoreitit-q/hae-leikkaavat-geometriat db
                                                                             {:urakka_id urakka-id
                                                                              :tie (:tie r)
                                                                              :aosa (:aosa r)
                                                                              :losa (:losa r)
                                                                              :aet (:aet r)
                                                                              :let (:let r)})]
                                                            (if-not (empty? leikkaavat)
                                                              (conj leikkaavat-geometriat
                                                                {:leikkaavat (format "Tieosoite: %s leikkaa jo olemassa olevan talvihoitoreitin kanssa."
                                                                               (tr/tr-osoite-moderni-fmt
                                                                                 (:tie r) (:aosa r) (:aet r)
                                                                                 (:losa r) (:let r)))})
                                                              leikkaavat-geometriat)))
                                                  [] (:sijainnit t)))

                        ;; Tallennetaan mahdolliset virheet atomiin
                        _ (swap! virheet-atom conj (conj leikkaavat-geometriat))

                        ;; Jos talvihoitoreittiä ei löydy tietokannasta, niin tallennetaan se uutena
                        talvihoitoreitti-id (when (and (nil? talvihoitoreitti-db) (empty? tieosoite-virheet) (empty? leikkaavat-geometriat))
                                              (:id (talvihoitoreitit-q/lisaa-talvihoitoreitti-tietokantaan
                                                     db t urakka-id (:id kayttaja))))

                        ;; Jos talvihoitoreitti löytyy tietokannasta, niin päivitetään
                        _ (when (and talvihoitoreitti-db (empty? tieosoite-virheet) (empty? leikkaavat-geometriat))
                            (talvihoitoreitit-q/paivita-talvihoitoreitti-tietokantaan db t urakka-id (:id kayttaja))
                            (swap! paivitetyt-atom conj (:reittinimi t)))]]

            ;; Jos talvihoitoreitin perustiedot on onnistuneesti tallennettu, niin tallennetaan myös kalustot ja reitit
            (when (and (nil? talvihoitoreitti-db) talvihoitoreitti-id)
              (do
                (talvihoitoreitit-q/lisaa-kalustot-ja-reitit db talvihoitoreitti-id t)
                (swap! lisatyt-atom conj (:reittinimi t)))))

        vastaus (if (and (empty? @lisatyt-atom) (empty? @virheet-atom) (empty? @paivitetyt-atom))
                  {:virheet ["Excelistä ei löydetty talvihoitoreittejä."]}
                  {:onnistuneet @lisatyt-atom
                   :paivitetyt @paivitetyt-atom
                   :virheet (flatten @virheet-atom)})]
    ;; Tiedoston lukemisesta tullut request vaatii jostain syystä transit-vastauksen. JSON olisi default vaihtoehto,
    ;; mutta koska transitiksi pakottaminen onnistuu, niin kutsujalle kelpaa JSONin kanssa myös transit.
    (transit-vastaus vastaus)))

(defn vastaanota-excel [db request]
  (log/debug "vastaanota-excel :: request" request)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset
    (:kayttaja request)
    (Integer/parseInt (get (:params request) "urakka-id")))
  (let [urakka-id (Integer/parseInt (get (:params request) "urakka-id"))
        kayttaja (:kayttaja request)]
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (if (and (not (nil? urakka-id))
          (not (nil? kayttaja)))
      (kasittele-excel db urakka-id kayttaja request)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Ladatussa tiedostossa virhe."}]}))))

(defrecord Talvihoitoreitit []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]

    (julkaise-palvelut http-palvelin :hae-urakan-talvihoitoreitit
      (fn [user tiedot]
        (hae-urakan-talvihoitoreitit db user tiedot)))

    (julkaise-palvelu http-palvelin :lue-talvihoitoreitit-excelista
      (wrap-multipart-params (fn [request] (vastaanota-excel db request)))
      {:ring-kasittelija? true})
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakan-talvihoitoreitit
      :lue-talvihoitoreitit-excelista)
    this))
