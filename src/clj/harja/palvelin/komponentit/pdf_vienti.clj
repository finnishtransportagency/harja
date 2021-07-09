(ns harja.palvelin.komponentit.pdf-vienti
  "PDF-vientin komponentti, tarjoaa reitin, jonka kautta PDF:n voi ladata selaimelle.
  Lisäksi tänne voi muut komponentit rekisteröidä PDF:n luontimekanismin.
  Tämä komponentti ei ota kantaa PDF:n sisältöön, se vain generoi Hiccup muotoisesta FOPista PDF:n."
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
            [harja.tyokalut.html :refer [sanitoi]]
            [ring.util.io :refer [piped-input-stream]]
            [harja.palvelin.komponentit.vienti :as vienti]
            [clojure.walk :as walk]
            [clojure.string :as str])
  (:import javax.xml.transform.sax.SAXResult
           javax.xml.transform.stream.StreamSource
           javax.xml.transform.TransformerFactory
           [org.apache.fop.apps FopConfParser MimeConstants]
           [java.io ByteArrayInputStream
                    ByteArrayOutputStream]))

(defprotocol PdfKasittelijat
  (rekisteroi-pdf-kasittelija! [this nimi kasittely-fn]
    "Julkaisee PDF käsittelijäfunktion annetulla keyword nimellä.
     Funktio ottaa parametriksi käyttäjän sekä HTTP request parametrit
     mäppeinä ja palauttaa PDF:n tiedot hiccup muotoisena FOPina.")
  (poista-pdf-kasittelija! [this nimi])
  (luo-pdf [this kasittelija-nimi kayttaja parametrit]
    "Muodostaa pdf tiedoston annettuna käsittelijän nimi ja sen parametrit"))

(declare muodosta-pdf)

(defn- escape [hiccup]
  (walk/postwalk #(if (and (string? %)
                           (not (str/starts-with? % "<![CDATA[")))
                    (sanitoi %)
                    %) hiccup))

(defn- hiccup->pdf [fop-factory hiccup out]
  (let [fop (.newFop fop-factory MimeConstants/MIME_PDF out)
        xform (.newTransformer (TransformerFactory/newInstance))
        src (StreamSource. (java.io.StringReader. (html (escape hiccup))))
        res (SAXResult. (.getDefaultHandler fop))]
    (.transform xform src res)))

(defn- luo-fop-factory []
  (let [conf (io/resource "fop/fop.xconf")
        conf-parser (FopConfParser. (io/input-stream conf)
                                    (.toURI conf)
                                    (org.apache.fop.apps.io.ResourceResolverFactory/createDefaultResourceResolver))]
    (-> conf-parser
        .getFopFactoryBuilder
        .build)))

(defrecord PdfVienti [pdf-kasittelijat fop-factory]
  component/Lifecycle
  (start [{http :http-palvelin :as this}]
    (let [fop-factory (luo-fop-factory)]
      (log/info "PDF-vientikomponentti aloitettu")
      (julkaise-palvelu http
                        :pdf (wrap-params (fn [req]
                                            (muodosta-pdf fop-factory @pdf-kasittelijat req)))
                        {:ring-kasittelija? true})
      (assoc this :fop-factory fop-factory)))

  (stop [{http :http-palvelin :as this}]
    (log/info "PDF-vientikomponentti lopetettu")
    (reset! pdf-kasittelijat {})
    (poista-palvelu http :pdf)
    (assoc this :fop-factory nil))

  PdfKasittelijat
  (rekisteroi-pdf-kasittelija! [_ nimi kasittely-fn]
    (log/info "Rekisteröidään PDF käsittelijä: " nimi)
    (swap! pdf-kasittelijat assoc nimi kasittely-fn))

  (poista-pdf-kasittelija! [_ nimi]
    (log/info "Poistetaan PDF käsittelijä: " nimi)
    (swap! pdf-kasittelijat dissoc nimi))

  (luo-pdf [_ kasittelija-nimi kayttaja parametrit]
    (let [kasittelija-fn (kasittelija-nimi @pdf-kasittelijat)
          pdf-hiccup (kasittelija-fn kayttaja parametrit)
          tiedostonimi (-> pdf-hiccup meta :tiedostonimi)
          pdf-outputstream (ByteArrayOutputStream.)]
      (with-open [pdf-outputstream (ByteArrayOutputStream.)]
        (hiccup->pdf fop-factory pdf-hiccup pdf-outputstream)
        {:tiedosto-bytet (.toByteArray pdf-outputstream)
         :tiedostonimi tiedostonimi}))))

(defn luo-pdf-vienti []
  (->PdfVienti (atom {}) nil))

(defn- muodosta-pdf [fop-factory kasittelijat {kayttaja :kayttaja body :body
                                               query-params :params
                                               :as req}]
  (let [tyyppi (keyword (get query-params "_"))
        params (or (vienti/lue-get-parametrit req)
                   (vienti/lue-body-parametrit body))
        kasittelija (get kasittelijat tyyppi)]
    (log/debug "PARAMS: " params)
    (if-not kasittelija
      {:status 404
       :body (str "Tuntematon PDF: " tyyppi)}
      (try
        (log/debug "Luodaan " tyyppi " PDF käyttäjälle " (:kayttajanimi kayttaja)
                   " parametreilla " params)
        (let [pdf (kasittelija kayttaja params)
              tiedostonimi (-> pdf meta :tiedostonimi)]
          (if (map? pdf)
            ;; Käsittelijä palautti ring vastauksen, annetaan se läpi as is
            pdf
            ;; Käsittelijä palautti hiccupia, generoidaan siitä PDF
            {:status  200
             :headers (merge {"Content-Type" "application/pdf"}
                             (when tiedostonimi
                               {"Content-Disposition" (str "attachment; filename=\"" tiedostonimi "\"")}))
             :body    (piped-input-stream
                       (fn [out]
                         (try
                           (hiccup->pdf fop-factory pdf out)
                           (catch Throwable t
                             (log/warn t "Poikkeus piped-input-streamissä hiccup->PDF")))))}))
        (catch Exception e
          (log/warn e "Virhe PDF-muodostuksessa: " tyyppi ", käyttäjä: " kayttaja)
          {:status 500
           :body "Virhe PDF-muodostuksessa"})))))
