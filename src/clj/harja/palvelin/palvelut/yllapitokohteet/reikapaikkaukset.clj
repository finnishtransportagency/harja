(ns harja.palvelin.palvelut.yllapitokohteet.reikapaikkaukset
  "Reikäpaikkausnäkymän palvelut"
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [throw+]]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.reikapaikkaukset :as q]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]))


(defn hae-reikapaikkaukset [db kayttaja {:keys [urakka-id tr aikavali]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/hae-reikapaikkaukset db {:tie (:numero tr)
                              :aosa (:alkuosa tr)
                              :aet (:alkuetaisyys tr)
                              :losa (:loppuosa tr)
                              :let (:loppuetaisyys tr)
                              :alkuaika (when
                                          (and
                                            (some? aikavali)
                                            (first aikavali))
                                          (konversio/sql-date (first aikavali)))
                              :loppuaika (when
                                           (and
                                             (some? aikavali)
                                             (second aikavali))
                                           (konversio/sql-date (second aikavali)))
                              :urakka-id urakka-id}))


(defn hae-tyomenetelmat [db kayttaja {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/hae-kaikki-tyomenetelmat db))


(defn poista-reikapaikkaus
  "Yksittäisen reikäpaikkauksen poisto"
  [db kayttaja
   {:keys [kayttaja-id urakka-id ulkoinen-id]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (q/poista-reikapaikkaustoteuma! db {:kayttaja-id kayttaja-id
                                      :ulkoinen-id ulkoinen-id
                                      :urakka-id urakka-id}))


(defn- luo-tai-paivita-reikapaikkaus
  "Lisää tai päivittää olemassa olevan paikkauksen kantaan"
  [db kayttaja-id urakka-id paikkaus]
  ;; Destruktoi paikkaus
  (let [{:keys [tunniste tie aosa aet losa let pvm menetelma
                tyomenetelma-id maara yksikko kustannus alkuaika loppuaika]} paikkaus
        ;; Koosta parametrit, alku/loppuaika reikäpaikkauksilla tällä hetkellä samoja (loppuaikaa ei ole speksattu)
        ;; joka on 'pvm' kun data tuodaan Excelistä, frontilta alkuaika/loppuaika
        parametrit {:luoja-id kayttaja-id
                    :alkuaika (konversio/sql-date (or alkuaika (pvm/->pvm pvm)))
                    :loppuaika (konversio/sql-date (or loppuaika (pvm/->pvm pvm)))
                    :urakka-id urakka-id
                    :ulkoinen-id tunniste
                    :tie tie
                    :muokkaaja-id kayttaja-id
                    :aosa aosa
                    :aet aet
                    :losa losa
                    :let let
                    :tyomenetelma-id tyomenetelma-id
                    :tyomenetelma menetelma
                    :maara maara
                    :kustannus kustannus
                    :yksikko yksikko}
        ;; Onko paikkaus kannassa, tyhjä tulos palauttaa (), johon seq lyö nilliä, joten boolean -> seq -> tulos 
        paikkaus-olemassa? (boolean (seq (q/hae-reikapaikkaus db {:ulkoinen-id tunniste
                                                                  :urakka-id urakka-id})))]
    ;; Jos paikkausta ei ole olemassa -> lisätään se, muuten kutsutaan UPDATE 
    (if paikkaus-olemassa?
      (q/paivita-reikapaikkaus! db parametrit)
      (q/lisaa-reikapaikkaus! db parametrit))))


(defn tallenna-reikapaikkaus
  "Yksittäisen reikäpaikkauksen muokkauksen tallennus (käyttöliittymän kautta)"
  [db {:keys [id] :as kayttaja} {:keys [urakka-id] :as paikkaus}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
  (luo-tai-paivita-reikapaikkaus db id urakka-id paikkaus))


(defn tallenna-reikapaikkaukset
  "Tallentaa kaikki Excelin reikäpaikkaukset valitulle urakalle (Excel-tuonti)"
  [db {:keys [id]} urakka-id reikapaikkaukset]
  (doseq [paikkaus reikapaikkaukset]
    (luo-tai-paivita-reikapaikkaus db id urakka-id paikkaus)))


(defn- kasittele-excel [db urakka-id kayttaja pyynto]
  (let [workbook (xls/load-workbook-from-file (:path (bean (get-in pyynto [:params "file" :tempfile]))))
        reikapaikkaukset (p-excel/parsi-syotetyt-reikapaikkaukset workbook)
        ;; Kerää kaikki parsinnan virheet
        virheet (reduce (fn [acc arvo]
                          (if-let [virhe (get arvo :virhe)]
                            (conj acc virhe)
                            acc))
                  []
                  reikapaikkaukset)
        virheita? (false? (empty? virheet))
        status (if virheita? 400 200)
        body (if virheita? virheet
               ;; Jos virheitä ei ollut, tallenna kaikki rivit tietokantaan
               (tallenna-reikapaikkaukset db kayttaja urakka-id reikapaikkaukset))]

    ;; Palauta status, body sisältää virheet jos virheitä tuli
    ;; Yritetty palauttaa päivitetty lista jos virheitä ei tullut, mutta menee enkoodauksen kanssa vääntämiseksi, helpompi kutsua tuckilla päivitetty lista
    {:status status
     :headers {"Content-Type" "application/json; charset=UTF-8"}
     :body (cheshire/encode body)}))


(defn vastaanota-excel [db pyynto]
  (let [urakka-id (Integer/parseInt (get (:params pyynto) "urakka-id"))
        kayttaja (:kayttaja pyynto)]
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (if (and
          (not (nil? urakka-id))
          (not (nil? kayttaja)))
      (do
        (oikeudet/vaadi-kirjoitusoikeus oikeudet/urakat-paikkaukset-toteumat kayttaja urakka-id)
        (kasittele-excel db urakka-id kayttaja pyynto))
      (throw+ {:type "Error" :virheet [{:koodi "ERROR" :viesti "Ladatussa tiedostossa virhe."}]}))))


(defrecord Reikapaikkaukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db excel-vienti] :as this}]
    ;; Haku
    (julkaise-palvelu http-palvelin
      :hae-reikapaikkaukset (fn [user tiedot] (hae-reikapaikkaukset db user tiedot)))
    ;; Toteuman tallennus
    (julkaise-palvelu http-palvelin
      :tallenna-reikapaikkaus (fn [user tiedot] (tallenna-reikapaikkaus db user tiedot)))
    ;; Toteuman poisto
    (julkaise-palvelu http-palvelin
      :poista-reikapaikkaus (fn [user tiedot] (poista-reikapaikkaus db user tiedot)))
    ;; Työmenetelmät
    (julkaise-palvelu http-palvelin
      :hae-tyomenetelmat (fn [user tiedot] (hae-tyomenetelmat db user tiedot)))
    ;; Excel tuonti
    (julkaise-palvelu http-palvelin
      :lue-reikapaikkauskohteet-excelista (wrap-multipart-params
                                            (fn [pyynto] (vastaanota-excel db pyynto)))
      {:ring-kasittelija? true})
    (when excel-vienti
      (excel-vienti/rekisteroi-excel-kasittelija! excel-vienti :reikapaikkaukset-urakalle-excel
        {:funktio (partial #'p-excel/vie-reikapaikkaukset-exceliin db hae-reikapaikkaukset)
         :optiot {:pohja (io/file (io/resource "public/excel/harja_reikapaikkausten_pohja.xlsx"))}}))

    this)

  (stop [{:keys [http-palvelin excel-vienti] :as this}]
    (poista-palvelut http-palvelin
      :hae-tyomenetelmat
      :hae-reikapaikkaukset
      :poista-reikapaikkaus
      :tallenna-reikapaikkaus
      :lue-reikapaikkauskohteet-excelista)
    (when excel-vienti
      (excel-vienti/poista-excel-kasittelija! excel-vienti :reikapaikkaukset-urakalle-excel))
    this))
