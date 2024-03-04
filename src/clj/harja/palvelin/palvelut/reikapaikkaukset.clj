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
  (let [_ (log/debug "hae-reikapaikkaukset :: tiedot" (pr-str tiedot))
        vastaus (q/hae-reikapaikkaukset db {:urakka-id (:urakka-id tiedot)})]
    vastaus))


(defn- kasittele-excel [db urakka-id kayttaja pyynto]
  (let [workbook (xls/load-workbook-from-file (:path (bean (get-in pyynto [:params "file" :tempfile]))))
        paikkauskohteet (p-excel/parsi-syotetyt-reikapaikkaukset workbook)
        _ (println "\n Syötetyt: " paikkauskohteet)
        tuloksia? (empty? paikkauskohteet)
        _ (println "empty?: " (empty? paikkauskohteet))
        
        ;; {:pvm 08.08.2018, :aosa 21.0, :kustannus 200000.0, :tie 81.0, :let 4270.0, :yksikko m2, :losa 21.0, :aet 4040.0, :menetelma Urapaikkaus (UREM/RREM), :maara 81.0, :tunniste 1234444.0}
        _ (dorun (for [x paikkauskohteet]
                   (println "validoitu: " x)))
        
        ;;_ (println "\n Workhook: " workbook)
        ]
    {:status 200
     :headers {"Content-Type" "application/json; charset=UTF-8"}
     :body (cheshire/encode paikkauskohteet)}))


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
      :hae-reikapaikkaukset (fn [user tiedot]
                              (hae-reikapaikkaukset db user tiedot)))
    ;; Excel tuonti
    (julkaise-palvelu http-palvelin
      :lue-reikapaikkauskohteet-excelista (wrap-multipart-params
                                            (fn [pyynto] (vastaanota-excel db pyynto)))
      {:ring-kasittelija? true})
    this)

  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-reikapaikkaukset
      :lue-reikapaikkauskohteet-excelista)
    this))
