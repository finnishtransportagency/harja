(ns harja.palvelin.lokitus.hipchat
  "Logger, joka lähettää HipChat kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]
            [hiccup.core :refer [html]]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))

(defn laheta-html [teksti]
  @(http/post (str "https://api.hipchat.com/v2/room/" 1396730 "/notification")
              {:headers {"Content-Type" "application/x-www-form-urlencoded"
                         "Authorization" (str "Bearer " "h4egJmxnIjE1EiApo70VQZlJOl29g6Hzo5dcFGnD")}
               :form-params {"message_format" "html"
                             "color" "purple"
                             "message" teksti}}))

(defn luo-hipchat-appender [huone-id token taso]
  {:doc "HipChat appender"
   :min-level taso
   :enabled? true
   :async? true
   :limit-per-msecs nil
   :fn (fn [{:keys [ap-config config level prefix throwable message args] :as args}]
         (let [[html-arg] args
               msg (if (vector? html-arg)
                     (html html-arg)
                     message)]
           (http/post (str "https://api.hipchat.com/v2/room/" huone-id "/notification")
                      {:headers {"Content-Type" "application/x-www-form-urlencoded"
                                 "Authorization" (str "Bearer " token)}
                       :form-params {"message_format" "html"
                                     "message"
                                     (str  kone " [" (str/upper-case (name level)) "] " msg)}})))})
