(ns harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-palvelu
  "Talvihoitoreittien UI:n endpointit."
  (:require [clojure.java.jdbc :as jdbc]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut julkaise-palvelu poista-palvelut transit-vastaus]]
            [harja.kyselyt.talvihoitoreitit :as talvihoitoreitit-q]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [slingshot.slingshot :refer [throw+ try+]]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.palvelut.laadunseuranta.talvihoitoreitit-excel :as t-excel]))

(defn hae-urakan-talvihoitoreitit [db user {:keys [urakka-id]}]
  (log/debug "hae-urakan-talvihoitoreitit ::user" user)
  ;; Estä muut, kuin järjestelmävastaavat näkemästä talvihoitoreittejä
  (when (or (contains? (:roolit user) "Jarjestelmavastaava")
          (contains? (:roolit user) "ELY_Urakanvalvoja"))
    ;;(oikeudet/vaadi-lukuoikeus oikeudet/urakat-laadunseuranta-talvihoitoreititys user urakka-id) ;; Lisätään muillekin kuin jvh:lle myöhemmin
    (talvihoitoreitit-q/hae-ja-muokkaa-talvihoitoreitit db urakka-id)))

(defn kasittele-excel [db urakka-id kayttaja req workbook]
  (let [;; Excelistä löytyneille talvihoitoreitteille koostetaan atomeihin statuksia. Päivittyneet omaansa, uudet lisäykset omaansa
        ;; ja virheet omaansa
        lisatyt-atom (atom [])
        paivitetyt-atom (atom [])
        virheet-atom (atom [])
        ;; Lue excelistä kaikki tiedot talteen -- Testiä varten mahdollista workbookin antaminen parametrina
        workbook (if (nil? workbook)
                   (xls/load-workbook-from-file (:path (bean (get-in req [:params "file" :tempfile]))))
                   workbook)
        talvihoitoreitit (try+
                           (t-excel/lue-talvihoitoreitit-excelista workbook)
                           (catch [:type :validaatiovirhe] {:keys [virheet]}
                             (swap! virheet-atom conj virheet)
                             nil))
        
        ;; Käsittele jokainen talvihoitoreitti itsenäisessä loopissa
        _ (dorun (for [t talvihoitoreitit]
                   (jdbc/with-db-transaction [db db]
                     (let [;; Varmista, että talvihoitoreittiä ei ole jo olemassa
                           talvihoitoreitti-db (first (talvihoitoreitit-q/hae-talvihoitoreitti-ulkoisella-idlla db {:urakka_id urakka-id
                                                                                                                    :ulkoinen_id (:tunniste t)}))
                           ;; Talvihoitoreitillä voi olla virheellisiä tieosoitteita
                           tieosoite-virheet (talvihoitoreitit-q/validoi-talvihoitoreitin-sijainnit db t)

                           _ (when-not (empty? tieosoite-virheet)
                               (swap! virheet-atom conj tieosoite-virheet))

                           ;; Etsitään leikkaavat geometriat vain, jos ei ole virheitä ja ei olla päivittämässä olemassa olevaa
                           leikkaavat-geometriat (when (and (empty? tieosoite-virheet) (nil? talvihoitoreitti-db))
                                                   (talvihoitoreitit-q/leikkaavat-geometriat db t urakka-id))

                           ;; Tallennetaan mahdolliset virheet atomiin
                           _ (swap! virheet-atom conj (conj leikkaavat-geometriat))

                           ;; Jos talvihoitoreittiä ei löydy tietokannasta, niin tallennetaan se uutena
                           talvihoitoreitti-id (when (and (nil? talvihoitoreitti-db) (empty? tieosoite-virheet) (empty? leikkaavat-geometriat))
                                                 (:id (talvihoitoreitit-q/lisaa-talvihoitoreitti-tietokantaan
                                                        db t urakka-id (:id kayttaja))))

                           ;; Jos talvihoitoreitti löytyy tietokannasta, niin päivitetään
                           _ (when (and talvihoitoreitti-db (empty? tieosoite-virheet) (empty? leikkaavat-geometriat))
                               (talvihoitoreitit-q/paivita-talvihoitoreitti-tietokantaan db t urakka-id (:id kayttaja))
                               (swap! paivitetyt-atom conj (:reittinimi t)))]

                       ;; Jos talvihoitoreitin perustiedot on onnistuneesti tallennettu, niin tallennetaan myös kalustot ja reitit
                       (when (and (nil? talvihoitoreitti-db) talvihoitoreitti-id)
                         (do
                           (talvihoitoreitit-q/lisaa-reitit db talvihoitoreitti-id t)
                           (swap! lisatyt-atom conj (:reittinimi t))))))))

        vastaus (if (and (empty? @lisatyt-atom) (empty? @virheet-atom) (empty? @paivitetyt-atom))
                  {:virheet [{:virheet "Excelistä ei löydetty talvihoitoreittejä."}]}
                  {:onnistuneet @lisatyt-atom
                   :paivitetyt @paivitetyt-atom
                   :virheet (flatten @virheet-atom)})]
    ;; Tiedoston lukemisesta tullut request vaatii jostain syystä transit-vastauksen. JSON olisi default vaihtoehto,
    ;; mutta koska transitiksi pakottaminen onnistuu, niin kutsujalle kelpaa JSONin kanssa myös transit.
    (transit-vastaus vastaus)))

