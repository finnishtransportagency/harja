(ns harja.palvelin.lokitus.slack
  "Logger, joka lähettää Slack kanavalle viestit"
  (:require [org.httpkit.client :as http]
            [clojure.string :as str]
            [hiccup.core :refer [html]]
            [cheshire.core :as cheshire]))

(def kone (.getHostName (java.net.InetAddress/getLocalHost)))


(defn laheta [webhook-url level msg]
  (http/post
   webhook-url
   {:headers {"Content-Type" "application/json"}
    :body (cheshire/encode
           {:username kone
            :attachments
            [{:fallback ""

              ;; Käytetään slackin esimääriteltyjä värejä levelin mukaan
              :color (case level
                       :warn "warning"
                       :error "danger"
                       "good")
              
              ;; Näytetään viesti kenttänä, jonka otsikkona on taso
              ;; ja arvona virheviesti
              :fields [{:title (str/upper-case (name level))
                        :value msg}]}]})}))

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
           (laheta webhook-url level msg)))})
