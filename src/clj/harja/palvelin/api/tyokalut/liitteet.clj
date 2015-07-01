(ns harja.palvelin.api.tyokalut.liitteet
  (:require [taoensso.timbre :as log])
  (:import (java.util Base64)))

(defn dekoodaa-base64 [data]
  (.decode (Base64/getDecoder) data))
