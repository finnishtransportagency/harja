(ns harja.palvelin.palvelut.reikapaikkaukset
  "Reikäpaikkausnäkymän palvelut"
  ;; TODO.. lisätty valmiiksi requireja, poista myöhemmin turhat 
  (:require [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [clj-time.coerce :as c]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as urakka-kyselyt]
            [harja.domain.roolit :as roolit]
            [slingshot.slingshot :refer [throw+]]
            [dk.ative.docjure.spreadsheet :as xls]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.reikapaikkaukset :as q]
            [harja.palvelin.palvelut.yllapitokohteet.paikkauskohteet-excel :as p-excel]))


(defn hae-reikapaikkaukset [db _user tiedot]
  ;; TODO lisää oikeustarkastus! 
  (oikeudet/ei-oikeustarkistusta!)
  (q/hae-reikapaikkaukset db {:urakka-id (:urakka-id tiedot)}))


(defn hae-tyomenetelmat [db _user]
  ;; TODO lisää oikeustarkastus! 
  (oikeudet/ei-oikeustarkistusta!)
  (q/hae-kaikki-tyomenetelmat db))


(defn tallenna-reikapaikkaukset
  "Tallentaa kaikki Excelin reikäpaikkaukset valitulle urakalle"
  [db {:keys [id] :as kayttaja} urakka-id reikapaikkaukset]
  ;; TODO lisää oikeustarkastus! 
  (oikeudet/ei-oikeustarkistusta!)
  (doseq [{:keys [tunniste tie aosa aet losa let menetelma maara yksikko kustannus]} reikapaikkaukset]
    (q/luo-tai-paivita-reikapaikkaus! db {:luoja_id id
                                          :urakka_id urakka-id
                                          :ulkoinen_id tunniste
                                          :tie tie
                                          :aosa aosa
                                          :aet aet
                                          :losa losa
                                          :let let
                                          :tyomenetelma menetelma
                                          :paikkaus_maara maara
                                          :kustannus kustannus
                                          :yksikko yksikko})))


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
  ;; TODO lisää oikeustarkastus! 
  (oikeudet/ei-oikeustarkistusta!)
  (let [urakka-id (Integer/parseInt (get (:params pyynto) "urakka-id"))
        kayttaja (:kayttaja pyynto)]
    ;; Tarkistetaan, että kutsussa on mukana urakka ja kayttaja
    (if (and 
          (not (nil? urakka-id))
          (not (nil? kayttaja)))
      (kasittele-excel db urakka-id kayttaja pyynto)
      (throw+ {:type "Error" :virheet [{:koodi "ERROR" :viesti "Ladatussa tiedostossa virhe."}]}))))


(defrecord Reikapaikkaukset []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    ;; Haku
    (julkaise-palvelu http-palvelin
      :hae-reikapaikkaukset (fn [user tiedot] (hae-reikapaikkaukset db user tiedot)))
    ;; Työmenetelmät
    (julkaise-palvelu http-palvelin
      :hae-tyomenetelmat (fn [user _tiedot] (hae-tyomenetelmat db user)))
    ;; Excel tuonti
    (julkaise-palvelu http-palvelin
      :lue-reikapaikkauskohteet-excelista (wrap-multipart-params
                                            (fn [pyynto] (vastaanota-excel db pyynto)))
      {:ring-kasittelija? true})
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-tyomenetelmat
      :hae-reikapaikkaukset
      :lue-reikapaikkauskohteet-excelista)
    this))
