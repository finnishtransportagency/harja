(ns harja.palvelin.komponentit.excel-vienti
  "Excel-viennin komponentti, tarjoaa reitin, jonka kautta Excel:n voi ladata selaimelle.
  Lisäksi tänne voi muut komponentit rekisteröidä Excel:n luontimekanismin.
  Tämä komponentti ei ota kantaa Excel:n sisältöön, se vain antaa excel workbook kahvan 
  rekisteröidylle funktiolle, joka mutatoi sitä haluamallaan tavalla."
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelu]]
            [harja.transit :as t]
            [hiccup.core :refer [html]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.codec :as codec]
            [taoensso.timbre :as log]
            [dk.ative.docjure.spreadsheet :as excel]
            [ring.util.io :refer [piped-input-stream]])
  (:import (java.io ByteArrayInputStream)
           (org.apache.poi.xssf.usermodel XSSFWorkbook)))

(defprotocol ExcelKasittelijat
  (rekisteroi-excel-kasittelija! [this nimi kasittely-fn]
    "Julkaisee Excel käsittelijäfunktion annetulla keyword nimellä. Funktio ottaa parametriksi
 käyttäjän sekä HTTP request parametrit mäppeinä ja palauttaa mäpin, jossa on Excelin tiedot.")
  (poista-excel-kasittelija! [this nimi]))

(declare muodosta-excel)

(defrecord ExcelVienti [excel-kasittelijat]
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
    (log/info "Excel-vientikomponentti aloitettu")
    (julkaise-palvelu http
                      :excel (wrap-params (fn [req]
                                            (muodosta-excel @excel-kasittelijat req)))
                      {:ring-kasittelija? true})
    this)

  (stop [{http :http-palvelin :as this}]
    (log/info "Excel-vientikomponentti lopetettu")
    (poista-palvelu http :excel))

  ExcelKasittelijat
  (rekisteroi-excel-kasittelija! [_ nimi kasittely-fn]
    (log/info "Rekisteröidään Excel käsittelijä: " nimi)
    (swap! excel-kasittelijat assoc nimi kasittely-fn))

  (poista-excel-kasittelija! [_ nimi]
    (log/info "Poistetaan Excel käsittelijä: " nimi)
    (swap! excel-kasittelijat dissoc nimi)))



(defn luo-excel-vienti []
  (->ExcelVienti (atom {})))

;; Jostain syystä wrap-params ei lue meidän POSTattua formia
;; Luetaan se ja otetaan "parametrit" niminen muuttuja ja
;; muunnetaan se transit+json muodosta Clojure dataksi
(defn- lue-body-parametrit [body]
  (-> body
      .bytes
      (String.)
      codec/form-decode
      (get "parametrit")
      .getBytes
      (ByteArrayInputStream.)
      t/lue-transit))

(defn- luo-workbook [{:keys [nimi rivit]}]
  (log/info "LUO WORKBOOK: nimi= " nimi "; rivit: " rivit)
  (let [workbook (XSSFWorkbook.)
        sheet    (excel/add-sheet! workbook nimi)]
    (doseq [rivi rivit
            :let [tyyli (meta rivi)]]
      (log/info "TYYLI: " tyyli)
      (let [row (excel/add-row! sheet rivi)]
        (when tyyli
          (excel/set-row-style! row
                                (excel/create-cell-style! workbook tyyli)))))
    workbook))

(defn- kirjoita-workbook [wb out]
  (excel/save-workbook! out wb)
  (.close out))

(def +mime-type+ "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

(defn- muodosta-excel [kasittelijat {kayttaja :kayttaja body :body
                                     query-params :params
                                     :as req}]
  (let [tyyppi (keyword (get query-params "_"))
        params (lue-body-parametrit body)
        kasittelija (get kasittelijat tyyppi)]
    (log/debug "PARAMS: " params)
    (if-not kasittelija
      {:status 404
       :body (str "Tuntematon Excel: " tyyppi)}
      (try
        (log/debug "Luodaan " tyyppi " Excel käyttäjälle " (:kayttajanimi kayttaja)
                   " parametreilla " params)
        {:status  200
         :headers {"Content-Type" +mime-type+}
         :body    (piped-input-stream
                   (fn [out]
                     (-> (kasittelija kayttaja params)
                         (luo-workbook)
                         (kirjoita-workbook out))))}
        (catch Exception e
          (log/warn e "Virhe Excel-muodostuksessa: " tyyppi ", käyttäjä: " kayttaja)
          {:status 500
           :body "Virhe Excel-muodostuksessa"})))))
