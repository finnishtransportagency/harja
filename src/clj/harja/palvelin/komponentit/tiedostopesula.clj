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
            [clojure.java.jdbc :as jdbc])
  (:import (java.io InputStream ByteArrayOutputStream)
           (org.postgresql.largeobject LargeObjectManager)
           (com.mchange.v2.c3p0 C3P0ProxyConnection)
           (net.coobird.thumbnailator Thumbnailator)
           (net.coobird.thumbnailator.tasks UnsupportedFormatException))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn pdf-preview "java.io/file -> png byte-array | nil" [pdf-file]
  (try
    (let [request-opts
          {:as :byte-array
           :multipart [{:name "file"
                        :content pdf-file
                        :mime-type "application/pdf"}]}
          resp (http/post (str (env/value :muuntaja :url) pdf-preview-path)
                          request-opts)]
      (if (= 200 (:status resp))
        (:body resp)
        (do
          (log/info "pdf preview failed for " (.getName pdf-file))
          nil)))
    (catch Exception ex
      (log/error ex)
      nil)))
