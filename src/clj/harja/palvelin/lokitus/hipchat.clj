(ns harja.palvelin.lokitus.hipchat
  "Logger, joka lähettää HipChat kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))

(defn luo-hipchat-appender [huone-id token taso]
  {:doc "HipChat appender"
   :min-level taso
   :enabled? true
   :async? true
   :limit-per-msecs nil
   :fn (fn [{:keys [ap-config config level prefix throwable message] :as args}]
         (http/post (str "https://api.hipchat.com/v2/room/" huone-id "/notification")
                    {:headers {"Content-Type" "application/x-www-form-urlencoded"
                               "Authorization" (str "Bearer " token)}
                     :form-params {"message"
                                   (str kone " [" (str/upper-case (name level)) "] " message)}}))})
