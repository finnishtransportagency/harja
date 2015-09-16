(ns harja.palvelin.komponentit.pdf-vienti
  "PDF-vientin komponentti, tarjoaa reitin, jonka kautta PDF:n voi ladata selaimelle.
  Lisäksi tänne voi muut komponentit rekisteröidä PDF:n luontimekanismin.
  Tämä komponentti ei ota kantaa PDF:n sisältöön, se vain generoi Hiccup muotoisesta FOPista PDF:n."
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [ring.middleware.params :refer [wrap-params]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [hiccup.core :refer [html]]
            [clojure.java.io :as io])
  (:import (org.apache.fop.apps FopConfParser MimeConstants)
           (javax.xml.transform Transformer TransformerFactory)
           (javax.xml.transform.stream StreamSource StreamResult)
           (javax.xml.transform.sax SAXResult)))

(defprotocol PdfKasittelijat
  (rekisteroi-pdf-kasittelija! [this nimi kasittely-fn]
    "Julkaisee PDF käsittelijäfunktion annetulla keyword nimellä. Funktio ottaa parametriksi käyttäjän sekä HTTP request parametrit mäppeinä ja palauttaa PDF:n tiedot hiccup muotoisena FOPina.")
  (poista-pdf-kasittelija! [this nimi]))

(declare muodosta-pdf)

(defrecord PdfVienti [pdf-kasittelijat fop-factory]
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
    (log/info "PDF-vientikomponentti aloitettu")
    (julkaise-palvelu http
                      :pdf (wrap-params (fn [req]
                                     (muodosta-pdf fop-factory @pdf-kasittelijat req)))
                      {:ring-kasittelija? true})
    this)

  (stop [{http :http-palvelin :as this}]
    (log/info "PDF-vientikomponentti lopetettu")
    (poista-palvelu http :pdf))
  
  PdfKasittelijat
  (rekisteroi-pdf-kasittelija! [_ nimi kasittely-fn]
    (log/info "Rekisteröidään PDF käsittelijä: " nimi)
    (swap! pdf-kasittelijat assoc nimi kasittely-fn))

  (poista-pdf-kasittelija! [_ nimi]
    (log/info "Poistetaan PDF käsittelijä: " nimi)
    (swap! pdf-kasittelijat dissoc nimi)))

(defn- luo-fop-factory []
  (-> "fop/fop.xconf"
      io/resource io/as-file
      (FopConfParser.)
      .getFopFactoryBuilder
      .build))

(defn luo-pdf-vienti []
  (->PdfVienti (atom {}) (luo-fop-factory)))

(defn- hiccup->pdf [fop-factory hiccup]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (let [fop (.newFop fop-factory MimeConstants/MIME_PDF out)
          xform (.newTransformer (TransformerFactory/newInstance))
          src (StreamSource. (java.io.StringReader. (html hiccup)))
          res (SAXResult. (.getDefaultHandler fop))]
      (.transform xform src res)
      (.toByteArray out))))



(defn- muodosta-pdf [fop-factory kasittelijat {kayttaja :kayttaja q :query-string
                                              params :params :as req}]
  (let [tyyppi (keyword (first (str/split q #"\&")))
        kasittelija (get kasittelijat tyyppi)]
    (if-not kasittelija
      {:status 404
       :body (str "Tuntematon PDF: " tyyppi)}
      (do (log/debug "Luodaan " tyyppi " PDF käyttäjälle " (:kayttajanimi kayttaja) " parametreilla " params)
          (try
            {:status 200
             :headers {"Content-Type" "application/pdf"} ;; content-disposition!
             :body (java.io.ByteArrayInputStream. (hiccup->pdf fop-factory (kasittelija kayttaja params)))}
            (catch Exception e
              (log/warn "Virhe PDF-muodostuksessa: " tyyppi ", käyttäjä: " kayttaja) 
              {:status 500
               :body "Virhe PDF-muodostuksessa"}))))))

              
                                      
(def test-fo
  [:fo:root {:xmlns:fo "http://www.w3.org/1999/XSL/Format"}

   ;; Layoutin konffi
   [:fo:layout-master-set
    [:fo:simple-page-master {:master-name "first"
                             :page-height "29.7cm" :page-width "21cm"
                             :margin-top "1cm" :margin-bottom "2cm" :margin-left "2.5cm" :margin-right "2.5cm"}
     [:fo:region-body {:margin-top "1cm"}]
     [:fo:region-before {:extent "1cm"}]
     [:fo:region-after {:extent "1.5cm"}]]]

   ;; Itse sisältö
   [:fo:page-sequence {:master-reference "first"}
    [:fo:flow {:flow-name "xsl-region-body"}
     [:fo:block {:font-family "Helvetica" :font-size "14pt"} "Jotain tekstiä tänne"]
     [:fo:block {:space-after.optimum "10pt" :font-family "Helvetica" :font-size "10pt"}
      [:fo:table
       [:fo:table-column {:column-width "10cm"}]
       [:fo:table-column {:column-width "10cm"}]
       [:fo:table-body
        (for [[eka toka] [["1" "jotain"] ["2" "ihan"] ["3" "muuta"]]]
          [:fo:table-row
           [:fo:table-cell
            [:fo:block eka]]
           [:fo:table-cell
            [:fo:block eka]]])]]]]]])

   
(defn rekisteroi-testi []
  (rekisteroi-pdf-kasittelija! (:pdf-vienti harja.palvelin.main/harja-jarjestelma) :test (fn [k params] test-fo)))
