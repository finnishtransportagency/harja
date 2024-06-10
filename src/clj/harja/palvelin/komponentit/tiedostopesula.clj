(ns harja.palvelin.komponentit.tiedostopesula
  (:require
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [org.httpkit.client :as http]))

(defn- pdfa-muunna-file->inputstream! "java.io/file -> java.io/file | nil" [{base-url :base-url} pdf-file]
  {:pre [(some? pdf-file)]}
  (when base-url
    (try
      (let [request-opts
            {:as :byte-array
             :multipart [{:name "file"
                          :content pdf-file
                          :filename "input.pdf"
                          :mime-type "application/pdf"}]}
            resp (deref (http/post (str base-url "pdf/pdf2pdfa") request-opts))]
        (if (= 200 (:status resp))
          (:body resp)
          (do
            (log/info "PDF/A muunnos epäonnistui, tiedoston nimi oli: " (when pdf-file (.getName pdf-file)))
            (log/info "Virhevastasus palvelulta: " (pr-str resp))
            nil)))
      (catch Exception ex
        (log/error "Poikkeus tiedostomuuntajan vastausta luettaessa: " ex)
        nil))))

(defn pdfa-muunna-file->file! [tp-komponentti pdf-file]
  (let [temp-file (java.io.File/createTempFile "pesula-tmp" ".pdf")]
    (try
      (let [muunnettu-stream (pdfa-muunna-file->inputstream! tp-komponentti pdf-file)]
        ;; (log/debug "luetussa streamissa merkkejä: " (count (slurp muunnettu-stream)))
        ;; (log/debug "uudelleen luetussa streamissa merkkejä: " (count (slurp muunnettu-stream)))
        (if muunnettu-stream
          (do (io/copy muunnettu-stream temp-file)
              ;; (log/debug "saadun tiedoston koko: " (.length pdf-file) "muunnetun tempfilen koko:" (.length temp-file))
              temp-file)
          ;; else
          (do (.delete temp-file)
              nil))))))

(defrecord Tiedostopesula [base-url]
  component/Lifecycle
  (start [this]
    this)

  (stop [this]
    this))

(defn luo-tiedostopesula [{base-url :base-url}]
  (->Tiedostopesula base-url))
