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
  (:import (org.apache.fop.apps FopConfParser)))

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


(defn muodosta-pdf [fop-factory kasittelijat {kayttaja :kayttaja q :query-string
                                              params :params :as req}]
  (let [tyyppi (keyword (first (str/split q #"\&")))
        kasittelija (get kasittelijat tyyppi)]
    (if-not kasittelija
      {:status 404
       :body (str "Tuntematon PDF: " tyyppi)}
      (do (log/debug "Luodaan " tyyppi " PDF käyttäjälle " (:kayttajanimi kayttaja) " parametreilla " params)
          (try
            {:status 200
             :body (str (some-> (kasittelija kayttaja params)
                                (html)))}
            (catch Exception e
              (log/warn "Virhe PDF-muodostuksessa: " tyyppi ", käyttäjä: " kayttaja) 
              {:status 500
               :body "Virhe PDF-muodostuksessa"}))))))

              
                                      