(defn vastaanota-excel [db request]
  (or (contains? (:roolit (:kayttaja request)) "Jarjestelmavastaava")
    (contains? (:roolit (:kayttaja request)) "ELY_Urakanvalvoja"))
  #_ (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-talvihoitoreititys
    (:kayttaja request)
    (Integer/parseInt (get (:params request) "urakka-id")))
  (let [urakka-id (Integer/parseInt (get (:params request) "urakka-id"))
        kayttaja (:kayttaja request)]
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (if (and urakka-id kayttaja)
      (kasittele-excel db urakka-id kayttaja request nil)
      (throw+ {:type "Error"
               :virheet [{:koodi "ERROR" :viesti "Ladatussa tiedostossa virhe."}]}))))

(defn poista-talvihoitoreitti [db user {:keys [urakka-id ulkoinenid]}]
  (if (or (contains? (:roolit user) "Jarjestelmavastaava")
        (contains? (:roolit user) "ELY_Urakanvalvoja"))
    ;;(oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-laadunseuranta-talvihoitoreititys user urakka-id) ;; Lisätään muillekin kuin jvh:lle myöhemmin
    (let [urakka-id (konv/konvertoi->int urakka-id)
          ;; Varmistetaan, että talvihoitoreitti on olemassa
          tr (talvihoitoreitit-q/hae-talvihoitoreitti-ulkoisella-idlla db {:urakka_id urakka-id :ulkoinen_id ulkoinenid})
          _ (if tr
              (talvihoitoreitit-q/poista-talvihoitoreitti! db {:ulkoinen_id ulkoinenid
                                                               :urakka_id urakka-id})
              (throw+ {:type "Error"
                       :virheet [{:koodi "ERROR" :viesti "Ei löydy poistettavaa talvihoitoreittiä. Tarkista tiedot."}]}))]
      {:onnistui "Talvihoitoreitti poistettu onnistuneesti."})
    (throw+ {:type "Error"
             :virheet [{:koodi "ERROR" :viesti "Ei käyttöoikeuksia."}]})))

(defrecord Talvihoitoreitit []
  component/Lifecycle
  (start [{:keys [http-palvelin db excel-vienti] :as this}]

    (julkaise-palvelut http-palvelin :hae-urakan-talvihoitoreitit
      (fn [user tiedot]
        (hae-urakan-talvihoitoreitit db user tiedot)))

    (julkaise-palvelu http-palvelin :lue-talvihoitoreitit-excelista
      (wrap-multipart-params (fn [request] (vastaanota-excel db request)))
      {:ring-kasittelija? true})

    (julkaise-palvelu http-palvelin :poista-talvihoitoreitti
      (fn [user tiedot]
        (poista-talvihoitoreitti db user tiedot)))

    (when excel-vienti
      (excel-vienti/rekisteroi-excel-kasittelija! excel-vienti :lataa-talvihoitoreitit-exceliin
        (partial #'t-excel/lataa-talvihoitoreitit-exceliin db)))

    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakan-talvihoitoreitit
      :lue-talvihoitoreitit-excelista
      :poista-talvihoitoreitti)
    this))
