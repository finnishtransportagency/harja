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
    "Julkaisee Excel käsittelijäfunktion annetulla keyword nimellä. Funktio ottaa parametriksi Excel
workbookin, käyttäjän sekä HTTP request parametrit mäppeinä ja palauttaa tiedoston nimen.")
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

(defn- luo-workbook []
  (XSSFWorkbook.))

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
        (let [wb (luo-workbook)
              nimi (kasittelija wb kayttaja params)]
          (log/info "WORKBOOK ON " wb)
          {:status  200
           :headers {"Content-Type" +mime-type+
                     "Content-Disposition" (str "attachment; filename=\"" nimi ".xlsx\"")}
           :body    (piped-input-stream #(kirjoita-workbook wb %))})
        (catch Exception e
          (log/warn e "Virhe Excel-muodostuksessa: " tyyppi ", käyttäjä: " kayttaja)
          {:status 500
           :body "Virhe Excel-muodostuksessa"})))))
