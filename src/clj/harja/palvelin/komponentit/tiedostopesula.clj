(ns harja.palvelin.komponentit.tiedostopesula
  (:require
            [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.komponentit.virustarkistus :as virustarkistus]
            [fileyard.client :as fileyard-client]
            [harja.palvelin.palvelut.pois-kytketyt-ominaisuudet :refer [ominaisuus-kaytossa?]]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.palvelin.tyokalut.lukot :as lukot]
            [clojure.java.jdbc :as jdbc]
            [org.httpkit.client :as http]
            [clojure.java.io :refer [file output-stream input-stream]])
  (:import (java.io InputStream ByteArrayOutputStream)
           (org.postgresql.largeobject LargeObjectManager)
           (com.mchange.v2.c3p0 C3P0ProxyConnection)
           (net.coobird.thumbnailator Thumbnailator)
           (net.coobird.thumbnailator.tasks UnsupportedFormatException))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn pdfa-convert! "java.io/file -> png byte-array | nil" [laundry-pdf2pdfa-url pdf-file]
  (try
    (let [request-opts
          {:as :byte-array
           :multipart [{:name "file"
                        :content pdf-file
                        :filename "input.pdf"
                        :mime-type "application/pdf"}]}
          resp (deref (http/post laundry-pdf2pdfa-url request-opts))]
      (if (= 200 (:status resp))
        (:body resp)
        (do
          (log/info "PDF/A conversion failed for " (.getName pdf-file))
          (log/info "got: " (pr-str resp))
          nil)))
    (catch Exception ex
      (log/error ex)
      nil)))

;; (defonce pdf-resp (atom nil))

(defn test-pdfa-convert [service-url]
  (let [in-file (file "/tmp/pdfa-test.pdf")]
    (let [resp (pdfa-convert! service-url in-file)]
      ;; (reset! pdf-resp resp)
      (with-open [out (output-stream "pdfa-test.out.pdf")]
        (log/info "resp type" (type resp))
        (.write out resp)))))
