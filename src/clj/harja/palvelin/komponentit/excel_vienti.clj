(ns harja.palvelin.komponentit.excel-vienti
  "Excel-viennin komponentti, tarjoaa reitin, jonka kautta Excel:n voi ladata selaimelle.
  Lisäksi tänne voi muut komponentit rekisteröidä Excel:n luontimekanismin.
  Tämä komponentti ei ota kantaa Excel:n sisältöön, se vain antaa excel workbook kahvan
  rekisteröidylle funktiolle, joka mutatoi sitä haluamallaan tavalla."
  (:require [com.stuartsierra.component :as component]
            [dk.ative.docjure.spreadsheet :as excel]
            [harja.palvelin.komponentit.http-palvelin
             :refer
             [julkaise-palvelu poista-palvelu]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.io :refer [piped-input-stream]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.vienti :as vienti])
  (:import org.apache.poi.xssf.usermodel.XSSFWorkbook))

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
    (doseq [[kasittelijan-nimi _] @excel-kasittelijat]
      (poista-excel-kasittelija! this kasittelijan-nimi))
    (poista-palvelu http :excel)
    this)

  ExcelKasittelijat
  (rekisteroi-excel-kasittelija! [_ nimi kasittely-fn]
    (log/info "Rekisteröidään Excel käsittelijä: " nimi)
    (swap! excel-kasittelijat assoc nimi kasittely-fn))

  (poista-excel-kasittelija! [_ nimi]
    (log/info "Poistetaan Excel käsittelijä: " nimi)
    (swap! excel-kasittelijat dissoc nimi)))



(defn luo-excel-vienti []
  (->ExcelVienti (atom {})))

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
        params (or (vienti/lue-get-parametrit req)
                   (vienti/lue-body-parametrit body))
        kasittelija (get kasittelijat tyyppi)]
    (if-not kasittelija
      {:status 404
       :body (str "Tuntematon Excel: " tyyppi)}
      (try
        (log/debug "Luodaan " tyyppi " Excel käyttäjälle " (:kayttajanimi kayttaja)
                   " parametreilla " params)
        (let [wb (luo-workbook)
              vastaus (kasittelija wb kayttaja params)]
          (if (map? vastaus)
            ;; Excel käsittelijä palautti Ring vastauksen, palauta se sellaisenaan
            vastaus

            ;; Generointi onnistui, vastauksessa on tiedoston nimi
            {:status  200
             :headers {"Content-Type" +mime-type+
                       "Content-Disposition" (str "attachment; filename=\"" vastaus ".xlsx\"")}
             :body    (piped-input-stream #(kirjoita-workbook wb %))}))
        (catch Exception e
          (log/warn e "Virhe Excel-muodostuksessa: " tyyppi ", käyttäjä: " kayttaja)
          {:status 500
           :body "Virhe Excel-muodostuksessa"})))))
