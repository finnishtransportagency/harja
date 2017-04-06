(ns harja.palvelin.lokitus.slack
  "Logger, joka lähettää Slack kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]
            [hiccup.core :refer [html]]
            [cheshire.core :as cheshire]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))

(defn luo-slack-appender [webhook-url taso]
  {:doc "Slack appender"
   :min-level taso
   :enabled? true
   :async? true
   :limit-per-msecs nil
   :fn (fn [{:keys [ap-config config level prefix throwable message args] :as args}]
         (let [[html-arg] args
               msg (if (vector? html-arg)
                     (html html-arg)
                     message)]
           (http/post webhook-url
                      {:headers {"Content-Type" "application/json"}
                       :body {"username" kone
                              "text" (str "[" (str/upper-case (name level)) "] " msg)}})))})
